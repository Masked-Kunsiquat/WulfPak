# /// script
# requires-python = ">=3.11"
# dependencies = [
#   "tensorflow>=2.16",
#   "transformers>=4.40",
#   "sentencepiece",
#   "numpy",
# ]
# ///
#
# Usage (no Python install needed — just uv):
#   winget install astral-sh.uv          (or: powershell -c "irm https://astral.sh/uv/install.ps1 | iex")
#   uv run scripts/convert_embedding_model_local.py
#
# Output: scripts/snowflake-arctic-embed-xs.tflite  (~66 MB, float32)
# Then drop it into: core-logic/src/main/assets/  (replace the float16 file)

import os, sys

MODEL_ID     = "Snowflake/snowflake-arctic-embed-xs"
SEQ_LEN      = 128
OUTPUT_PATH  = os.path.join(os.path.dirname(__file__), "snowflake-arctic-embed-xs.tflite")
SAVED_MODEL  = os.path.join(os.path.dirname(__file__), "_arctic_saved_model_tmp")

print("=== Arctic Embed XS → float32 TFLite ===\n")

import tensorflow as tf
print(f"TensorFlow {tf.__version__}")

from transformers import TFAutoModel, AutoTokenizer
import numpy as np

print("\n[1/4] Loading tokenizer + model (downloads ~90 MB on first run)...")
tokenizer = AutoTokenizer.from_pretrained(MODEL_ID)
tf_model  = TFAutoModel.from_pretrained(MODEL_ID, from_pt=True)
print("      Model loaded.")

print("\n[2/4] Wrapping with fixed-shape signature and saving SavedModel...")

class EmbedWrapper(tf.Module):
    def __init__(self, model):
        super().__init__()
        self.model = model

    @tf.function(input_signature=[
        tf.TensorSpec(shape=[1, SEQ_LEN], dtype=tf.int32, name="input_ids"),
        tf.TensorSpec(shape=[1, SEQ_LEN], dtype=tf.int32, name="attention_mask"),
        tf.TensorSpec(shape=[1, SEQ_LEN], dtype=tf.int32, name="token_type_ids"),
    ])
    def __call__(self, input_ids, attention_mask, token_type_ids):
        out = self.model(
            input_ids=input_ids,
            attention_mask=attention_mask,
            token_type_ids=token_type_ids,
            training=False,
        )
        return {"last_hidden_state": out.last_hidden_state}

wrapper = EmbedWrapper(tf_model)
tf.saved_model.save(wrapper, SAVED_MODEL, signatures={"serving_default": wrapper.__call__})
print("      Saved.")

print("\n[3/4] Converting to TFLite float32 (may take a few minutes)...")
converter = tf.lite.TFLiteConverter.from_saved_model(SAVED_MODEL, signature_keys=["serving_default"])
converter.target_spec.supported_ops = [
    tf.lite.OpsSet.TFLITE_BUILTINS,
    tf.lite.OpsSet.SELECT_TF_OPS,
]
converter._experimental_lower_tensor_list_ops = False
tflite_bytes = converter.convert()

with open(OUTPUT_PATH, "wb") as f:
    f.write(tflite_bytes)
size_mb = len(tflite_bytes) / 1024 / 1024
print(f"      Written: {OUTPUT_PATH}  ({size_mb:.1f} MB)")

print("\n[4/4] Sanity check...")
interp = tf.lite.Interpreter(model_path=OUTPUT_PATH)
interp.allocate_tensors()

print("      Input tensors:")
for t in interp.get_input_details():
    print(f"        [{t['index']}] '{t['name']}'  {t['shape']}  {t['dtype']}")
print("      Output tensors:")
for t in interp.get_output_details():
    print(f"        [{t['index']}] '{t['name']}'  {t['shape']}  {t['dtype']}")

def embed(text):
    enc = tokenizer(text, return_tensors="np", max_length=SEQ_LEN, padding="max_length", truncation=True)
    for t in interp.get_input_details():
        name = t["name"]
        if   "input_ids"      in name: interp.set_tensor(t["index"], enc["input_ids"].astype(np.int32))
        elif "attention_mask" in name: interp.set_tensor(t["index"], enc["attention_mask"].astype(np.int32))
        elif "token_type_ids" in name: interp.set_tensor(t["index"], enc["token_type_ids"].astype(np.int32))
    interp.invoke()
    last_hidden = interp.get_tensor(interp.get_output_details()[0]["index"])
    mask = enc["attention_mask"][0].astype(np.float32)
    return (last_hidden[0] * mask[:, None]).sum(0) / mask.sum()

def cosine(a, b):
    return float(np.dot(a, b) / (np.linalg.norm(a) * np.linalg.norm(b)))

e1 = embed("had dinner at Sharkey's with friends last night")
e2 = embed("went out for food and drinks with the group")
e3 = embed("quarterly earnings report and budget forecast")
print(f"      dinner <-> food   : {cosine(e1, e2):.4f}  (expect high)")
print(f"      dinner <-> finance: {cosine(e1, e3):.4f}  (expect low)")
assert cosine(e1, e2) > cosine(e1, e3), "Similarity ordering wrong!"
print("      Sanity check passed!\n")

# Clean up temp saved model
import shutil
shutil.rmtree(SAVED_MODEL, ignore_errors=True)

print(f"=== Done! ===")
print(f"Output: {OUTPUT_PATH}")
print(f"Next: copy it to core-logic/src/main/assets/ (replace the float16 file)")
print(f"Note: vocab.txt does NOT need replacing.")
