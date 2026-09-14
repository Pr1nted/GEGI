#!/usr/bin/env python3
"""What the auto-porter needs to know about Minecraft versions and their loaders.

  versions.py releases                 every release that is the last patch of its minor, oldest first
  versions.py loaders <mc>             the loader and tool versions a build of <mc> should use, as JSON
  versions.py pending <versions dir>   releases newer than the newest ported version, with loader readiness
  versions.py tools                    the newest Gradle, Fabric Loom and ModDevGradle, as JSON

Only public metadata: Mojang's version manifest, Fabric meta, the NeoForged and Forge
mavens, Modrinth (Mod Menu) and PaperMC's fill API (Folia/Paper). Standard library only.
"""
import json
import re
import sys
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

UA = {"User-Agent": "OpenArcade-autoport (github.com/Pr1nted/Open-Arcade)"}


def get(url, parse="json"):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30) as r:
        body = r.read()
    if parse == "json":
        return json.loads(body)
    if parse == "xml":
        return ET.fromstring(body)
    return body.decode()


def key(v):
    """1.21.11 < 26.1 < 26.1.2 < 26.2, numerically."""
    return [int(p) for p in re.findall(r"\d+", v)]


def minor(v):
    m = re.match(r"^(\d+\.\d+)", v)
    return m.group(1)


def manifest():
    return get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")


def releases():
    """The last patch of every minor from 1.12 on, oldest first."""
    last = {}
    for v in manifest()["versions"]:
        if v["type"] != "release" or key(v["id"]) < [1, 12]:
            continue
        last.setdefault(minor(v["id"]), v["id"])  # the manifest lists newest first
    return sorted(last.values(), key=key)


def java_for(mc):
    v = next(x for x in manifest()["versions"] if x["id"] == mc)
    return get(v["url"]).get("javaVersion", {}).get("majorVersion", 8)


def maven_versions(url):
    root = get(url, "xml")
    return [e.text for e in root.iter("version")]


def fabric(mc):
    games = {g["version"] for g in get("https://meta.fabricmc.net/v2/versions/game")}
    if mc not in games:
        return None
    loader = next(l["version"] for l in get("https://meta.fabricmc.net/v2/versions/loader") if l["stable"])
    return loader


def neoforge(mc):
    # NeoForge numbers its builds after the game: 21.11.x is 1.21.11, 26.2.0.x is 26.2.
    parts = key(mc)
    prefix = (f"{parts[1]}.{parts[2] if len(parts) > 2 else 0}." if parts[0] == 1
              else f"{parts[0]}.{parts[1]}.{parts[2] if len(parts) > 2 else 0}.")
    found = [v for v in maven_versions("https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml")
             if v.startswith(prefix)]
    stable = [v for v in found if "beta" not in v and "alpha" not in v]
    pick = sorted(stable or found, key=key)
    return pick[-1] if pick else None


def neo_form(mc):
    # "26.2-1", not "26.2-snapshot-8-1" or "26.2-pre-3-1", which sort higher by their numbers.
    found = [v for v in maven_versions("https://maven.neoforged.net/releases/net/neoforged/neoform/maven-metadata.xml")
             if v.startswith(mc + "-") and not re.search(r"snapshot|pre|rc", v[len(mc):])]
    return sorted(found, key=key)[-1] if found else None


def forge(mc):
    promos = get("https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json")["promos"]
    # The newest build, not the older "recommended" one: CI runs the real game on it before a port merges.
    return promos.get(f"{mc}-latest") or promos.get(f"{mc}-recommended")


def modmenu(mc):
    q = urllib.parse.urlencode({"game_versions": json.dumps([mc]), "loaders": json.dumps(["fabric"])})
    found = get(f"https://api.modrinth.com/v2/project/modmenu/version?{q}")
    releases_ = [v for v in found if v["version_type"] == "release"] or found
    return releases_[0]["version_number"] if releases_ else None


def paper_api(mc):
    """The paper-api artifact the Folia plugin compiles against: "26.2.build.123-stable"."""
    found = [v for v in maven_versions("https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/maven-metadata.xml")
             if v.startswith(mc + ".build.")]
    stable = [v for v in found if v.endswith("-stable")]
    pick = sorted(stable or found, key=lambda v: int(re.search(r"\.build\.(\d+)", v).group(1)))
    if pick:
        return pick[-1]
    # Before 26.1, Paper published one rolling API per game version.
    legacy = f"{mc}-R0.1-SNAPSHOT"
    all_versions = maven_versions("https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/maven-metadata.xml")
    return legacy if legacy in all_versions else None


def loaders(mc):
    forge_version = forge(mc)
    return {
        "minecraft_version": mc,
        "java_version": java_for(mc),
        "fabric_loader_version": fabric(mc),
        "modmenu_version": modmenu(mc),
        "neoforge_version": neoforge(mc),
        "neo_form_version": neo_form(mc),
        "forge_version": forge_version,
        "forge_loader_version_range": f"[{forge_version.split('.')[0]},)" if forge_version else None,
        "paper_api_version": paper_api(mc),
    }


def tools():
    """What a port moves its build to first: a new Minecraft usually needs the newest of these."""
    gradle = get("https://services.gradle.org/versions/current")["version"]
    loom = [v for v in maven_versions("https://maven.fabricmc.net/net/fabricmc/fabric-loom/net.fabricmc.fabric-loom.gradle.plugin/maven-metadata.xml")
            if re.fullmatch(r"\d+\.\d+\.\d+", v)]
    mdg = [v for v in maven_versions("https://plugins.gradle.org/m2/net/neoforged/moddev/net.neoforged.moddev.gradle.plugin/maven-metadata.xml")
           if re.fullmatch(r"\d+\.\d+\.\d+", v)]
    return {
        "gradle": gradle,
        "fabric_loom": sorted(loom, key=key)[-1] if loom else None,
        "moddev": sorted(mdg, key=key)[-1] if mdg else None,
    }


def pending(versions_dir):
    import os
    ported = [d for d in os.listdir(versions_dir) if re.match(r"^\d+\.\d+(\.\d+)?$", d)] if os.path.isdir(versions_dir) else []
    newest = max(ported, key=key) if ported else "0.0"
    out = []
    for mc in releases():
        if key(mc) <= key(newest) or mc in ported:
            continue
        info = loaders(mc)
        info["from"] = newest
        # A port needs every loader the version before it had; Fabric and NeoForge usually
        # land within days of a release, Forge sometimes later.
        info["ready"] = all(info[k] for k in ("fabric_loader_version", "neoforge_version", "neo_form_version", "forge_version"))
        out.append(info)
    return out


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "releases"
    if cmd == "releases":
        print(json.dumps(releases()))
    elif cmd == "loaders":
        print(json.dumps(loaders(sys.argv[2]), indent=2))
    elif cmd == "tools":
        print(json.dumps(tools(), indent=2))
    elif cmd == "pending":
        print(json.dumps(pending(sys.argv[2]), indent=2))
    else:
        sys.exit(__doc__)
