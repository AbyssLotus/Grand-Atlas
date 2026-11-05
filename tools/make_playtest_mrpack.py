"""Build a small .mrpack for playtesting Grand Atlas, importable into the Modrinth App.

    python tools/make_playtest_mrpack.py        (after gradlew build)

Minecraft 26.3 + NeoForge, Grand Atlas from build/libs (in overrides/), and a handful of
release-only helpers fetched from Modrinth's API (URLs and hashes, no downloads here):
Distant Horizons to see the terrain from far off, Xaero's maps to find macro landforms, and
light performance mods. Betas are skipped on purpose, so a
first launch isn't a crash test of somebody else's mod.
"""
import json
import pathlib
import sys
import urllib.parse
import urllib.request
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
MC = "26.3"
LOADER = "neoforge"

PROPS = dict(line.split("=", 1) for line in (ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines()
             if "=" in line and not line.startswith("#"))
NEOFORGE = PROPS["neo_version"].strip()
VERSION = PROPS["mod_version"].strip()

HELPERS = [
    "distanthorizons",   # see 700-block terrain from far away
    "xaeros-world-map",  # spot macro outlines, read coordinates
    "xaeros-minimap",
    # Not Chunky: on 26.3, Distant Horizons 3.3.4's Chunky hook calls Chunky during world
    # creation (spawn search) before Chunky has started, and the game crashes with
    # "Chunky is not loaded". DH fills in distant terrain without it.
    "lithium", "ferrite-core", "immediatelyfast", "entityculling",
]


def latest_release(slug):
    query = urllib.parse.urlencode({"loaders": json.dumps([LOADER]), "game_versions": json.dumps([MC])})
    req = urllib.request.Request(f"https://api.modrinth.com/v2/project/{slug}/version?{query}",
                                 headers={"User-Agent": "grand-atlas-playtest-pack (local build script)"})
    versions = json.load(urllib.request.urlopen(req, timeout=30))
    releases = [v for v in versions if v["version_type"] == "release"]
    if not releases:
        sys.exit(f"{slug}: no release build for {LOADER} {MC}")
    v = releases[0]
    f = next((x for x in v["files"] if x["primary"]), v["files"][0])
    return v["version_number"], {
        "path": f"mods/{f['filename']}",
        "hashes": {"sha1": f["hashes"]["sha1"], "sha512": f["hashes"]["sha512"]},
        "downloads": [f["url"]],
        "fileSize": f["size"],
    }


def main():
    jars = sorted((ROOT / "build" / "libs").glob("grandatlas-*.jar"), key=lambda p: p.stat().st_mtime)
    if not jars:
        sys.exit("no build/libs/grandatlas-*.jar; run gradlew build first")
    mod_jar = jars[-1]

    files = []
    for slug in HELPERS:
        version, entry = latest_release(slug)
        print(f"  {slug:18} {version}")
        files.append(entry)

    index = {
        "formatVersion": 1,
        "game": "minecraft",
        "versionId": VERSION,
        "name": "Grand Atlas Playtest",
        "summary": "Grand Atlas terrain on Minecraft 26.3, with Distant Horizons and maps for exploring it.",
        "files": files,
        "dependencies": {"minecraft": MC, "neoforge": NEOFORGE},
    }
    out = ROOT / "dist" / f"grand-atlas-playtest-{VERSION}.mrpack"
    out.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("modrinth.index.json", json.dumps(index, indent=2))
        z.writestr(f"overrides/mods/{mod_jar.name}", mod_jar.read_bytes())
    print(f"{out}  ({out.stat().st_size / 1e3:.0f} kB, {len(files)} downloads + {mod_jar.name})")


if __name__ == "__main__":
    main()
