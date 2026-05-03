"""
WulfPak seed data generator — Kaggle/Gemini API version.

Generates realistic fake contacts in WulfPak's backup JSON format.
The output file can be imported directly via Settings → Contacts → Import backup.

Usage (Kaggle):
    1. Add your Gemini API key as a Kaggle secret named GEMINI_API_KEY
    2. Run all cells — the .json file appears in the output panel for download

Usage (local):
    uv run --with google-generativeai python generate_seed_data.py
"""

import json
import uuid
import os
from datetime import datetime, timezone, timedelta
import random

# ── Setup ──────────────────────────────────────────────────────────────────────

try:
    from kaggle_secrets import UserSecretsClient  # noqa: F401
    IN_KAGGLE = True
except ImportError:
    IN_KAGGLE = False

if IN_KAGGLE:
    from kaggle_secrets import UserSecretsClient
    GEMINI_API_KEY = UserSecretsClient().get_secret("GEMINI_API_KEY")
else:
    GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY", "")

if not GEMINI_API_KEY:
    raise RuntimeError("Set GEMINI_API_KEY env var or Kaggle secret")

import google.generativeai as genai
genai.configure(api_key=GEMINI_API_KEY)
model = genai.GenerativeModel("gemini-2.5-flash")

DB_VERSION = 8
NOW_MS = int(datetime.now(timezone.utc).timestamp() * 1000)

REL_LABELS = [
    "friend", "best_friend", "acquaintance", "romantic_partner",
    "mother", "father", "sibling", "child", "grandparent", "cousin", "aunt", "uncle",
    "colleague", "manager", "report", "mentor", "client",
]

FAMILY_LABELS = {"mother", "father", "sibling", "child", "grandparent", "cousin", "aunt", "uncle"}
SOCIAL_LABELS = {"friend", "best_friend", "acquaintance", "romantic_partner"}
WORK_LABELS   = {"colleague", "manager", "report", "mentor", "client"}

def rel_category(label):
    if label in FAMILY_LABELS:
        return "FAMILY"
    if label in SOCIAL_LABELS:
        return "SOCIAL"
    if label in WORK_LABELS:
        return "WORK"
    return "OTHER"

def make_uuid():
    return str(uuid.uuid4())

def ms_days_ago(days):
    return int((datetime.now(timezone.utc) - timedelta(days=days)).timestamp() * 1000)

def ms_date(year, month, day):
    return int(datetime(year, month, day, tzinfo=timezone.utc).timestamp() * 1000)

def gemini_json(prompt):
    """Call Gemini and parse the JSON response."""
    resp = model.generate_content(
        prompt,
        generation_config=genai.GenerationConfig(response_mime_type="application/json"),
    )
    return json.loads(resp.text)

# ── Step 1: Generate the "me" person ──────────────────────────────────────────

def make_me():
    return {
        "id": make_uuid(),
        "firstName": "Demo",
        "lastName": "User",
        "nickname": None,
        "photoUri": None,
        "relationLabel": "friend",
        "isFavorite": False,
        "lastContactedAt": None,
        "interactionCount": 0,
        "closenessRating": None,
        "company": None,
        "jobTitle": None,
        "cachedSummary": None,
        "summaryGeneratedAt": None,
        "closenessScore": None,
        "isMe": True,
    }

# ── Step 2: Generate contacts ─────────────────────────────────────────────────

PERSONS_PROMPT = """
Generate a JSON array of 22 realistic fake people for a personal CRM app.
Use culturally diverse names (mix of American, European, Asian, Latino backgrounds).
No real people — completely fictional.

Each person object must have exactly these fields:
- firstName (string)
- lastName (string or null)
- nickname (string or null, ~25% have one)
- relationLabel: one of: {labels}
- isFavorite (boolean, ~20% true)
- company (string or null)
- jobTitle (string or null)
- closenessRating (integer 1–5 or null, ~60% have one)

Desired mix:
- 2 family: one mother or father, one sibling or cousin
- 1 romantic_partner or best_friend
- 5 friends
- 3 acquaintances
- 6 colleagues (mix of colleague, manager, report)
- 2 work-adjacent (mentor or client)
- 3 miscellaneous

Return ONLY a valid JSON array — no markdown, no explanation.
""".format(labels=", ".join(REL_LABELS))

def generate_persons():
    raw = gemini_json(PERSONS_PROMPT)
    persons = []
    for p in raw:
        persons.append({
            "id": make_uuid(),
            "firstName": p["firstName"],
            "lastName": p.get("lastName"),
            "nickname": p.get("nickname"),
            "photoUri": None,
            "relationLabel": p["relationLabel"],
            "isFavorite": p.get("isFavorite", False),
            "lastContactedAt": None,
            "interactionCount": 0,
            "closenessRating": p.get("closenessRating"),
            "company": p.get("company"),
            "jobTitle": p.get("jobTitle"),
            "cachedSummary": None,
            "summaryGeneratedAt": None,
            "closenessScore": None,
            "isMe": False,
        })
    return persons

# ── Step 3: Contact details ───────────────────────────────────────────────────

CONTACT_DETAILS_PROMPT = """
Generate contact details for these people. Use the relation to decide what's realistic:
- family → phone (mobile) + maybe email
- friends → phone (mobile), ~50% also have email
- colleagues → email (work) + maybe phone, ~30% have LinkedIn social
- acquaintances → just phone or just email
- mentor/client → email + maybe LinkedIn

Each contact detail:
- personId (from input)
- type: PHONE, EMAIL, SOCIAL, or ADDRESS
- label: e.g. "mobile", "home", "work email", "instagram", "linkedin"
- value: realistic fake value (fake phone numbers like (555) 867-5309, fake emails like name@domain.com)

People: {summary}

Return ONLY a valid JSON array of objects with fields: personId, type, label, value.
"""

def generate_contact_details(persons):
    summary = [
        {"id": p["id"], "firstName": p["firstName"], "lastName": p["lastName"], "relationLabel": p["relationLabel"]}
        for p in persons
    ]
    raw = gemini_json(CONTACT_DETAILS_PROMPT.format(summary=json.dumps(summary)))
    return [
        {
            "id": make_uuid(),
            "personId": d["personId"],
            "type": d["type"],
            "label": d["label"],
            "value": d["value"],
        }
        for d in raw
    ]

# ── Step 4: Life events ───────────────────────────────────────────────────────

LIFE_EVENTS_PROMPT = """
Generate life events for these people.

Rules:
- Everyone gets a birthday (use realistic birth years 1950–2002)
- romantic_partner gets an anniversary
- ~40% of friends/family also get a graduation, job_change, or moved event

eventType values: birthday, anniversary, graduation, job_change, moved

People: {summary}

Each event:
- personId
- eventType
- date: Unix timestamp in milliseconds (realistic past date)
- isRecurring: true for birthday/anniversary, false otherwise
- note: optional short string or null

Return ONLY a valid JSON array.
"""

def generate_life_events(persons):
    summary = [{"id": p["id"], "firstName": p["firstName"], "relationLabel": p["relationLabel"]} for p in persons]
    raw = gemini_json(LIFE_EVENTS_PROMPT.format(summary=json.dumps(summary)))
    return [
        {
            "id": make_uuid(),
            "personId": e["personId"],
            "eventType": e["eventType"],
            "date": e["date"],
            "isRecurring": e.get("isRecurring", False),
            "note": e.get("note"),
        }
        for e in raw
    ]

# ── Step 5: Interactions + participants ───────────────────────────────────────

INTERACTIONS_PROMPT = """
Generate interaction history for these contacts in a personal CRM.

Interaction types: call, text, email, video_call, in_person, social_media

Frequency guidelines:
- best_friend / family: 8–12 interactions over the past year
- friend: 4–7 interactions
- colleague / manager: 3–5 interactions, mostly email/call
- acquaintance: 1–2 interactions

For each interaction:
- personId (contact it was with)
- type (one of the types above)
- daysAgo: integer 1–400
- durationSeconds: integer or null (for call/video_call: 120–3600, else null)
- note: short realistic note string or null (~35% should have notes)

People: {summary}

Return ONLY a valid JSON array.
"""

def generate_interactions(persons):
    summary = [{"id": p["id"], "firstName": p["firstName"], "relationLabel": p["relationLabel"]} for p in persons]
    raw = gemini_json(INTERACTIONS_PROMPT.format(summary=json.dumps(summary)))

    interactions = []
    participants = []
    for item in raw:
        iid = make_uuid()
        interactions.append({
            "id": iid,
            "timestamp": ms_days_ago(item.get("daysAgo", random.randint(1, 200))),
            "type": item["type"],
            "durationSeconds": item.get("durationSeconds"),
            "note": item.get("note"),
        })
        participants.append({"interactionId": iid, "personId": item["personId"]})
    return interactions, participants

# ── Step 6: Notes ─────────────────────────────────────────────────────────────

NOTES_PROMPT = """
Generate personal notes about these contacts in a CRM. Notes capture things worth
remembering: preferences, allergies, mentioned projects, names of their kids, etc.

Quantity:
- best_friend / family: 3–5 notes
- friend: 2–3 notes
- colleague: 1–2 notes
- acquaintance: 0–1 notes

Each note:
- personId
- daysAgo: integer 1–600
- body: 1–3 sentence note text

People: {summary}

Return ONLY a valid JSON array.
"""

def generate_notes(persons):
    summary = [{"id": p["id"], "firstName": p["firstName"], "relationLabel": p["relationLabel"]} for p in persons]
    raw = gemini_json(NOTES_PROMPT.format(summary=json.dumps(summary)))
    return [
        {
            "id": make_uuid(),
            "personId": n["personId"],
            "timestamp": ms_days_ago(n.get("daysAgo", 60)),
            "body": n["body"],
        }
        for n in raw
    ]

# ── Step 7: Gifts ─────────────────────────────────────────────────────────────

GIFTS_PROMPT = """
Generate gift ideas and gift history for close contacts.

Focus on:
- best_friend / romantic_partner: 2–3 gifts
- family / friend: 1–2 gifts
- colleague: 0–1 gifts (office-appropriate)

Each gift:
- personId
- name: gift name
- occasion: birthday, christmas, anniversary, graduation, or null
- status: IDEA, PURCHASED, or GIVEN
- note: optional note or null

People: {summary}

Return ONLY a valid JSON array.
"""

def generate_gifts(persons):
    close = [p for p in persons if p["relationLabel"] in {"best_friend", "romantic_partner", "friend", "mother", "father", "sibling"}]
    if not close:
        return []
    summary = [{"id": p["id"], "firstName": p["firstName"], "relationLabel": p["relationLabel"]} for p in close]
    raw = gemini_json(GIFTS_PROMPT.format(summary=json.dumps(summary)))
    return [
        {
            "id": make_uuid(),
            "personId": g["personId"],
            "name": g["name"],
            "occasion": g.get("occasion"),
            "status": g.get("status", "IDEA"),
            "note": g.get("note"),
        }
        for g in raw
    ]

# ── Step 8: Activities (shared events) ───────────────────────────────────────

ACTIVITIES_PROMPT = """
Generate 6–8 shared activities/events that involve multiple people from this list.
Examples: "Game night at Jake's", "Team offsite in Austin", "Hiking trip", "Birthday dinner for Maria".

Each activity:
- title: short event title
- body: 1–2 sentence description or null
- daysAgo: integer 1–365
- participantIds: list of 2–4 person IDs from the input who attended

People available: {summary}

Return ONLY a valid JSON array with fields: title, body, daysAgo, participantIds.
"""

def generate_activities(persons):
    summary = [{"id": p["id"], "firstName": p["firstName"], "relationLabel": p["relationLabel"]} for p in persons]
    raw = gemini_json(ACTIVITIES_PROMPT.format(summary=json.dumps(summary)))

    activities = []
    participants = []
    for item in raw:
        aid = make_uuid()
        activities.append({
            "id": aid,
            "timestamp": ms_days_ago(item.get("daysAgo", random.randint(7, 180))),
            "title": item["title"],
            "body": item.get("body"),
        })
        for pid in item.get("participantIds", []):
            participants.append({"activityId": aid, "personId": pid})
    return activities, participants

# ── Step 9: Person-to-person relationships ────────────────────────────────────

RELATIONSHIPS_PROMPT = """
Given these contacts, generate 5–8 inter-contact relationships (people who know each other).
For example: two siblings both in the list, coworkers at the same company, mutual friends.

Important: personAId MUST be lexicographically less than personBId (string sort on UUIDs).
Label is from personA's perspective.

Use only these labels: friend, best_friend, acquaintance, sibling, romantic_partner, colleague, cousin

People: {summary}

Return ONLY a valid JSON array with fields: personAId, personBId, label.
"""

def generate_relationships(persons):
    summary = [{"id": p["id"], "firstName": p["firstName"], "relationLabel": p["relationLabel"]} for p in persons]
    raw = gemini_json(RELATIONSHIPS_PROMPT.format(summary=json.dumps(summary)))
    result = []
    seen = set()
    for r in raw:
        a, b = r["personAId"], r["personBId"]
        if a > b:
            a, b = b, a
        key = (a, b)
        if key in seen:
            continue
        seen.add(key)
        label = r["label"]
        result.append({
            "personAId": a,
            "personBId": b,
            "label": label,
            "category": rel_category(label),
            "relType": None,
        })
    return result

# ── Step 10: Wire up interaction counts ───────────────────────────────────────

def update_person_stats(persons, interactions, participants):
    from collections import defaultdict
    counts = defaultdict(int)
    last_ts = defaultdict(int)

    iid_to_ts = {i["id"]: i["timestamp"] for i in interactions}
    for p in participants:
        pid = p["personId"]
        ts = iid_to_ts.get(p["interactionId"], 0)
        counts[pid] += 1
        if ts > last_ts[pid]:
            last_ts[pid] = ts

    for p in persons:
        if p["id"] in counts:
            p["interactionCount"] = counts[p["id"]]
            p["lastContactedAt"] = last_ts[p["id"]]

# ── Main ───────────────────────────────────────────────────────────────────────

def main():
    print("Step 1/9: Creating 'me' person …")
    me = make_me()

    print("Step 2/9: Generating 22 contacts …")
    persons = generate_persons()
    all_persons = [me] + persons
    non_me = [p for p in all_persons if not p["isMe"]]
    print(f"  → {len(persons)} contacts created")

    print("Step 3/9: Generating contact details …")
    contact_details = generate_contact_details(non_me)
    print(f"  → {len(contact_details)} contact details")

    print("Step 4/9: Generating life events …")
    life_events = generate_life_events(non_me)
    print(f"  → {len(life_events)} life events")

    print("Step 5/9: Generating interactions …")
    interactions, i_participants = generate_interactions(non_me)
    print(f"  → {len(interactions)} interactions")

    print("Step 6/9: Generating notes …")
    notes = generate_notes(non_me)
    print(f"  → {len(notes)} notes")

    print("Step 7/9: Generating gifts …")
    gifts = generate_gifts(non_me)
    print(f"  → {len(gifts)} gifts")

    print("Step 8/9: Generating shared activities …")
    activities, a_participants = generate_activities(non_me)
    print(f"  → {len(activities)} activities")

    print("Step 9/9: Generating inter-contact relationships …")
    person_relationships = generate_relationships(non_me)
    print(f"  → {len(person_relationships)} relationships")

    update_person_stats(all_persons, interactions, i_participants)

    backup = {
        "version": DB_VERSION,
        "exportedAt": NOW_MS,
        "persons": all_persons,
        "personRelationships": person_relationships,
        "contactDetails": contact_details,
        "interactions": interactions,
        "interactionParticipants": i_participants,
        "notes": notes,
        "lifeEvents": life_events,
        "activities": activities,
        "activityParticipants": a_participants,
        "gifts": gifts,
        "tasks": [],
    }

    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    filename = f"wulfpak_seed_{ts}.json"
    with open(filename, "w", encoding="utf-8") as f:
        json.dump(backup, f, indent=2, ensure_ascii=False)

    print(f"\nDone! Output: {filename}")
    print(f"  Persons        : {len(all_persons)} (including you)")
    print(f"  Interactions   : {len(interactions)}")
    print(f"  Notes          : {len(notes)}")
    print(f"  Life events    : {len(life_events)}")
    print(f"  Contact details: {len(contact_details)}")
    print(f"  Gifts          : {len(gifts)}")
    print(f"  Activities     : {len(activities)}")
    print(f"  Relationships  : {len(person_relationships)}")
    print(f"\nImport via: Settings → Contacts → Import backup")

    return backup


if __name__ == "__main__":
    main()
