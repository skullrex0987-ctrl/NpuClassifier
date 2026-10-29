# AGENTS.md - instructions for AI coding agents (Hermes etc.)

## This drop = Phase 1 of docs/SPEC.md. Read docs/SPEC.md first.

## Applying it
1. Copy these files over the repo (same paths). Package stays `com.example.npuclassifier`.
2. IMPORTANT: `app/build.gradle.kts` is replaced. If you previously edited it or the root build files / CI to make the
   build pass (versions, repos, SDK), MERGE rather than overwrite, and keep what made CI work.
3. KEEP `app/src/main/assets/model.tflite` and `labels.txt` (MobileNet, quantized). Do not delete them.
4. Delete anything left over from the old UI (old MainActivity layouts, AppCompat theme usage).
5. Build: `./gradlew assembleDebug`. Fix compile errors minimally. The code was NOT compiled by its author.

## Known things to check first
- Compose compiler 1.5.14 <-> Kotlin 1.9.24 must match. AGP 8.5.2 needs Gradle >= 8.7 and JDK 17.
- minSdk is 30 (Environment.isExternalStorageManager).
- `tensorflow-lite-gpu` + `-gpu-api` 2.16.1 are used for the GPU backend. If GPU delegate init crashes, the app must show
  "GPU unavailable: ..." and continue with other backends (it does via onBackendError) - keep that.
- GPU delegate must be created/used/closed on one thread. Do NOT move Classifier creation outside SortEngine.worker.

## Verify on the phone (report results)
- Pick folder -> files found; Scan -> progress, per-backend stats ("NPU: n files, x ms/file").
- Select NPU+GPU+CPU together: all three process files in parallel.
- Moving the confidence slider changes "ready to sort / needs review" counts instantly.
- Copy creates Pictures/Sorted/<Label>/ (auto-created); videos land in Pictures/Sorted/Videos/<Label>/.
- Undo removes the copies (or moves originals back if Move was on).
- Review tab: tapping a chip files the item.

## Rules
- Never delete or overwrite user originals except in explicit Move mode; Copy is the default.
- Do not add INTERNET permission yet (planned for Phase 4 model download only).
- Do not fake results. If a backend falls back or fails, say so.

## Report format
```
Build: PASS/FAIL (+ fixes)
Device/Android: ...
Backends tested: CPU/GPU/NPU -> works? ms/file each
Parallel split works: yes/no
Video sorting tested: yes/no
Issues: ...
```
