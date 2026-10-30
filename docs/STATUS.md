# CP2 STATUS — 2026-10-30 (GREEN, phone bugs reported)

## Green (CI 41073262038 — ALL PASS)
- unit-tests: 29 passed
- emulator-tests: StopTest, ScanEndToEndTest, NoDuplicateProcessingTest, OcrEmulatorTest, FtsSpecialCharsTest
- build: APK built successfully

## Done this session
- (a) Pipeline hooks ✅
- (b) Settings UI ✅ + Prefs round-trip ✅
- (c) Semantic search ✅
- (d) FileOps multi-folder test ✅ + fake-Enricher test ✅
- (e) Diagnostics speed lines ✅

## Phone bugs (3) — IN PROGRESS
### Bug 1: Bitmap crop crash
- Need: safeCenterCrop helper, AppLog full stack trace, thumbnail failure != file ERROR, Retry button
### Bug 2: Copies everything it processes
- Need: Auto-apply Off/Ask/On (default Off), Apply only AUTO, skip DONE/existing, exclude Sorted folder
### Bug 3: Progress sometimes shows, sometimes not
- Need: read from ScanController.state, Repo.counts polling while visible, progress card never disappears, notification determinate bar, Compose UI test

## Releases
- phase4-cp1d: verified
- phase4-cp2: INCOMPLETE - do not test
- phase4-cp2e: CI GREEN — published with APK
