# Gym MCP server

Read-only questions about the Gym app's data (Firestore) from Claude: workouts, one exercise's
history, training balance per muscle group, plans.

Tools: `gym_overview`, `list_workouts`, `get_workout`, `exercise_history`, `training_balance`.

## Set-up

1. A read-only key: Google Cloud console → project `gym-cdgmp` → IAM & Admin → Service accounts →
   Create service account (e.g. `gym-reader`) with the role **Cloud Datastore Viewer** → its Keys tab →
   Add key → JSON. Save it as `~/.config/gym-mcp/service-account.json` (never in this repo).
2. Python environment (macOS's own Python 3.9 is enough):
   ```bash
   /usr/bin/python3 -m venv tools/gym-mcp/.venv
   tools/gym-mcp/.venv/bin/pip install -r tools/gym-mcp/requirements.txt
   ```
3. Claude Code: user-level server `gym` in `~/.claude.json` (`mcpServers`), command
   `tools/gym-mcp/.venv/bin/python tools/gym-mcp/server.py` (absolute paths). New sessions pick it up.

Try a tool without Claude:

```bash
tools/gym-mcp/.venv/bin/python tools/gym-mcp/server.py --try training_balance '{"person":"Chris"}'
```

Settings: `GYM_MCP_KEY` (key path), `GYM_PARTNERSHIP` (if there's more than one pair),
`GYM_MCP_TZ` (default Europe/Zurich).
