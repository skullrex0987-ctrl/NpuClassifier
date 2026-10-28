# NPU Image Classifier (Poco X6 Pro / MediaTek)

Classifies a picked image fully on-device and compares NPU (TFLite -> NNAPI -> MediaTek driver) vs CPU speed.

- Humans: read `docs/HUMAN_GUIDE.md`
- AI agents (Hermes, Claude Code, etc.): read `AGENTS.md`

Quick start: add `model.tflite` (quantized uint8 classifier) and `labels.txt` to `app/src/main/assets/`,
open in Android Studio, run on your phone.

Status: reviewed but NOT compiled by its author. Expect minor build fixes.
