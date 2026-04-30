# /// script
# requires-python = ">=3.11,<3.13"
# dependencies = [
#   "torch",
#   "transformers>=4.40",
#   "sentencepiece",
#   "onnx>=1.14",
#   "onnx2tf>=1.20",
#   "tensorflow>=2.16",
#   "onnxruntime",
#   "flatbuffers",
#   "numpy<2.0",
# ]
#
# [[tool.uv.index]]
# name = "pytorch-cpu"
# url = "https://download.pytorch.org/whl/cpu"
# explicit = true
#
# [tool.uv.sources]
# torch = { index = "pytorch-cpu" }
# ///
#
# Usage:
#   uv run scripts/convert_embedding_model_local.py
#
# Output: scripts/snowflake-arctic-embed-xs.tflite  (~66 MB, float32)
# Drop it into: core-logic/src/main/assets/  (replace the float16 file)

import os, sys, shutil

HERE        = os.path.dirname(__file__)
MODEL_ID    = "Snowflake/snowflake-arctic-embed-xs"
SEQ_LEN     = 128
ONNX_PATH   = os.path.join(HERE, "_arctic_tmp.onnx")
TFLITE_DIR  = os.path.join(HERE, "_arctic_tflite_tmp")
OUTPUT_PATH = os.path.join(HERE, "snowflake-arctic-embed-xs.tflite")

print("=== Arctic Embed XS → float32 TFLite ===\n")

# ── Step 1: Load PyTorch model ────────────────────────────────────────────────
print("[1/4] Loading PyTorch model (downloads ~90 MB on first run)...")
import torch
from transformers import AutoModel, AutoTokenizer

tokenizer = AutoTokenizer.from_pretrained(MODEL_ID)
model     = AutoModel.from_pretrained(MODEL_ID)
model.eval()
print("      Loaded.")

# ── Step 2: Export to ONNX ────────────────────────────────────────────────────
print("\n[2/4] Exporting to ONNX...")
dummy_ids   = torch.ones(1, SEQ_LEN, dtype=torch.long)
dummy_mask  = torch.ones(1, SEQ_LEN, dtype=torch.long)
dummy_types = torch.zeros(1, SEQ_LEN, dtype=torch.long)

with torch.no_grad():
    torch.onnx.export(
        model,
        (dummy_ids, dummy_mask, dummy_types),
        ONNX_PATH,
        input_names  = ["input_ids", "attention_mask", "token_type_ids"],
        output_names = ["last_hidden_state"],
        opset_version        = 17,
        do_constant_folding  = True,
    )
size_mb = os.path.getsize(ONNX_PATH) / 1024 / 1024
print(f"      {ONNX_PATH}  ({size_mb:.1f} MB)")

# ── Step 3: ONNX → TFLite via onnx2tf ────────────────────────────────────────
print("\n[3/4] Converting ONNX → TFLite float32 (may take a few minutes)...")
os.makedirs(TFLITE_DIR, exist_ok=True)
import onnx2tf
onnx2tf.convert(
    input_onnx_file_path = ONNX_PATH,
    output_folder_path   = TFLITE_DIR,
    non_verbose          = True,
)

tflite_files = [f for f in os.listdir(TFLITE_DIR) if f.endswith(".tflite")]
assert tflite_files, f"No .tflite found in {TFLITE_DIR} — check onnx2tf output above"
# onnx2tf produces both a float32 and a _float16 variant; pick the float32 one
float32_files = [f for f in tflite_files if "_float16" not in f]
tflite_src = os.path.join(TFLITE_DIR, (float32_files or tflite_files)[0])
shutil.copy(tflite_src, OUTPUT_PATH)
size_mb = os.path.getsize(OUTPUT_PATH) / 1024 / 1024
print(f"      Written: {OUTPUT_PATH}  ({size_mb:.1f} MB)")

# ── Step 4: Inspect + sanity check ───────────────────────────────────────────
print("\n[4/4] Verifying...")
import tensorflow as tf
import numpy as np

interp = tf.lite.Interpreter(model_path=OUTPUT_PATH)
interp.allocate_tensors()

print("      Input tensors:")
for t in interp.get_input_details():
    print(f"        [{t['index']}] '{t['name']}'  {t['shape']}  {t['dtype']}")
print("      Output tensors:")
for t in interp.get_output_details():
    print(f"        [{t['index']}] '{t['name']}'  {t['shape']}  {t['dtype']}")

def embed(text: str) -> np.ndarray:
    enc = tokenizer(text, return_tensors="np", max_length=SEQ_LEN,
                    padding="max_length", truncation=True)
    for t in interp.get_input_details():
        name = t["name"]
        if   "input_ids"      in name: interp.set_tensor(t["index"], enc["input_ids"].astype(t["dtype"]))
        elif "attention_mask" in name: interp.set_tensor(t["index"], enc["attention_mask"].astype(t["dtype"]))
        elif "token_type_ids" in name: interp.set_tensor(t["index"], enc["token_type_ids"].astype(t["dtype"]))
    interp.invoke()
    last_hidden = interp.get_tensor(interp.get_output_details()[0]["index"])  # [1, 128, 384]
    mask = enc["attention_mask"][0].astype(np.float32)
    return (last_hidden[0] * mask[:, None]).sum(0) / mask.sum()

def cosine(a, b):
    return float(np.dot(a, b) / (np.linalg.norm(a) * np.linalg.norm(b)))

e1 = embed("had dinner at Sharkey's with friends last night")
e2 = embed("went out for food and drinks with the group")
e3 = embed("quarterly earnings report and budget forecast")
print(f"\n      dinner <-> food   : {cosine(e1, e2):.4f}  (expect high  >0.7)")
print(f"      dinner <-> finance: {cosine(e1, e3):.4f}  (expect low   <0.4)")
assert cosine(e1, e2) > cosine(e1, e3), "Similarity ordering wrong — conversion may have failed"
print("      Sanity check passed!")

# ── Cleanup ───────────────────────────────────────────────────────────────────
os.remove(ONNX_PATH)
shutil.rmtree(TFLITE_DIR, ignore_errors=True)

print(f"\n=== Done! ===")
print(f"Output : {OUTPUT_PATH}")
print(f"Next   : copy to core-logic/src/main/assets/ (replace the float16 file)")
print(f"Note   : vocab.txt does NOT need replacing")
