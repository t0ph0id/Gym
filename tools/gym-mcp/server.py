#!/usr/bin/env python3
"""
Gym MCP server: read-only questions about your Gym app data (Firestore), for Claude.

Speaks MCP over stdio (newline-delimited JSON-RPC). Signs in with a Google service account key
(ideally one with only the "Cloud Datastore Viewer" role), at $GYM_MCP_KEY or
~/.config/gym-mcp/service-account.json. Nothing here writes to Firestore.

Muscle groups come from the app's own catalogue (composeApp/.../Catalog.kt), read at start-up, plus
your custom exercises from Firestore.
"""
import json
import os
import re
import sys
import traceback
import warnings
from collections import defaultdict
from datetime import datetime, timedelta, timezone
from pathlib import Path

try:
    from zoneinfo import ZoneInfo
except ImportError:  # pragma: no cover
    ZoneInfo = None

# Google's libraries warn about Python 3.9 (macOS's own); harmless here, and noisy in logs.
warnings.filterwarnings("ignore", category=FutureWarning)
warnings.filterwarnings("ignore", message=".*OpenSSL.*")

KEY_PATH = Path(os.environ.get("GYM_MCP_KEY", "~/.config/gym-mcp/service-account.json")).expanduser()
TZ = ZoneInfo(os.environ.get("GYM_MCP_TZ", "Europe/Zurich")) if ZoneInfo else timezone.utc
REPO = Path(__file__).resolve().parents[2]
CATALOG_KT = REPO / "composeApp/src/commonMain/kotlin/com/gym/app/Catalog.kt"
MOBILITY_KT = REPO / "composeApp/src/commonMain/kotlin/com/gym/app/MobilityData.kt"
PROTOCOL = "2025-06-18"

SETUP_HELP = (
    f"No service account key at {KEY_PATH}. Create a read-only one: Google Cloud console → project "
    "gym-cdgmp → IAM & Admin → Service accounts → Create (role: Cloud Datastore Viewer) → Keys → "
    f"Add key → JSON, and save it as {KEY_PATH}."
)

MUSCLES = ["Chest", "Back", "Shoulders", "Biceps", "Triceps", "Legs", "Glutes", "Core", "FullBody", "Other"]
MUSCLE_LABEL = {"FullBody": "Full body & cardio"}


# ---------------------------------------------------------------- catalogue (muscle per exercise)

def key(name):
    return (name or "").strip().lower()


def load_catalog():
    """exercise key -> (muscle, equipment, warm-up kind or None), from the app's Kotlin sources."""
    out = {}
    try:
        muscle = None
        for line in CATALOG_KT.read_text().splitlines():
            g = re.search(r"group\(Muscle\.(\w+)\)", line)
            if g:
                muscle = g.group(1)
            e = re.search(r'\bex\("([^"]+)",\s*Equipment\.(\w+)', line)
            if e and muscle:
                out[key(e.group(1))] = (muscle, e.group(2), None)
        for m in re.finditer(
            r'CatalogExercise\("([^"]+)",\s*Muscle\.(\w+),\s*Equipment\.(\w+)(?:[^)]*?kind\s*=\s*WarmUpKind\.(\w+))?[^)]*\)',
            MOBILITY_KT.read_text(),
        ):
            k = key(m.group(1))
            muscle_, equipment_, _ = out.get(k, (m.group(2), m.group(3), None))
            out[k] = (muscle_, equipment_, m.group(4))
    except OSError:
        pass
    return out


CATALOG = load_catalog()


# ---------------------------------------------------------------- Firestore

_db = None


def db():
    global _db
    if _db is None:
        if not KEY_PATH.exists():
            raise UserError(SETUP_HELP)
        from google.cloud import firestore  # imported late so start-up is fast and errors are clear
        _db = firestore.Client.from_service_account_json(str(KEY_PATH))
    return _db


class UserError(Exception):
    pass


_pair = None


def pair():
    """(partnership id, its data). $GYM_PARTNERSHIP picks one; otherwise the only (or busiest) pair."""
    global _pair
    if _pair is None:
        wanted = os.environ.get("GYM_PARTNERSHIP")
        docs = [(d.id, d.to_dict() or {}) for d in db().collection("partnerships").stream()]
        if wanted:
            docs = [d for d in docs if d[0] == wanted]
        if not docs:
            raise UserError("No partnership found in Firestore (or $GYM_PARTNERSHIP doesn't match one).")
        docs.sort(key=lambda d: len(d[1].get("members", [])), reverse=True)
        _pair = docs[0]
    return _pair


def coll(name):
    pid, _ = pair()
    return [(d.id, d.to_dict() or {}) for d in db().collection(f"partnerships/{pid}/{name}").stream()]


def names():
    _, p = pair()
    return p.get("names", {}) or {}


def person_uid(person):
    """A uid from a name ("Chris"), case-insensitive; None for no filter."""
    if not person:
        return None
    for uid, n in names().items():
        if key(n) == key(person) or key(n).split(" ")[0] == key(person):
            return uid
    raise UserError(f"No one called {person!r}. People: {', '.join(names().values())}.")


def name_of(uid):
    return names().get(uid, uid)


def gyms():
    return {gid: g.get("name", gid) for gid, g in coll("gyms")}


def custom_muscles():
    return {key(c.get("name")): c.get("muscle", "Other") for _, c in coll("exercises")}


def muscle_of(exercise, customs):
    k = key(exercise)
    if k in CATALOG:
        return CATALOG[k][0]
    return customs.get(k, "Other")


def day(ms):
    return datetime.fromtimestamp(ms / 1000, TZ).strftime("%Y-%m-%d") if ms else None


def stamp(ms):
    return datetime.fromtimestamp(ms / 1000, TZ).strftime("%Y-%m-%d %H:%M") if ms else None


def parse_day(s, end=False):
    if not s:
        return None
    d = datetime.strptime(s, "%Y-%m-%d").replace(tzinfo=TZ)
    if end:
        d += timedelta(days=1)
    return int(d.timestamp() * 1000)


def includes(w, uid):
    people = w.get("people") or []
    return not people or uid in people


def circuit(c):
    c = c or {}
    mins = c.get("minutes", {}) or {}
    health = c.get("health", {}) or {}
    out = []
    for ex in c.get("exercises", []) or []:
        item = {"move": ex}
        if key(ex) in mins:
            item["minutes"] = mins[key(ex)]
        h = health.get(key(ex))
        if h:
            item["watch"] = {k: v for k, v in h.items() if k in ("kcal", "avgBpm", "maxBpm") and v is not None}
        out.append(item)
    if out and (c.get("rounds") or 1) > 1:
        return {"rounds": c.get("rounds"), "moves": out}
    return out


def finished_workouts():
    ws = [(wid, w) for wid, w in coll("workouts") if w.get("finishedAt")]
    ws.sort(key=lambda x: x[1].get("startedAt", 0), reverse=True)
    return ws


def workout_summary(wid, w, gym_names):
    people = w.get("people") or list(names().keys())
    return {
        "id": wid,
        "date": stamp(w.get("startedAt")),
        "name": w.get("name"),
        "gym": gym_names.get(w.get("gymId") or "home", w.get("gymId") or "home"),
        "minutes": round((w.get("finishedAt", 0) - w.get("startedAt", 0)) / 60000),
        "people": [name_of(u) for u in people],
        "exercises": [e.get("name") for e in w.get("exercises", [])],
        "warm_up": circuit(w.get("warmUp")),
        "cool_down": circuit(w.get("mobility")),
        "watch": {name_of(u): {k: v for k, v in h.items() if k in ("kcal", "avgBpm", "maxBpm") and v is not None}
                  for u, h in (w.get("health") or {}).items()},
        "personal_bests": {name_of(u): ex for u, ex in (w.get("prs") or {}).items() if ex},
    }


# ---------------------------------------------------------------- tools

def t_overview(args):
    pid, p = pair()
    ws = finished_workouts()
    plans = coll("plans")
    return {
        "people": list(names().values()),
        "home_gym": gyms().get(p.get("homeGymId", "home")),
        "gyms": [{"name": g.get("name"), "brands": g.get("brands", [])} for _, g in coll("gyms")],
        "default_sets_reps": f"{p.get('defaultSets', 3)} x {p.get('defaultReps', 10)}",
        "finished_workouts": len(ws),
        "first_workout": stamp(ws[-1][1].get("startedAt")) if ws else None,
        "last_workout": stamp(ws[0][1].get("startedAt")) if ws else None,
        "plans": [
            {
                "name": pl.get("name"),
                "exercises": [f"{e.get('name')} {e.get('sets')}x{e.get('reps')}" for e in pl.get("exercises", [])],
                "warm_up": (pl.get("warmUp") or {}).get("exercises", []),
                "cool_down": (pl.get("mobility") or {}).get("exercises", []),
            }
            for _, pl in sorted(plans, key=lambda x: x[1].get("name", ""))
        ],
        "custom_exercises": [f"{c.get('name')} ({c.get('muscle')}, {c.get('equipment')})" for _, c in coll("exercises")],
    }


def t_list_workouts(args):
    uid = person_uid(args.get("person"))
    since, until = parse_day(args.get("since")), parse_day(args.get("until"), end=True)
    limit = int(args.get("limit") or 30)
    g = gyms()
    out = []
    for wid, w in finished_workouts():
        s = w.get("startedAt", 0)
        if (since and s < since) or (until and s >= until) or (uid and not includes(w, uid)):
            continue
        out.append(workout_summary(wid, w, g))
        if len(out) >= limit:
            break
    return {"count": len(out), "workouts": out}


def t_get_workout(args):
    wid = args.get("id")
    if not wid:
        raise UserError("Give a workout id (from list_workouts).")
    pid, _ = pair()
    doc = db().document(f"partnerships/{pid}/workouts/{wid}").get()
    if not doc.exists:
        raise UserError(f"No workout {wid!r}.")
    w = doc.to_dict()
    out = workout_summary(wid, w, gyms())
    sets = [s.to_dict() for s in db().collection(f"partnerships/{pid}/workouts/{wid}/sets").stream()]
    by_ex = defaultdict(lambda: defaultdict(list))
    for s in sorted(sets, key=lambda s: s.get("order", 0)):
        wt, reps = s.get("weight"), s.get("reps")
        label = f"{wt:g}x{reps}" if wt is not None and reps is not None else (f"{wt:g} kg" if wt is not None else f"{reps} reps")
        by_ex[s.get("exerciseId")][name_of(s.get("uid"))].append(label + ("" if s.get("done") else " (not ticked)"))
    exercises = []
    for e in w.get("exercises", []):
        item = {"name": e.get("name"), "target": f"{e.get('targetSets')}x{e.get('targetReps')}"}
        if e.get("brand"):
            item["brand"] = e["brand"]
        if e.get("isometric"):
            item["isometric"] = True
        if e.get("gymId"):
            item["gym"] = gyms().get(e["gymId"], e["gymId"])
        item["sets"] = dict(by_ex.get(e.get("id"), {}))
        exercises.append(item)
    out["exercises"] = exercises
    return out


def t_exercise_history(args):
    q = key(args.get("exercise"))
    if not q:
        raise UserError("Give an exercise name (or part of one).")
    uid = person_uid(args.get("person"))
    since = parse_day(args.get("since"))
    g = gyms()
    points = [p for _, p in coll("progress") if q in key(p.get("exercise"))]
    points = [p for p in points if (not uid or p.get("uid") == uid) and (not since or p.get("at", 0) >= since)]
    points.sort(key=lambda p: p.get("at", 0))
    if not points:
        raise UserError(f"No logged sets for an exercise matching {args.get('exercise')!r}.")
    return {
        "matches": sorted({p.get("exercise") for p in points}),
        "history": [
            {
                "date": day(p.get("at")),
                "person": name_of(p.get("uid")),
                "exercise": p.get("exercise"),
                "setup": " · ".join(x for x in [g.get(p.get("gymId"), p.get("gymId")), p.get("brand"),
                                                  "isometric" if p.get("isometric") else None] if x),
                "top": f"{p['topWeight']:g}x{p.get('topReps')}" if p.get("topWeight") is not None else f"{p.get('topReps')} reps",
                "sets": p.get("sets"),
                "volume_kg": round(p.get("volume", 0)),
            }
            for p in points
        ],
    }


def t_training_balance(args):
    days = int(args.get("days") or 56)
    uid = person_uid(args.get("person"))
    now = datetime.now(TZ)
    since = int((now - timedelta(days=days)).timestamp() * 1000)
    customs = custom_muscles()
    all_points = [p for _, p in coll("progress") if not uid or p.get("uid") == uid]
    stats = {m: {"sets": 0, "sessions": set(), "volume_kg": 0.0, "exercises": defaultdict(int)} for m in MUSCLES}
    last = {}
    for p in all_points:
        m = muscle_of(p.get("exercise"), customs)
        at = p.get("at", 0)
        last[m] = max(last.get(m, 0), at)
        if at >= since:
            s = stats[m]
            s["sets"] += p.get("sets", 0)
            s["sessions"].add(p.get("workoutId"))
            s["volume_kg"] += p.get("volume", 0)
            s["exercises"][p.get("exercise")] += p.get("sets", 0)
    weeks = max(days / 7, 1)
    muscles = []
    for m in MUSCLES:
        s = stats[m]
        if m == "Other" and not s["sets"]:
            continue
        muscles.append({
            "muscle": MUSCLE_LABEL.get(m, m),
            "sets": s["sets"],
            "sets_per_week": round(s["sets"] / weeks, 1),
            "workouts": len(s["sessions"]),
            "last_trained": day(last.get(m)),
            "days_since": (now - datetime.fromtimestamp(last[m] / 1000, TZ)).days if m in last else None,
            "exercises": dict(sorted(s["exercises"].items(), key=lambda x: -x[1])),
        })
    # Cardio minutes and workout count from the workouts themselves.
    cardio = defaultdict(int)
    sessions = 0
    for _, w in finished_workouts():
        if w.get("startedAt", 0) < since or (uid and not includes(w, uid)):
            continue
        sessions += 1
        for part in ("warmUp", "mobility"):
            c = w.get(part) or {}
            for ex, mins in (c.get("minutes") or {}).items():
                cardio[ex] += mins * (c.get("rounds") or 1)
    return {
        "person": name_of(uid) if uid else "both",
        "period": f"last {days} days ({day(since)} to {now.strftime('%Y-%m-%d')})",
        "workouts": sessions,
        "workouts_per_week": round(sessions / weeks, 1),
        "muscles": muscles,
        "cardio_minutes": dict(cardio),
        "note": "Sets are ticked sets only. Muscle is each exercise's main group in the app's catalogue.",
    }


PERSON = {"type": "string", "description": "A first name, e.g. \"Chris\". Leave out for both people."}
TOOLS = [
    ("gym_overview", "People, gyms, plans (with their exercises), custom exercises and how many workouts are logged.",
     {}, t_overview),
    ("list_workouts", "Finished workouts, newest first: date, plan name, gym, length, exercises, warm-up/cool-down "
     "cardio minutes, watch calories/heart rate and personal bests. No individual sets (see get_workout).",
     {"since": {"type": "string", "description": "YYYY-MM-DD"}, "until": {"type": "string", "description": "YYYY-MM-DD"},
      "person": PERSON, "limit": {"type": "integer", "description": "Default 30"}}, t_list_workouts),
    ("get_workout", "One workout in full, with every set (weight x reps) per person.",
     {"id": {"type": "string", "description": "Workout id from list_workouts"}}, t_get_workout),
    ("exercise_history", "Progress of one exercise over time: top set, sets and volume per workout, per setup "
     "(gym, machine brand). Name matching is case-insensitive and partial.",
     {"exercise": {"type": "string"}, "person": PERSON, "since": {"type": "string", "description": "YYYY-MM-DD"}},
     t_exercise_history),
    ("training_balance", "Sets per muscle group over a period (default the last 56 days), with when each muscle was "
     "last trained, the exercises used, workouts per week and cardio minutes. Use it to spot neglected areas.",
     {"days": {"type": "integer", "description": "Default 56"}, "person": PERSON}, t_training_balance),
]


# ---------------------------------------------------------------- MCP over stdio

def tool_list():
    return [
        {"name": n, "description": d, "inputSchema": {"type": "object", "properties": props},
         "annotations": {"readOnlyHint": True}}
        for n, d, props, _ in TOOLS
    ]


def call(name, args):
    fn = next((f for n, _, _, f in TOOLS if n == name), None)
    if not fn:
        return {"content": [{"type": "text", "text": f"Unknown tool {name}"}], "isError": True}
    try:
        result = fn(args or {})
        return {"content": [{"type": "text", "text": json.dumps(result, ensure_ascii=False, indent=1)}]}
    except UserError as e:
        return {"content": [{"type": "text", "text": str(e)}], "isError": True}
    except Exception as e:  # noqa: BLE001 - reported to the model, details to stderr
        traceback.print_exc(file=sys.stderr)
        return {"content": [{"type": "text", "text": f"{type(e).__name__}: {e}"}], "isError": True}


def handle(msg):
    method, mid = msg.get("method"), msg.get("id")
    if method == "initialize":
        version = (msg.get("params") or {}).get("protocolVersion") or PROTOCOL
        result = {"protocolVersion": version, "capabilities": {"tools": {}},
                  "serverInfo": {"name": "gym", "version": "1.0.0"},
                  "instructions": "Read-only access to the Gym app's workouts. Weights are kg."}
    elif method == "tools/list":
        result = {"tools": tool_list()}
    elif method == "tools/call":
        p = msg.get("params") or {}
        result = call(p.get("name"), p.get("arguments"))
    elif method == "ping":
        result = {}
    elif mid is None:
        return None  # a notification
    else:
        return {"jsonrpc": "2.0", "id": mid, "error": {"code": -32601, "message": f"Method not found: {method}"}}
    return None if mid is None else {"jsonrpc": "2.0", "id": mid, "result": result}


def main():
    if len(sys.argv) > 1 and sys.argv[1] == "--try":
        # Local check without Claude: server.py --try training_balance '{"person":"Chris"}'
        print(call(sys.argv[2], json.loads(sys.argv[3]) if len(sys.argv) > 3 else {})["content"][0]["text"])
        return
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            reply = handle(json.loads(line))
        except Exception as e:  # noqa: BLE001
            reply = {"jsonrpc": "2.0", "id": None, "error": {"code": -32700, "message": str(e)}}
        if reply is not None:
            sys.stdout.write(json.dumps(reply) + "\n")
            sys.stdout.flush()


if __name__ == "__main__":
    main()
