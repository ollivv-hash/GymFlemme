#!/usr/bin/env python3
"""Re-encode the Open Exercise Illustrations images for the APK (V2.13).

The upstream WebP files are close to lossless (~29 kB each, 23.2 MB for 796 images). Re-encoded as lossy WebP at
quality 80, same dimensions, they weigh ~11 MB with no visible difference at the sizes the app shows them (sheet:
340 dp high at most, ~1020 px on an xxhdpi phone; lists: 56 dp thumbnails).

Usage (from the repository root, on a fresh copy of the upstream `images/` folder):
    python3 tools/library/recompress.py third_party/open-exercise-illustrations/assets/exercise_library/images

Rewrites every `*.webp` in place. Requires Pillow. CC0: modifying the images is allowed.
"""
import io
import os
import sys

from PIL import Image

QUALITY = 80


def main(root: str) -> None:
    before = after = count = 0
    for folder, _, files in sorted(os.walk(root)):
        for name in sorted(files):
            if not name.endswith(".webp"):
                continue
            path = os.path.join(folder, name)
            data = open(path, "rb").read()
            image = Image.open(io.BytesIO(data))
            size = image.size
            out = io.BytesIO()
            image.convert("RGB").save(out, "WEBP", quality=QUALITY, method=6)
            # Never grow a file (an image already compressed this way stays as it is).
            if out.tell() < len(data):
                with open(path, "wb") as f:
                    f.write(out.getvalue())
                assert Image.open(path).size == size, path
            before += len(data)
            after += min(out.tell(), len(data))
            count += 1
    print(f"{count} images: {before / 1e6:.1f} MB -> {after / 1e6:.1f} MB")


if __name__ == "__main__":
    main(sys.argv[1])
