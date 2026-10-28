# AGENTS.md - Instructions for AI coding agents (Hermes, Claude Code, Codex, etc.)

## Goal
Get the Android app in this repo to **build, install, and run** on a MediaTek (Dimensity 8300 Ultra) phone,
classifying a picked image locally and reporting NPU (NNAPI) vs CPU latency.

## Hard constraints
- Do NOT try to obtain or fake the MediaTek NeuroPilot / Neuron SDK. It is gated to MediaTek direct customers. Use TFLite + NNAPI only.
- Do NOT invent model download URLs. If you cannot download a model, stop and ask the human to supply `model.tflite` + `labels.txt`.
- Keep the CPU fallback. Never make the app crash if the NPU path fails (it must show "NPU_NNAPI failed: ...").
- Do not bump `tensorflow-lite` major/minor versions unless the build requires it; if you do, report it.
- Treat the existing code as UNVERIFIED: it was never compiled by its author.

## Repo map
- `app/build.gradle.kts` - deps (`org.tensorflow:tensorflow-lite:2.16.1`), `noCompress += "tflite"`, minSdk 28
- `app/src/main/java/com/example/npuclassifier/Classifier.kt` - model loading, NNAPI/CPU interpreter, preprocessing, timing
- `app/src/main/java/com/example/npuclassifier/MainActivity.kt` - programmatic UI, image picker, background thread
- `app/src/main/assets/` - must contain `model.tflite` and `labels.txt` (NOT in repo; human supplies or you fetch from a verifiable source)
- `docs/HUMAN_GUIDE.md` - human-facing explanation

## Task list (do in order)
1. Check env: `java -version` (17), Android SDK (`ANDROID_HOME`), `adb version`. If the SDK is missing, report it; do not proceed blindly.
2. Generate wrapper if absent: `gradle wrapper --gradle-version 8.7`.
3. `./gradlew assembleDebug`. Fix compile errors minimally (API names, imports, version compatibility between AGP/Kotlin/Gradle).
4. Ensure `app/src/main/assets/model.tflite` and `labels.txt` exist and match:
   - input must be `uint8 [1,H,W,3]` (or float32), output `[1,N]`
   - `labels.txt` has exactly N non-empty lines
   - verify with Python: `tf.lite.Interpreter(model_path=...).get_input_details()/get_output_details()` if available
5. `adb devices` -> `./gradlew installDebug` -> launch `com.example.npuclassifier/.MainActivity`.
6. Verify (needs a human to tap, or use `adb shell input` + a test image pushed to the gallery):
   - app launches without crash: `adb logcat -d | grep -i -E "AndroidRuntime|FATAL"` is empty
   - result shows both `NPU_NNAPI` and `CPU` sections with ms/inference and 3 labels
7. Report: build output, device model, both latencies, whether NPU < CPU, any warnings.

## Definition of done
- `assembleDebug` passes
- App installs and shows results for both backends on the target phone
- If NPU is not faster than CPU, say so plainly and list likely causes (float model, unsupported ops, driver fallback) - do not claim NPU acceleration without the timing evidence

## Known risks / things to check first
- AGP 8.5.2 needs Gradle >= 8.7 and JDK 17.
- `ImageDecoder` needs minSdk 28 (already set).
- Float models: preprocessing assumes [-1,1]; some models need [0,1] or mean/std. Adjust in `prepareInput`.
- The 1001-class MobileNet has a leading `background` label.
- Only one inference job runs at a time (guarded by `lock` + disabled button). Keep that.

## Report format (return to the human)
```
Build: PASS/FAIL (+ errors fixed)
Device: <model>, Android <ver>
Model: <file>, input <shape/type>, output <shape/type>
NPU_NNAPI: <ms>  CPU: <ms>  Speedup: <x>
Top-3 on test image: ...
Issues / next steps: ...
```
