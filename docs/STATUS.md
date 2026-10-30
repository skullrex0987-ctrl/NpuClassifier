# CP2 STATUS — 2026-10-30

## Green (CI 41056071466)
- emulator-tests: StopTest, ScanEndToEndTest, NoDuplicateProcessingTest, OcrEmulatorTest (skipped if ML Kit empty)
- DecisionPriorityTest (fixed: priority reorder + extraLabels)
- PrefsRoundTripTest, StarterRulesTest, ScreenshotTyperTest, ProgressMathTest, RepoClaimTest, RepoCountsTest, RrfMergeTest

## Red
- FtsSpecialCharsTest.prefixQuery — FTS returns empty for "pay" against "payment upi transaction"
  - 7 CI runs failed; root cause NOT yet proven
  - Hypotheses: H1 sanitizer breaks prefix, H2 Robolectric SQLite, H3 docid mismatch, H4 saveEnrichment overwrites/deletes, H5 test wrong
  - Next: offline SQL test (step 2), then ONE diagnostic CI run (step 3)

## Not done
- FTS prefix query verified
- Settings UI for all new fields (scan mode, smart model, OCR mode, Hindi, tags, search model, index while charging, Fastest preset, diagnostics, background setup guide)
- Semantic search (embed + cosine + RRF in UI) + "N of M indexed" + Build index
- FileOps multi-folder copy test (dental X-ray -> Teeth AND X-ray)
- Fake-Enricher pipeline test (verify Enricher called per ocrMode)
- Diagnostics speed lines verified on device
- phase4-cp2b NOT published (CI red)

## Releases
- phase4-cp1d: verified (emulator tests green)
- phase4-cp2: INCOMPLETE - do not test
- phase4-cp2b: tag exists, CI red, NOT published
