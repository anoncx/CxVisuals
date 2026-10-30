import datetime
import hashlib
import json
import pathlib
import re
import subprocess
import sys

JAR = re.compile(r"^cxvisuals-(?P<version>.+)\+(?P<minecraft>[0-9]+(?:\.[0-9]+){1,3})\.jar$")


def properties(path):
    values = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    return values


def notes():
    log = subprocess.run(
        ["git", "log", "-8", "--no-merges", "--format=%s"], capture_output=True, text=True, check=True
    ).stdout.splitlines()
    out = []
    for line in log:
        line = re.sub(r"^\[[^]]*\]\s*", "", line)
        line = re.sub(r"\s+-\s+[A-Za-z0-9 ]+$", "", line).strip()
        if line:
            out.append(line)
    return out[:5]


def main():
    libs = pathlib.Path(sys.argv[1])
    root = pathlib.Path(sys.argv[2]) if len(sys.argv) > 2 else pathlib.Path(".")
    published = datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds")
    changes = notes()
    made = 0
    for jar in sorted(libs.glob("cxvisuals-*.jar")):
        match = JAR.match(jar.name)
        if not match:
            continue
        minecraft = match.group("minecraft")
        versions = properties(root / "versions" / f"{minecraft}.properties")
        data = jar.read_bytes()
        manifest = {
            "name": "CxVisuals",
            "version": f"{match.group('version')}+{minecraft}",
            "minecraft": minecraft,
            "loader": versions["loader_version"],
            "file": jar.name,
            "size": len(data),
            "sha1": hashlib.sha1(data).hexdigest(),
            "sha256": hashlib.sha256(data).hexdigest(),
            "requires": [{"project": "fabric-api", "version": versions["fabric_api_version"]}],
            "notes": changes,
            "published": published,
        }
        (libs / f"manifest-{minecraft}.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
        made += 1
    if made == 0:
        sys.exit("no jars found in " + str(libs))
    print(f"wrote {made} manifest(s)")


main()
