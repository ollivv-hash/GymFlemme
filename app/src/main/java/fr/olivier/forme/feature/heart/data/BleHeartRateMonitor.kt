package fr.olivier.forme.feature.heart.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.olivier.forme.feature.heart.domain.HeartRateMeasurement
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class BleDevice(val address: String, val name: String?, val rssi: Int?, val advertisesHeartRate: Boolean, val bonded: Boolean)

sealed interface BleLink {
    data object Idle : BleLink
    data class Connecting(val device: BleDevice) : BleLink
    data class Connected(val device: BleDevice, val last: HeartRateMeasurement?, val notifications: Int, val lastAtMillis: Long?) : BleLink
    /** Session mode: the link dropped and Android reconnects as soon as the watch broadcasts again. */
    data class Reconnecting(val device: BleDevice) : BleLink
    data class Failed(val device: BleDevice?, val reason: String) : BleLink
}

data class BleState(
    val available: Boolean = true,
    val enabled: Boolean = true,
    val scanning: Boolean = false,
    val devices: List<BleDevice> = emptyList(),
    val link: BleLink = BleLink.Idle,
    val log: List<String> = emptyList(),
)

/**
 * Bluetooth LE client for the standard Heart Rate profile (service 0x180D, measurement 0x2A37), as used by chest
 * straps and by watches that "broadcast" their heart rate. Lot 0: scan, connect, subscribe and keep a diagnostic log.
 */
@Singleton
class BleHeartRateMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) {
    private val manager = context.getSystemService(BluetoothManager::class.java)
    private val _state = MutableStateFlow(BleState(available = manager?.adapter != null))
    val state: StateFlow<BleState> = _state.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var scanCallback: ScanCallback? = null
    /** Session mode: keep (re)connecting until [disconnect]. */
    @Volatile private var persistent = false

    /** Every heart rate received (epoch millis, bpm), skipping those flagged "no skin contact". */
    private val _measurements = MutableSharedFlow<Pair<Long, Int>>(extraBufferCapacity = 64)
    val measurements: SharedFlow<Pair<Long, Int>> = _measurements.asSharedFlow()

    fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    @SuppressLint("MissingPermission")
    fun isEnabled(): Boolean = manager?.adapter?.isEnabled == true

    fun hasPermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Lists bonded devices, then scans; with [onlyHeartRate] the scan keeps only devices advertising 0x180D. */
    @SuppressLint("MissingPermission")
    fun startScan(onlyHeartRate: Boolean) {
        val adapter = manager?.adapter ?: return log("Bluetooth indisponible sur ce téléphone")
        _state.update { it.copy(enabled = adapter.isEnabled) }
        if (!adapter.isEnabled) return log("Bluetooth désactivé")
        if (!hasPermissions()) return log("Autorisation Bluetooth manquante")
        stopScan()
        val bonded = adapter.bondedDevices.orEmpty().map {
            BleDevice(it.address, it.name, null, advertisesHeartRate = false, bonded = true)
        }
        _state.update { it.copy(devices = bonded, scanning = true) }
        log("Appareils appairés : ${bonded.size}. Recherche ${if (onlyHeartRate) "(service FC uniquement)" else "(tous)"}…")
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = onScanned(result)
            override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach(::onScanned)
            override fun onScanFailed(errorCode: Int) {
                log("Échec de la recherche (code $errorCode)")
                _state.update { it.copy(scanning = false) }
            }
        }
        val filters = if (onlyHeartRate) listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(HR_SERVICE)).build()) else emptyList()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        adapter.bluetoothLeScanner?.startScan(filters, settings, callback)
            ?: return log("Scanner BLE indisponible")
        scanCallback = callback
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        val callback = scanCallback ?: return
        scanCallback = null
        if (hasPermissions()) runCatching { manager?.adapter?.bluetoothLeScanner?.stopScan(callback) }
        _state.update { it.copy(scanning = false) }
        log("Recherche arrêtée")
    }

    @SuppressLint("MissingPermission")
    private fun onScanned(result: ScanResult) {
        val advertisesHr = result.scanRecord?.serviceUuids?.any { it.uuid == HR_SERVICE } == true
        val device = BleDevice(
            address = result.device.address,
            name = result.device.name ?: result.scanRecord?.deviceName,
            rssi = result.rssi,
            advertisesHeartRate = advertisesHr,
            bonded = result.device.bondState == BluetoothDevice.BOND_BONDED,
        )
        val known = _state.value.devices.any { it.address == device.address && !it.bonded }
        _state.update { s ->
            s.copy(devices = (s.devices.filterNot { it.address == device.address } + device).sortedWith(DEVICE_ORDER))
        }
        if (!known) log("Trouvé : ${device.name ?: "(sans nom)"} ${device.address} ${device.rssi} dBm${if (advertisesHr) " ♥ service FC" else ""}")
    }

    /**
     * Connects to [device]. With [persistent] (session mode) Android's auto-connect is used: no timeout, the link is
     * made whenever the watch broadcasts, and it is re-established after every drop until [disconnect].
     */
    @SuppressLint("MissingPermission")
    fun connect(device: BleDevice, persistent: Boolean = false) {
        val adapter = manager?.adapter ?: return
        if (!hasPermissions()) return log("Autorisation Bluetooth manquante")
        if (!adapter.isEnabled) return log("Bluetooth désactivé")
        stopScan()
        disconnect()
        this.persistent = persistent
        _state.update { it.copy(link = BleLink.Connecting(device)) }
        log("Connexion à ${device.name ?: device.address}${if (persistent) " (attente de la diffusion)" else ""}…")
        gatt = adapter.getRemoteDevice(device.address)
            .connectGatt(context, persistent, GattCallback(device), BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        persistent = false
        val g = gatt ?: return
        gatt = null
        if (hasPermissions()) runCatching { g.disconnect(); g.close() }
        _state.update { it.copy(link = BleLink.Idle) }
        log("Déconnecté")
    }

    /** Session mode: drops [g] and waits for the watch again. */
    @SuppressLint("MissingPermission")
    private fun reconnect(g: BluetoothGatt, device: BleDevice) {
        runCatching { g.close() }
        if (!persistent || gatt != g || !hasPermissions()) return
        _state.update { it.copy(link = BleLink.Reconnecting(device)) }
        log("Reconnexion automatique dès que la montre diffuse…")
        gatt = manager?.adapter?.getRemoteDevice(device.address)
            ?.connectGatt(context, true, GattCallback(device), BluetoothDevice.TRANSPORT_LE)
    }

    private inner class GattCallback(private val device: BleDevice) : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    log("Connecté (statut $status), découverte des services…")
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    log("Connexion perdue (statut $status)")
                    if (persistent && gatt == g) {
                        reconnect(g, device)
                    } else {
                        _state.update { it.copy(link = BleLink.Failed(device, "Connexion perdue (statut $status)")) }
                        if (gatt == g) gatt = null
                        runCatching { g.close() }
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val uuids = g.services.map { shortUuid(it.uuid) }
            log("Services : ${uuids.joinToString()}")
            val characteristic = g.getService(HR_SERVICE)?.getCharacteristic(HR_MEASUREMENT)
            if (characteristic == null) {
                log("Service FC (180D) absent : la diffusion de la FC n'est pas active sur la montre ?")
                if (persistent && gatt == g) {
                    reconnect(g, device)
                } else {
                    _state.update { it.copy(link = BleLink.Failed(device, "Service FC absent")) }
                }
                return
            }
            g.setCharacteristicNotification(characteristic, true)
            val cccd = characteristic.getDescriptor(CCCD)
            if (cccd == null) {
                log("Descripteur de notification absent")
                return
            }
            val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(cccd, value)
            } else {
                @Suppress("DEPRECATION")
                cccd.value = value
                @Suppress("DEPRECATION")
                g.writeDescriptor(cccd)
            }
            log("Abonnement aux mesures FC demandé")
            _state.update { it.copy(link = BleLink.Connected(device, null, 0, null)) }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            log("Abonnement ${if (status == BluetoothGatt.GATT_SUCCESS) "accepté" else "refusé (statut $status)"}")
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            onMeasurement(value)
        }

        @Deprecated("Called before Android 13")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) characteristic.value?.let(::onMeasurement)
        }

        private fun onMeasurement(value: ByteArray) {
            val measurement = HeartRateMeasurement.parse(value) ?: return log("Mesure illisible : ${value.toHex()}")
            val count = ((_state.value.link as? BleLink.Connected)?.notifications ?: 0) + 1
            val now = clock.millis()
            _state.update { it.copy(link = BleLink.Connected(device, measurement, count, now)) }
            if (measurement.sensorContact != false && measurement.bpm > 0) _measurements.tryEmit(now to measurement.bpm)
            if (count == 1) log("Première mesure : ${measurement.bpm} bpm (brut ${value.toHex()})")
        }
    }

    private fun log(line: String) {
        val stamp = LocalTime.now(clock).format(TIME)
        _state.update { it.copy(log = (it.log + "$stamp $line").takeLast(LOG_LINES)) }
    }

    companion object {
        val HR_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HR_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val LOG_LINES = 40
        private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
        private val DEVICE_ORDER = compareByDescending<BleDevice> { it.advertisesHeartRate }
            .thenByDescending { it.name != null }
            .thenByDescending { it.rssi ?: Int.MIN_VALUE }

        private fun shortUuid(uuid: UUID): String = uuid.toString().let { if (it.endsWith("-0000-1000-8000-00805f9b34fb")) it.substring(4, 8) else it }
        private fun ByteArray.toHex() = joinToString(" ") { "%02X".format(it) }
    }
}
