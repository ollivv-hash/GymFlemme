# Open Exercise Illustrations — source

- Repository: https://github.com/hitmanrepo/open-exercise-illustrations
- Commit: `2786aefa4b36a08faeca8114e0d1eb0af3ef6732` (dataset version 1.0.0, committed 2026-10-03)
- Downloaded: 2026-10-07
- License: CC0 1.0 Universal (public domain), see `LICENSE` — attribution not required, credited in the app anyway.

Layout in this project:

- `LICENSE`, `README.md`, `SOURCE.md`: proof of license, **not** packaged in the APK.
- `assets/exercise_library/`: `exercises.json` (byte-identical copy of the upstream file) and `images/{id}/*.webp`,
  packaged as Android assets (`app/build.gradle.kts`, `sourceSets.main.assets`). Since app 2.12.2 the images are
  **re-encoded** as lossy WebP quality 80, same dimensions, by `tools/library/recompress.py` (23.2 MB → 11.1 MB, no
  visible difference at the sizes shown; CC0 allows it). Nothing else is changed: same files, names, frames, crops.
  The upstream `exercises.csv` and `preview.jpg` are not copied (same data / not used).

To update: replace `assets/exercise_library/` with a newer release, run `tools/library/recompress.py` on its
`images/` folder, update this file and `ExerciseLibraryLoader.DATASET_VERSION`; the app reloads the library without touching the user's exercises.
