#!/usr/bin/env python3
"""
Automated tests for firestore.rules, run against Google's local Firestore emulator.

    python3 tools/rules_test.py

Starts the emulator (tools/firestore-emulator/*.jar, needs Java 25+; Android Studio's bundled Java
is used if present) with ../firestore.rules, then plays different people against it over the REST
API: you, your partner, a stranger, an anonymous sign-in and nobody. Each test starts from a clean
database. Exits non-zero if any test fails. Standard library only.
"""
import base64
import glob
import json
import os
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RULES = os.environ.get("RULES_FILE", os.path.join(ROOT, "firestore.rules"))
PROJECT = "demo-gym"  # "demo-" projects never touch real Firebase
JAVA_CANDIDATES = [
    os.environ.get("JAVA25", ""),
    "/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java",
    "java",
]

# ---------------------------------------------------------------- emulator

def free_port():
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def start_emulator():
    jars = sorted(glob.glob(os.path.join(ROOT, "tools", "firestore-emulator", "cloud-firestore-emulator-*.jar")))
    if not jars:
        sys.exit("No emulator jar in tools/firestore-emulator (see README: Testing the security rules).")
    port = free_port()
    log = open(os.path.join(ROOT, "tools", "firestore-emulator", "emulator.log"), "w")
    for java in filter(None, JAVA_CANDIDATES):
        proc = subprocess.Popen(
            [java, "-jar", jars[-1], "--host=127.0.0.1", f"--port={port}", f"--rules={RULES}",
             f"--project_id={PROJECT}", "--single_project_mode=true"],
            stdout=log, stderr=subprocess.STDOUT,
        )
        deadline = time.time() + 60
        while time.time() < deadline and proc.poll() is None:
            try:
                urllib.request.urlopen(f"http://127.0.0.1:{port}/", timeout=1).read()
                return proc, port
            except Exception:
                time.sleep(0.3)
        proc.kill()
    sys.exit("Couldn't start the Firestore emulator (it needs Java 25 or newer; set JAVA25=/path/to/java).")


# ---------------------------------------------------------------- REST helpers

def token(uid, provider="google.com"):
    """An unsigned ID token; the emulator accepts these and the rules see request.auth."""
    def b64(d):
        return base64.urlsafe_b64encode(json.dumps(d).encode()).decode().rstrip("=")
    now = int(time.time())
    claims = {
        "iss": f"https://securetoken.google.com/{PROJECT}", "aud": PROJECT, "iat": now, "exp": now + 3600,
        "auth_time": now, "sub": uid, "user_id": uid, "firebase": {"sign_in_provider": provider, "identities": {}},
    }
    return b64({"alg": "none", "typ": "JWT"}) + "." + b64(claims) + "."


def enc(v):
    if v is None:
        return {"nullValue": None}
    if isinstance(v, bool):
        return {"booleanValue": v}
    if isinstance(v, int):
        return {"integerValue": str(v)}
    if isinstance(v, float):
        return {"doubleValue": v}
    if isinstance(v, str):
        return {"stringValue": v}
    if isinstance(v, list):
        return {"arrayValue": {"values": [enc(x) for x in v]}}
    if isinstance(v, dict):
        return {"mapValue": {"fields": {k: enc(x) for k, x in v.items()}}}
    raise TypeError(v)


class Client:
    """Talks to the emulator as one person (uid None = signed out, "owner" = admin, bypasses rules)."""

    def __init__(self, port, uid=None, provider="google.com"):
        self.base = f"http://127.0.0.1:{port}/v1/projects/{PROJECT}/databases/(default)/documents"
        self.auth = "owner" if uid == "owner" else (token(uid, provider) if uid else None)

    def _call(self, method, path, body=None, query=""):
        req = urllib.request.Request(
            f"{self.base}/{path}{query}", method=method,
            data=json.dumps(body).encode() if body is not None else None,
            headers={"Content-Type": "application/json", **({"Authorization": f"Bearer {self.auth}"} if self.auth else {})},
        )
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                return r.status
        except urllib.error.HTTPError as e:
            return e.code

    def get(self, path):
        return self._call("GET", path)

    def set(self, path, data):
        return self._call("PATCH", path, {"fields": {k: enc(v) for k, v in data.items()}})

    def update(self, path, data):
        """Only these fields, and only if the document exists (like updateFields in the app)."""
        mask = "".join(f"&updateMask.fieldPaths={k}" for k in data)
        return self._call("PATCH", path, {"fields": {k: enc(v) for k, v in data.items()}},
                          query="?currentDocument.exists=true" + mask)

    def delete(self, path):
        return self._call("DELETE", path)


# ---------------------------------------------------------------- tests

ME, PARTNER, STRANGER, NEWCOMER = "chris", "graciela", "stranger", "newcomer"
PID, CODE = "pair1", "ABC234"
OK, DENIED = 200, 403


def seed(admin, members=(ME, PARTNER)):
    admin.set(f"partnerships/{PID}", {
        "id": PID, "code": CODE, "members": list(members),
        "names": {m: m.title() for m in members}, "homeGymId": "home",
    })
    admin.set(f"invites/{CODE}", {"partnershipId": PID})
    admin.set(f"partnerships/{PID}/workouts/w1", {"id": "w1", "name": "Legs", "startedAt": 1})
    admin.set(f"partnerships/{PID}/workouts/w1/sets/s1", {"id": "s1", "uid": ME, "weight": 80.0})
    admin.set(f"users/{ME}", {"uid": ME, "name": "Chris", "partnershipId": PID})


TESTS = []


def test(fn):
    TESTS.append(fn)
    return fn


@test
def profiles_are_private(c):
    c.seed()
    c.expect(OK, c.me.get(f"users/{ME}"), "read own profile")
    c.expect(OK, c.me.set(f"users/{ME}", {"uid": ME, "name": "C"}), "write own profile")
    c.expect(DENIED, c.partner.get(f"users/{ME}"), "partner reads my profile")
    c.expect(DENIED, c.stranger.set(f"users/{ME}", {"uid": ME}), "stranger writes my profile")
    c.expect(DENIED, c.nobody.get(f"users/{ME}"), "signed-out read")


@test
def only_google_accounts(c):
    c.seed()
    anon = c.client(ME, provider="anonymous")
    c.expect(DENIED, anon.get(f"users/{ME}"), "anonymous sign-in reads own profile")
    c.expect(DENIED, anon.get(f"partnerships/{PID}"), "anonymous sign-in reads the pair")


@test
def invites_by_exact_code_only(c):
    c.seed()
    c.expect(OK, c.stranger.get(f"invites/{CODE}"), "look up a code")
    c.expect(DENIED, c.stranger.get("invites"), "list all codes")
    c.expect(DENIED, c.nobody.get(f"invites/{CODE}"), "signed-out look-up")


@test
def only_members_create_or_retire_codes(c):
    c.seed()
    c.expect(OK, c.me.set("invites/NEW234", {"partnershipId": PID}), "member creates a code")
    c.expect(DENIED, c.stranger.set("invites/BAD234", {"partnershipId": PID}), "stranger creates a code for our pair")
    c.expect(DENIED, c.stranger.delete(f"invites/{CODE}"), "stranger retires our code")
    c.expect(OK, c.partner.delete(f"invites/{CODE}"), "member retires the code")


@test
def creating_a_pair(c):
    c.seed()
    c.expect(OK, c.stranger.set("partnerships/p2", {"id": "p2", "members": [STRANGER], "names": {}}), "start own pair")
    c.expect(DENIED, c.stranger.set("partnerships/p3", {"id": "p3", "members": [STRANGER, ME], "names": {}}),
             "start a pair that includes someone else")


@test
def full_pair_is_private(c):
    c.seed()
    c.expect(OK, c.me.get(f"partnerships/{PID}"), "member reads pair")
    c.expect(OK, c.partner.get(f"partnerships/{PID}"), "partner reads pair")
    c.expect(DENIED, c.stranger.get(f"partnerships/{PID}"), "stranger reads full pair")
    c.expect(DENIED, c.stranger.update(f"partnerships/{PID}", {"members": [ME, PARTNER, STRANGER]}), "stranger joins full pair")


@test
def joining_a_pair_with_a_code(c):
    c.seed(members=(ME,))
    c.expect(OK, c.newcomer.get(f"partnerships/{PID}"), "newcomer reads a half-full pair")
    c.expect(DENIED, c.newcomer.update(f"partnerships/{PID}", {
        "members": [ME, NEWCOMER], "names": {ME: "Chris", NEWCOMER: "New"}, "code": "MINE23"}),
        "joining while changing the code")
    c.expect(DENIED, c.newcomer.update(f"partnerships/{PID}", {
        "members": [ME, NEWCOMER], "names": {ME: "Hacked", NEWCOMER: "New"}}), "joining while renaming the other person")
    c.expect(DENIED, c.newcomer.update(f"partnerships/{PID}", {
        "members": [ME, STRANGER], "names": {ME: "Me", STRANGER: "S"}}), "adding someone else")
    c.expect(DENIED, c.newcomer.update(f"partnerships/{PID}", {
        "members": [NEWCOMER], "names": {NEWCOMER: "New"}}), "replacing the existing member")
    c.expect(DENIED, c.newcomer.get(f"partnerships/{PID}/workouts/w1"), "reading workouts before joining")
    c.expect(OK, c.newcomer.update(f"partnerships/{PID}", {
        "members": [ME, NEWCOMER], "names": {ME: "Chris", NEWCOMER: "New"}}), "joining properly")
    c.expect(OK, c.newcomer.get(f"partnerships/{PID}/workouts/w1"), "reading workouts after joining")


@test
def members_share_everything_inside_the_pair(c):
    c.seed()
    for who, client in (("me", c.me), ("partner", c.partner)):
        c.expect(OK, client.get(f"partnerships/{PID}/workouts/w1"), f"{who} reads workout")
        c.expect(OK, client.get(f"partnerships/{PID}/workouts/w1/sets/s1"), f"{who} reads a set")
        c.expect(OK, client.set(f"partnerships/{PID}/workouts/w1/sets/s1", {"uid": ME, "weight": 85.0}),
                 f"{who} edits my set")
        c.expect(OK, client.set(f"partnerships/{PID}/plans/p", {"name": "Push"}), f"{who} saves a plan")
        c.expect(OK, client.set(f"partnerships/{PID}/recipes/r1", {"url": "https://x.com/r", "title": "Oats"}),
                 f"{who} saves a recipe")
        c.expect(OK, client.update(f"partnerships/{PID}", {"homeGymId": "gym2"}), f"{who} changes the home gym")


@test
def strangers_see_nothing_inside_the_pair(c):
    c.seed()
    for path in ("workouts/w1", "workouts/w1/sets/s1", "lastWeights/chris", "progress/x", "exercisePhotos/x", "recipes/r1"):
        c.expect(DENIED, c.stranger.get(f"partnerships/{PID}/{path}"), f"stranger reads {path}")
    c.expect(DENIED, c.stranger.set(f"partnerships/{PID}/workouts/w2", {"name": "x"}), "stranger adds a workout")
    c.expect(DENIED, c.stranger.set(f"partnerships/{PID}/recipes/r2", {"url": "https://evil.example"}), "stranger adds a recipe")
    c.expect(DENIED, c.stranger.delete(f"partnerships/{PID}/workouts/w1"), "stranger deletes a workout")
    c.expect(DENIED, c.nobody.get(f"partnerships/{PID}/workouts/w1"), "signed-out read")


@test
def leaving_removes_access(c):
    c.seed()
    c.expect(OK, c.partner.update(f"partnerships/{PID}", {"members": [ME], "names": {ME: "Chris"}, "code": "NEW234"}),
             "partner leaves")
    c.expect(DENIED, c.partner.get(f"partnerships/{PID}/workouts/w1"), "former partner reads workouts")
    c.expect(DENIED, c.partner.set(f"partnerships/{PID}/workouts/w1/sets/s1", {"weight": 1.0}), "former partner edits a set")
    c.expect(OK, c.me.get(f"partnerships/{PID}/workouts/w1"), "remaining member still reads")


# ---------------------------------------------------------------- runner

class Case:
    def __init__(self, port):
        self.port = port
        self.failures = []
        self.admin = Client(port, "owner")
        self.me, self.partner = Client(port, ME), Client(port, PARTNER)
        self.stranger, self.newcomer = Client(port, STRANGER), Client(port, NEWCOMER)
        self.nobody = Client(port)

    def client(self, uid, provider="google.com"):
        return Client(self.port, uid, provider)

    def seed(self, members=(ME, PARTNER)):
        seed(self.admin, members)

    def expect(self, want, got, what):
        if (want == OK) != (got == OK):
            self.failures.append(f"{what}: expected {'allowed' if want == OK else 'denied'}, got HTTP {got}")


def main():
    proc, port = start_emulator()
    failed = 0
    try:
        for fn in TESTS:
            # Fresh database for each test.
            urllib.request.urlopen(urllib.request.Request(
                f"http://127.0.0.1:{port}/emulator/v1/projects/{PROJECT}/databases/(default)/documents", method="DELETE"))
            case = Case(port)
            fn(case)
            if case.failures:
                failed += 1
                print(f"FAIL {fn.__name__}")
                for f in case.failures:
                    print(f"     {f}")
            else:
                print(f"ok   {fn.__name__}")
    finally:
        proc.terminate()
        proc.wait(timeout=10)
    print(f"\n{len(TESTS) - failed} of {len(TESTS)} passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
