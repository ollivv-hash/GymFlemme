# Open Exercise Illustrations — source

- Repository: https://github.com/hitmanrepo/open-exercise-illustrations
- Commit: `2786aefa4b36a08faeca8114e0d1eb0af3ef6732` (dataset version 1.0.0, committed 2026-10-03)
- Downloaded: 2026-10-07
- License: CC0 1.0 Universal (public domain), see `LICENSE` — attribution not required, credited in the app anyway.

Layout in this project:

- `LICENSE`, `README.md`, `SOURCE.md`: proof of license, **not** packaged in the APK.
- `assets/exercise_library/`: `exercises.json` and `images/{id}/*.webp`, byte-identical copies of the upstream files,
  packaged as Android assets (`app/build.gradle.kts`, `sourceSets.main.assets`).
  The upstream `exercises.csv` and `preview.jpg` are not copied (same data / not used).

To update: replace `assets/exercise_library/` with a newer release, update this file and
`ExerciseLibraryLoader.DATASET_VERSION`; the app reloads the library without touching the user's exercises.
