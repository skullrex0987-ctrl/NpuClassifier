#!/bin/bash
set -e
mkdir -p app/src/main/assets/clip
BASE="https://huggingface.co/Xenova/clip-vit-base-patch32/resolve/main"
curl -L --fail -o app/src/main/assets/clip/vision.onnx "$BASE/onnx/vision_model_quantized.onnx"
curl -L --fail -o app/src/main/assets/clip/text.onnx "$BASE/onnx/text_model_quantized.onnx"
curl -L --fail -o app/src/main/assets/clip/tokenizer.json "$BASE/tokenizer.json"
echo "Downloaded models:"
ls -lh app/src/main/assets/clip/