package fr.olivier.forme.core.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Clock that tests move forward explicitly. */
class TestClock(var now: Instant = Instant.parse("2026-10-01T16:00:00Z")) : Clock() {
    private val zoneId = ZoneId.of("Europe/Paris")
    override fun getZone(): ZoneId = zoneId
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = now
    fun advance(duration: Duration) {
        now = now.plus(duration)
    }
}

fun context(): Context = ApplicationProvider.getApplicationContext()

/** In-memory database seeded exactly like the real one. */
fun testDatabase(clock: Clock): FormeDatabase =
    Room.inMemoryDatabaseBuilder(context(), FormeDatabase::class.java)
        .addCallback(SeedCallback(clock))
        .allowMainThreadQueries()
        .build()
