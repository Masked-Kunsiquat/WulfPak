# scripts

Dev and QA utilities. None of these are part of the Android build.

Run all Python scripts with `uv run python scripts/<name>.py` — `python3` is not on PATH.

## Scripts

### `validate_seed.py`

Sanity-checks a WulfPak backup JSON file before import.

```bash
uv run python scripts/validate_seed.py <file.json>
# defaults to wulfpak_seed_20260503_151736.json if no arg given
```

**Checks**:
1. Duplicate UUIDs within each table
2. `contactDetails.personId` → `persons` foreign key
3. `interactionParticipants` → both `interactions` and `persons`
4. `activityParticipants` → both `activities` and `persons`
5. `notes.personId` → `persons`
6. `lifeEvents.personId` → `persons`
7. `gifts.personId` → `persons`
8. `personRelationships` — both IDs valid; warns if `personAId >= personBId` (DB enforces A < B); warns on duplicate pairs
9. Orphaned interactions (no participant row)
10. Exactly one `isMe = true` person

Prints a summary table (person count, relation label breakdown, etc.). Exit code 0 = warnings only; exit code 1 = errors.

---

### `generate_seed_data.py`

Generates a realistic fake-contact dataset via the Gemini 2.5 Flash API and writes it as a WulfPak backup JSON (importable via Settings → Import backup).

Supports both local (`uv run`) and Kaggle notebook execution. Requires `GEMINI_API_KEY` env var (or Kaggle secret of the same name).

Generates: persons, contact_details, interactions, activities, notes, life_events, gifts, person_relationships.

After generating, validate with `validate_seed.py` before importing.

---

### `convert_embedding_model.py` (and `convert_embedding_model_local.py`)

Downloads Snowflake Arctic Embed XS (PyTorch, ~90 MB) from Hugging Face, exports to ONNX, and converts to TFLite float32 via `onnx2tf`.

Output: `scripts/snowflake-arctic-embed-xs.tflite` (~86 MB)

After conversion, copy to `core-logic/src/main/assets/` and rebuild. The `_arctic_tmp.onnx.data` and `.tflite` files in `scripts/` are build artefacts — not committed.

```bash
uv run python scripts/convert_embedding_model.py
cp scripts/snowflake-arctic-embed-xs.tflite core-logic/src/main/assets/
```
