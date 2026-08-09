# Third-party notices

## DocQuadNet-256 model (`app/src/main/assets/docquad/`)

- Source: [MakeACopy](https://github.com/egdels/makeacopy) by Christian Kierdorf
- File: `docquadnet256_trained_opset17.ort`
- License: Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0)
- Copyright 2025 Christian Kierdorf

Used unmodified for on-device document corner detection (see
`com.example.scanby.core.vision.DocQuadDetector`). No training data, images, labels, or
intermediate checkpoints were copied — only the exported inference model file.

## ONNX Runtime

- Source: [microsoft/onnxruntime](https://github.com/microsoft/onnxruntime)
- License: MIT License
