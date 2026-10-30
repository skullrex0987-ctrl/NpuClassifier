# CP2 STATUS — 2026-10-30 (GREEN, phone bugs reported)

## Green (CI 41084538046 — ALL PASS)
- unit-tests: passed
- emulator-tests: passed
- build: APK built successfully

## Done this session (phase4-cp2b)
- Bug 1: SafeCrop.kt safeCenterCrop helper, ClipEmbedder patched, full stack trace in AppLog, thumbnail failure != file ERROR, Retry failed button + Repo.resetErrors
- Bug 2: autoApply setting (Off/Ask/On default Off), FileOps skips existing dest (no "(1)" duplicates), Sorted folder excluded
- Bug 3: Initial stage total emitted before first file (progress never disappears), stage name/done/total/perSec

## Remaining / Not Done
- Bug 1 odd-dimension image tests not yet written (need JVM test with 1x1, 10x10, 223x4000, etc.)
- Bug 2 "Auto-apply after scan" wiring in ScanScreen (currently manual Apply only) — setting added, wiring pending
- Bug 2 free-space check before copy — pending
- Bug 2 confirm dialog before copy — pending
- Bug 3 Compose UI test (androidTest) for progress card at 3 moments + activity recreate — pending
- Bug 3 notification determinate bar — pending
- Bug 3 Repo.counts polling every 500ms while visible — partially (SortViewModel collects ScanController.state)

## Published
- phase4-cp2b: https://github.com/skullrex0987-ctrl/NpuClassifier/releases/tag/phase4-cp2b
- APK: https://github.com/skullrex0987-ctrl/NpuClassifier/releases/download/phase4-cp2b/app-debug.apk
- Need: read from ScanController.state, Repo.counts polling while visible, progress card never disappears, notification determinate bar, Compose UI test

## Releases
- phase4-cp1d: verified
- phase4-cp2: INCOMPLETE - do not test
- phase4-cp2e: CI GREEN — published with APK
