# facts: vision (ML Kit)

## APIs used
- com.google.mlkit:text-recognition:16.0.1 (bundled, Latin) — com.google.mlkit.vision.text.TextRecognition, TextRecognizerOptions.DEFAULT_OPTIONS
- com.google.mlkit:text-recognition-devanagari:16.0.1 (bundled) — DevanagariTextRecognizerOptions.Builder().build()
- com.google.mlkit.vision.common.InputImage.fromBitmap(Bitmap, rotationDegrees)
- TextRecognizer.process(InputImage): Task<Text>
- com.google.android.gms.tasks.Tasks.await(Task) — must NOT be called on main thread
- TextRecognizer.close()
- Docs: https://developers.google.com/ml-kit/vision/text-recognition/v2/android

## Risks
- ML Kit may not run on x86_64 emulator (unverified). If so, OCR test skipped.
- Bitmap must be recycled after use to avoid OOM.

## Plan
1. Add bundled deps.
2. Enricher.run decodes bitmap, runs Latin + Devanagari recognizers, returns OCR text.
3. Call in SortEngine according to cfg.ocrMode.
4. Save via Repo.saveEnrichment.