# Human Co-worker Guide

## 1. What this is
A small Android app that classifies a picked image **fully on-device** and compares:
- **NPU path:** TensorFlow Lite -> NNAPI delegate -> the phone's MediaTek driver (APU / NeuroPilot stack)
- **CPU path:** TensorFlow Lite on 4 CPU threads

It prints top-3 labels and average ms/inference per backend.

Target phone: Poco X6 Pro (MediaTek Dimensity 8300 Ultra). Works on other Android 9+ phones too.

## 2. Important limits (read first)
- **This is NOT the Neuron/NeuroPilot SDK.** MediaTek gives that SDK only to approved direct customers
  (MediaTek Online account). AnTuTu's AI benchmark can use it; we cannot. NNAPI is the public route to the same hardware,
  so speed should be close but may not match AnTuTu.
- **The project has not been compiled or run by its author.** Expect to fix small build errors on first sync.
- NNAPI is deprecated from Android 15 but still works on current devices.

## 3. Prerequisites
- Android Studio (recent stable) with Android SDK 34 and JDK 17
- A phone with Developer options + USB debugging on, and a USB cable
- Internet for the first Gradle sync

## 4. Setup
1. Unzip and open the `NpuClassifier` folder in Android Studio. Accept the Gradle sync.
   (`gradle/wrapper/gradle-wrapper.properties` pins Gradle 8.7. If you want `./gradlew` on the command line,
   run `gradle wrapper --gradle-version 8.7` once, using any installed Gradle.)
2. Get a model: a **quantized (uint8) image-classification `.tflite`**, e.g. MobileNet V2 1.0 224 quant,
   from Kaggle Models, TF Hub archive or Hugging Face.
3. Open the model in https://netron.app and confirm:
   - input: `uint8 [1, H, W, 3]`
   - output: `[1, N]`
4. Create `labels.txt` with exactly N lines in output order
   (the 1001-class MobileNet needs the leading `background` line; the 1000-class one does not).
5. Copy into `app/src/main/assets/` as **`model.tflite`** and **`labels.txt`**.
6. Select your phone, press Run, tap **Pick image**.

## 5. Reading the results
- `NPU_NNAPI  (x ms/inference)` vs `CPU  (y ms/inference)`
- If x is clearly lower than y -> the NPU is doing the work.
- If x ~= y or higher -> the model fell back to CPU (unsupported ops, float model, or driver limits).
- A `WARNING: ... labels.txt` line means labels and model outputs don't match; fix labels.txt.

Optional check: `adb logcat | grep -i -E "nnapi|neuron|mtk|apu|tflite"` while tapping Pick image.

## 6. Troubleshooting
| Symptom | Likely cause / fix |
|---|---|
| `FileNotFoundException model.tflite` | Model not in `app/src/main/assets/` or wrong name |
| `Unexpected input shape` | Not an image classifier with [1,H,W,3] input |
| Gibberish labels | labels.txt order/length mismatch (see warning line) |
| NPU slower than CPU | Use a full-integer quantized model; try a different model |
| `NPU_NNAPI failed: ...` | Driver rejected the model; CPU result still valid |
| Gradle/AGP version errors | Update AGP/Kotlin versions in root `build.gradle.kts` to what your Android Studio expects |
| Float model gives poor accuracy | Change normalization in `Classifier.prepareInput` to match the model's training |

## 7. Customizing categories
Fine-tune a MobileNet / EfficientNet-Lite with Keras transfer learning (Colab is fine), convert to `.tflite`
with **full-integer quantization** using a representative dataset, then replace `model.tflite` and `labels.txt`.

## 8. Ideas for next steps
- Camera live-classification (CameraX)
- Try Google's LiteRT NeuroPilot Accelerator (newer, MediaTek-supported path; check Dimensity 8300 support)
- Export timing results to CSV
