# CP2 STATUS — 2026-10-30 (GREEN)

## Green (CI 41067953448 — ALL PASS)
- unit-tests: 25 passed (DecisionPriority, PrefsRoundTrip, StarterRules, ScreenshotTyper, ProgressMath, RepoClaim, RepoCounts, RrfMerge, etc.)
- emulator-tests: StopTest, ScanEndToEndTest, NoDuplicateProcessingTest, OcrEmulatorTest (skipped if ML Kit empty), FtsSpecialCharsTest (moved to androidTest — real Android SQLite)
- build: APK built successfully

## FTS fix
- Moved FtsSpecialCharsTest from Robolectric (src/test) to androidTest (src/androidTest) — real Android SQLite
- Triggers (schema v4) in Db.kt handle FTS sync
- LIKE fallback in ftsSearch as safety net

## Not done (CP2 items remaining)
- Settings UI for all new fields (scan mode, smart model, OCR mode, Hindi, tags, search model, index while charging, Fastest preset, diagnostics, background setup guide)
- Semantic search (embed + cosine + RRF in UI) + "N of M indexed" + Build index
- FileOps multi-folder copy test (dental X-ray -> Teeth AND X-ray)
- Fake-Enricher pipeline test (verify Enricher called per ocrMode)
- Diagnostics speed lines verified on device
- Enricher injectable (EnricherApi exists but no fake test yet)

## Releases
- phase4-cp1d: verified (emulator tests green)
- phase4-cp2: INCOMPLETE - do not test
- phase4-cp2b: CI GREEN — publishing now
