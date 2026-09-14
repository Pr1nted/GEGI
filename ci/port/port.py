#!/usr/bin/env python3
"""Ports Open Arcade to a new Minecraft version, the way a person starts a port.

  port.py <from> <to> [--summary summary.md]

Copies versions/<from> to versions/<to>, then moves the copy to the new game: its
loaders (Fabric Loader, NeoForge, NeoForm, Forge, Mod Menu, the Paper API), its Java,
the newest Gradle, Fabric Loom and ModDevGradle, and every place the version is
written down (port.json, the Gradle project name, the Folia plugin's api-version).

What this cannot know, a changed Minecraft API, shows up in CI as a compile error or
a failed in-game self-test, which is why the port workflow tests the result before
anyone sees a pull request.
"""
import argparse
import json
import os
import re
import shutil
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import versions  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def next_minor(mc):
    parts = versions.key(mc)
    return f"1.{parts[1] + 1}" if parts[0] == 1 else f"{parts[0]}.{parts[1] + 1}"


def set_property(text, name, value, changes):
    pattern = re.compile(rf"^{re.escape(name)}=(.*)$", re.M)
    match = pattern.search(text)
    if not match:
        return text
    if match.group(1) != str(value):
        changes.append(f"`{name}` {match.group(1)} → {value}")
    return pattern.sub(lambda _: f"{name}={value}", text, count=1)


def rewrite(path, transform):
    with open(path, encoding="utf-8") as f:
        before = f.read()
    after = transform(before)
    if after != before:
        with open(path, "w", encoding="utf-8", newline="") as f:
            f.write(after)
        return True
    return False


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("source")
    parser.add_argument("target")
    parser.add_argument("--summary")
    args = parser.parse_args()

    src = os.path.join(ROOT, "versions", args.source)
    dst = os.path.join(ROOT, "versions", args.target)
    if not os.path.isfile(os.path.join(src, "port.json")):
        sys.exit(f"no port at versions/{args.source}")
    if os.path.exists(dst):
        sys.exit(f"versions/{args.target} already exists")

    loaders = versions.loaders(args.target)
    tools = versions.tools()
    changes = []
    missing = [k for k in ("fabric_loader_version", "neoforge_version", "neo_form_version", "forge_version",
                           "modmenu_version", "paper_api_version") if not loaders.get(k)]

    shutil.copytree(src, dst, ignore=shutil.ignore_patterns("build", ".gradle", "runs", "run", "out", "*.log"))

    # gradle.properties: the game and its loaders.
    props = os.path.join(dst, "gradle.properties")

    def bump_properties(text):
        text = set_property(text, "minecraft_version", args.target, changes)
        text = set_property(text, "minecraft_version_range", f"[{args.target}, {next_minor(args.target)})", changes)
        text = set_property(text, "java_version", loaders["java_version"], changes)
        for name in ("neo_form_version", "fabric_loader_version", "neoforge_version", "forge_version",
                     "forge_loader_version_range", "modmenu_version", "paper_api_version"):
            if loaders.get(name):
                text = set_property(text, name, loaders[name], changes)
        return text

    rewrite(props, bump_properties)

    # The plugins a new Minecraft usually needs the newest of.
    def bump_plugins(text):
        for plugin, version in (("net.fabricmc.fabric-loom", tools["fabric_loom"]), ("net.neoforged.moddev", tools["moddev"])):
            if not version:
                continue
            pattern = re.compile(rf"(id '{re.escape(plugin)}' version ')([^']+)(')")
            match = pattern.search(text)
            if match and match.group(2) != version:
                changes.append(f"`{plugin}` {match.group(2)} → {version}")
                text = pattern.sub(lambda m: m.group(1) + version + m.group(3), text, count=1)
        return text

    rewrite(os.path.join(dst, "build.gradle"), bump_plugins)

    def bump_gradle(text):
        match = re.search(r"gradle-([\d.]+)-bin\.zip", text)
        if match and tools["gradle"] and versions.key(tools["gradle"]) > versions.key(match.group(1)):
            changes.append(f"Gradle {match.group(1)} → {tools['gradle']}")
            text = text.replace(match.group(0), f"gradle-{tools['gradle']}-bin.zip")
        return text

    rewrite(os.path.join(dst, "gradle", "wrapper", "gradle-wrapper.properties"), bump_gradle)

    # Where the version is written down.
    rewrite(os.path.join(dst, "settings.gradle"),
            lambda t: re.sub(r"rootProject\.name = '[^']*'", f"rootProject.name = '{args.target}'", t))
    plugin_yml = os.path.join(dst, "folia", "src", "main", "resources", "plugin.yml")
    if os.path.isfile(plugin_yml):
        rewrite(plugin_yml, lambda t: re.sub(r"^api-version: .*$", f"api-version: '{args.target}'", t, flags=re.M))

    with open(os.path.join(dst, "port.json"), encoding="utf-8") as f:
        port = json.load(f)
    port["minecraft"] = args.target
    port["java"] = loaders["java_version"]
    if not loaders.get("forge_version"):
        port["clients"] = [c for c in port["clients"] if c["loader"] != "forge"]
    if not loaders.get("neoforge_version"):
        port["clients"] = [c for c in port["clients"] if c["loader"] != "neoforge"]
    if not loaders.get("paper_api_version"):
        port["folia"] = False
    with open(os.path.join(dst, "port.json"), "w", encoding="utf-8") as f:
        json.dump(port, f, indent=2)
        f.write("\n")

    summary = [
        f"Ports Open Arcade from Minecraft {args.source} to **{args.target}**, made by `ci/port/port.py` from a copy of `versions/{args.source}`.",
        "",
        "**Moved to:**",
        *[f"- {c}" for c in changes],
    ]
    if missing:
        summary += ["", "**Not available yet for this version** (left out of the port):", *[f"- `{m}`" for m in missing]]
    summary += ["", "Every loader in `port.json` was built and started as a real client with the in-game self-test before this was opened."]
    text = "\n".join(summary) + "\n"
    if args.summary:
        with open(args.summary, "w", encoding="utf-8") as f:
            f.write(text)
    print(text)


if __name__ == "__main__":
    main()
