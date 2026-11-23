import json, re, sys
data_path, catalog_path, out_path = sys.argv[1:4]
d = {e["id"]: e for e in json.load(open(data_path))}
ids = re.findall(r'ex\("[^"]+", Equipment\.\w+, "([^"]+)"\)', open(catalog_path).read())
missing = [i for i in ids if i not in d]
if missing:
    sys.exit("missing ids: %s" % missing)
def kt(s):
    s = " ".join(s.split())
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$") + '"'
lines = [
    "package com.gym.app",
    "",
    "// GENERATED from free-exercise-db (github.com/yuhonas/free-exercise-db, The Unlicense)",
    "// for the image ids used in Catalog.kt. Regenerate rather than hand-editing.",
    "internal object ExerciseInstructions {",
    "    val byId: Map<String, List<String>> = mapOf(",
]
for i in dict.fromkeys(ids):
    steps = ", ".join(kt(s) for s in d[i].get("instructions", []) if s.strip())
    lines.append(f'        {kt(i)} to listOf({steps}),')
lines += ["    )", ""]
no_photos = [i for i in dict.fromkeys(ids) if not d[i].get("images")]
lines.append("    /** Image ids that have instructions but no photos in the dataset. */")
lines.append("    val noPhotos: Set<String> = setOf(" + ", ".join(kt(i) for i in no_photos) + ")")
lines += ["}", ""]
open(out_path, "w").write("\n".join(lines))
print(len(ids), "exercises with images;", sum(1 for i in ids if d[i].get("instructions")), "with instructions")
