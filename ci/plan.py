#!/usr/bin/env python3
"""Turns every versions/<minecraft>/port.json into CI job matrices.

Writes GitHub Actions outputs (to $GITHUB_OUTPUT, or stdout when run by hand):
  versions  [{"minecraft", "java"}]                          one build job each
  clients   [{"minecraft", "java", "loader", "mcrt"}]        one real-client run each, by MC-Runtime-Test
  selfdrive [{"minecraft", "java", "loader"}]                 the same where MC-Runtime-Test has no build
                                                            ("mcrt": "none"): the mod makes its own world
  quilt     [{"minecraft", "java"}]                          versions with a Quilt run
  folia     [{"minecraft", "java"}]                          versions with a Folia plugin

A port is a directory with a port.json; nothing in the workflow names a version.
"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def key(version):
    return [int(p) for p in re.findall(r"\d+", version)]


def ports():
    base = os.path.join(ROOT, "versions")
    found = []
    for name in os.listdir(base):
        path = os.path.join(base, name, "port.json")
        if os.path.isfile(path):
            with open(path, encoding="utf-8") as f:
                port = json.load(f)
            if port["minecraft"] != name:
                sys.exit(f"versions/{name}/port.json says minecraft {port['minecraft']}")
            found.append(port)
    return sorted(found, key=lambda p: key(p["minecraft"]), reverse=True)


def main():
    all_ports = ports()
    only = os.environ.get("GEGI_ONLY_VERSION", "").strip()
    if only:
        all_ports = [p for p in all_ports if p["minecraft"] == only]
        if not all_ports:
            sys.exit(f"no port for {only}")
    base = lambda p: {"minecraft": p["minecraft"], "java": p["java"]}
    outputs = {
        "versions": [base(p) for p in all_ports],
        "clients": [dict(base(p), **c) for p in all_ports for c in p.get("clients", []) if c.get("mcrt", "none") != "none"],
        "selfdrive": [dict(base(p), **c) for p in all_ports for c in p.get("clients", []) if c.get("mcrt", "none") == "none"],
        "quilt": [base(p) for p in all_ports if p.get("quilt")],
        "folia": [base(p) for p in all_ports if p.get("folia")],
    }
    target = os.environ.get("GITHUB_OUTPUT")
    lines = [f"{name}={json.dumps(value, separators=(',', ':'))}" for name, value in outputs.items()]
    if target:
        with open(target, "a", encoding="utf-8") as f:
            f.write("\n".join(lines) + "\n")
    print("\n".join(lines))


if __name__ == "__main__":
    main()
