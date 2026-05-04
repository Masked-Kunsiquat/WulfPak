"""
Quick sanity-check for a WulfPak backup JSON.
Usage: uv run python scripts/validate_seed.py <file.json>
"""
import json, sys
from collections import defaultdict

path = sys.argv[1] if len(sys.argv) > 1 else "wulfpak_seed_20260503_151736.json"
with open(path, encoding="utf-8") as f:
    data = json.load(f)

errors = []
warnings = []

def err(msg):
    errors.append(f"  ERROR: {msg}")


def warn(msg):
    warnings.append(f"  WARN : {msg}")

# ── Build ID sets ─────────────────────────────────────────────────────────────
person_ids   = set()
person_by_id = {}
for _p in data.get("persons", []):
    _pid = _p.get("id")
    if _pid is None:
        err(f"persons: record missing 'id' field: {_p}")
    else:
        person_ids.add(_pid)
        person_by_id[_pid] = _p

interaction_ids = {i["id"] for i in data.get("interactions", []) if "id" in i}
activity_ids    = {a["id"] for a in data.get("activities", [])    if "id" in a}

# ── 1. Duplicate UUIDs within each table ─────────────────────────────────────
for table_key in ("persons", "contactDetails", "interactions", "notes", "lifeEvents", "gifts", "activities"):
    ids = [r["id"] for r in data.get(table_key, [])]
    seen = set(); dupes = set()
    for i in ids:
        if i in seen: dupes.add(i)
        seen.add(i)
    if dupes:
        err(f"{table_key}: duplicate IDs: {dupes}")

# ── 2. contactDetails.personId → persons ─────────────────────────────────────
for cd in data.get("contactDetails", []):
    if cd["personId"] not in person_ids:
        err(f"contactDetails id={cd['id']}: unknown personId {cd['personId']}")

# ── 3. interactionParticipants ────────────────────────────────────────────────
for ip in data.get("interactionParticipants", []):
    if ip["interactionId"] not in interaction_ids:
        err(f"interactionParticipants: unknown interactionId {ip['interactionId']}")
    if ip["personId"] not in person_ids:
        err(f"interactionParticipants: unknown personId {ip['personId']} (interactionId={ip['interactionId']})")

# ── 4. activityParticipants ───────────────────────────────────────────────────
for ap in data.get("activityParticipants", []):
    if ap["activityId"] not in activity_ids:
        err(f"activityParticipants: unknown activityId {ap['activityId']}")
    if ap["personId"] not in person_ids:
        err(f"activityParticipants: unknown personId {ap['personId']} (activityId={ap['activityId']})")

# ── 5. notes.personId ────────────────────────────────────────────────────────
for n in data.get("notes", []):
    if n["personId"] not in person_ids:
        err(f"notes id={n['id']}: unknown personId {n['personId']}")

# ── 6. lifeEvents.personId ───────────────────────────────────────────────────
for e in data.get("lifeEvents", []):
    if e["personId"] not in person_ids:
        err(f"lifeEvents id={e['id']}: unknown personId {e['personId']}")

# ── 7. gifts.personId ────────────────────────────────────────────────────────
for g in data.get("gifts", []):
    if g["personId"] not in person_ids:
        err(f"gifts id={g['id']}: unknown personId {g['personId']}")

# ── 8. personRelationships ────────────────────────────────────────────────────
seen_pairs = set()
for r in data.get("personRelationships", []):
    a, b = r["personAId"], r["personBId"]
    if a not in person_ids:
        err(f"personRelationships: unknown personAId {a}")
    if b not in person_ids:
        err(f"personRelationships: unknown personBId {b}")
    if a >= b:
        pA = person_by_id.get(a, {})
        pB = person_by_id.get(b, {})
        warn(f"personRelationships: personAId >= personBId ({pA.get('firstName')} vs {pB.get('firstName')}) — DB will reject or flip")
    pair = (min(a,b), max(a,b))
    if pair in seen_pairs:
        warn(f"personRelationships: duplicate pair {pair}")
    seen_pairs.add(pair)

# ── 9. Orphaned interactions (no participant) ─────────────────────────────────
i_with_participant = {ip["interactionId"] for ip in data.get("interactionParticipants", [])}
for i in data.get("interactions", []):
    iid = i.get("id")
    if iid is not None and iid not in i_with_participant:
        warn(f"interaction id={iid} has no participants")

# ── 10. isMe count ────────────────────────────────────────────────────────────
me_persons = [p for p in data["persons"] if p.get("isMe")]
if len(me_persons) != 1:
    err(f"Expected exactly 1 isMe person, found {len(me_persons)}")

# ── Summary ───────────────────────────────────────────────────────────────────
print(f"\n{'='*55}")
print(f"  File : {path}")
print(f"{'='*55}")
print(f"  DB version        : {data.get('version')}")
print(f"  Persons           : {len(data.get('persons', []))} ({len(me_persons)} isMe)")
non_me = [p for p in data["persons"] if not p.get("isMe")]
print(f"  Contacts          : {len(non_me)}")
print(f"  Contact details   : {len(data.get('contactDetails', []))}")
print(f"  Interactions      : {len(data.get('interactions', []))}")
print(f"  Interaction ptcps : {len(data.get('interactionParticipants', []))}")
print(f"  Notes             : {len(data.get('notes', []))}")
print(f"  Life events       : {len(data.get('lifeEvents', []))}")
print(f"  Gifts             : {len(data.get('gifts', []))}")
print(f"  Activities        : {len(data.get('activities', []))}")
print(f"  Activity ptcps    : {len(data.get('activityParticipants', []))}")
print(f"  Relationships     : {len(data.get('personRelationships', []))}")

# Relation label breakdown
from collections import Counter
rel_counts = Counter(p.get("relationLabel", "<missing>") for p in non_me)
print("\n  Relation label breakdown:")
for label, count in sorted(rel_counts.items(), key=lambda x: -x[1]):
    print(f"    {label:<20} {count}")

print(f"\n  {'-'*45}")
if errors:
    print(f"  ERRORS ({len(errors)}):")
    for e in errors: print(e)
else:
    print("  No errors found.")
if warnings:
    print(f"\n  WARNINGS ({len(warnings)}):")
    for w in warnings: print(w)
else:
    print("  No warnings.")
print(f"{'='*55}\n")
sys.exit(1 if errors else 0)
