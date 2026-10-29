#!/usr/bin/env python3
"""Find the newest Fabric toolchain for a Minecraft version.

Prints key=value lines on stdout, including `gradle_args` (the -P overrides
that would build against these versions instead of gradle.properties), and a
readable summary on stderr. Exits non-zero, explaining why, when the version
is not supported. CI runs it to report available upgrades.

Usage: resolve_fabric_versions.py <minecraft_version>
"""
import json
import re
import sys
import urllib.request
import xml.etree.ElementTree as ET

FABRIC_MAVEN = "https://maven.fabricmc.net"
FABRIC_META = "https://meta.fabricmc.net/v2"
# Minecraft 26.1+ is unobfuscated; Loom's non-remapping plugin builds against it.
LOOM_PLUGIN_ID = "net.fabricmc.fabric-loom"


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "zendrahub-evolve-ci"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return resp.read().decode("utf-8")


def maven_versions(group, artifact):
    path = f"{FABRIC_MAVEN}/{group.replace('.', '/')}/{artifact}/maven-metadata.xml"
    root = ET.fromstring(fetch(path))
    return [v.text for v in root.iter("version") if v.text]


def version_key(v):
    """Numeric-aware sort key: 1.15.10 sorts after 1.15.9."""
    return [int(p) if p.isdigit() else p for p in re.split(r"[.+\-]", v)]


def fail(message):
    print(f"error: {message}", file=sys.stderr)
    sys.exit(1)


def main():
    if len(sys.argv) != 2:
        fail("usage: resolve_fabric_versions.py <minecraft_version>")
    mc = sys.argv[1].strip()

    loaders = json.loads(fetch(f"{FABRIC_META}/versions/loader/{mc}"))
    if not loaders:
        games = [g["version"] for g in json.loads(fetch(f"{FABRIC_META}/versions/game"))[:15]]
        fail(f"Fabric Loader lists no builds for Minecraft {mc}. Recent game versions: {', '.join(games)}")
    stable = [entry["loader"]["version"] for entry in loaders if entry["loader"].get("stable")]
    loader = stable[0] if stable else loaders[0]["loader"]["version"]

    api_all = maven_versions("net.fabricmc.fabric-api", "fabric-api")
    api = sorted((v for v in api_all if v.endswith("+" + mc)), key=version_key)
    if not api:
        recent = sorted(api_all, key=version_key)[-8:]
        fail(f"no Fabric API build for Minecraft {mc}. Newest Fabric API builds: {', '.join(recent)}")
    fabric_api = api[-1]

    loom_all = maven_versions(LOOM_PLUGIN_ID, f"{LOOM_PLUGIN_ID}.gradle.plugin")
    loom_releases = sorted((v for v in loom_all if "SNAPSHOT" not in v), key=version_key)
    if not loom_releases:
        fail(f"no releases of the Gradle plugin {LOOM_PLUGIN_ID} on {FABRIC_MAVEN}")
    loom = loom_releases[-1]

    print(f"Minecraft {mc}: Fabric Loader {loader} (stable builds: {len(stable)}), "
          f"Fabric API {fabric_api}, Loom ({LOOM_PLUGIN_ID}) {loom}", file=sys.stderr)
    print(f"loader={loader}")
    print(f"fabric_api={fabric_api}")
    print(f"loom={loom}")
    print(f"gradle_args=-Ploader_version={loader} -Pfabric_api_version={fabric_api} -Ploom_version={loom}")


if __name__ == "__main__":
    main()
