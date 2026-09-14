#!/usr/bin/env python3
"""Publishes a GEGI release to Modrinth and CurseForge. Standard library only.

  publish.py check <tag>
      The tag (v1.0.0) matches `version` in every versions/*/gradle.properties.
      Writes tag= and version= to $GITHUB_OUTPUT.
  publish.py plan --jars <dir> [--tag <tag>]
      What would go where, checked against Modrinth's public lists of game versions
      and loaders. Needs no token.
  publish.py modrinth --jars <dir> --tag <tag>
      MODRINTH_TOKEN, MODRINTH_MOD_ID (the mod), MODRINTH_PLUGIN_ID (the Folia plugin; optional)
  publish.py curseforge --jars <dir> --tag <tag>
      CURSEFORGE_TOKEN + CURSEFORGE_MOD_ID for the mod,
      BUKKIT_TOKEN + BUKKIT_PLUGIN_ID for the Folia plugin on dev.bukkit.org (optional)

A jar is gegi-<loader>-<minecraft>-<version>.jar, anywhere under --jars: one upload
per Minecraft version and loader. The Fabric jar is tagged Quilt as well where that
version's port.json runs Quilt in CI.

Every game version and loader is looked up on the site before the first upload, so an
unknown one (a Minecraft release the site does not list yet) stops the job with
nothing half-published. Modrinth versions that already exist are skipped, so a rerun
continues where a failed one stopped. CurseForge's upload API cannot list files: do
not rerun its job after some uploads succeeded, or they are uploaded twice.
"""
import argparse
import glob
import io
import json
import os
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
USER_AGENT = "Pr1nted/GEGI release (github.com/Pr1nted/GEGI)"
MODRINTH_API = os.environ.get("MODRINTH_API", "https://api.modrinth.com/v2")
CURSEFORGE_API = os.environ.get("CURSEFORGE_API", "https://minecraft.curseforge.com")
BUKKIT_API = os.environ.get("BUKKIT_API", "https://dev.bukkit.org")

JAR = re.compile(r"^gegi-(?P<loader>fabric|forge|neoforge|folia)-(?P<minecraft>\d+\.\d+(?:\.\d+)?)-(?P<version>.+)\.jar$")
TAG = re.compile(r"^v(?P<version>\d+\.\d+\.\d+(?:-[0-9A-Za-z.]+)?)$")
LOADER_NAMES = {"fabric": "Fabric", "quilt": "Quilt", "forge": "Forge", "neoforge": "NeoForge", "folia": "Folia"}


def key(version):
    return [int(p) for p in re.findall(r"\d+", version)]


def fail(message):
    sys.exit(f"::error::{message}")


def summary(line):
    print(line)
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if path:
        with open(path, "a", encoding="utf-8") as f:
            f.write(line + "\n")


# ---- what is being released


def versions_in_repo():
    found = {}
    for props in sorted(glob.glob(os.path.join(ROOT, "versions", "*", "gradle.properties"))):
        with open(props, encoding="utf-8") as f:
            match = re.search(r"^version=(.*)$", f.read(), flags=re.M)
        found[os.path.basename(os.path.dirname(props))] = match.group(1).strip() if match else None
    return found


def check(tag):
    match = TAG.match(tag)
    if not match:
        fail(f"{tag} is not a release tag: use v<major>.<minor>.<patch>, like v1.0.0")
    version = match.group("version")
    wrong = {mc: v for mc, v in versions_in_repo().items() if v != version}
    if wrong:
        fail(f"{tag} does not match the mod's version in " + ", ".join(f"versions/{mc} ({v})" for mc, v in sorted(wrong.items(), key=lambda i: key(i[0]))))
    print(f"{tag}: every versions/*/gradle.properties says {version}")
    out = os.environ.get("GITHUB_OUTPUT")
    if out:
        with open(out, "a", encoding="utf-8") as f:
            f.write(f"tag={tag}\nversion={version}\n")
    return version


def port(minecraft):
    path = os.path.join(ROOT, "versions", minecraft, "port.json")
    if not os.path.isfile(path):
        fail(f"a jar for Minecraft {minecraft}, but there is no versions/{minecraft}/port.json")
    with open(path, encoding="utf-8") as f:
        return json.load(f)


class Upload:
    def __init__(self, path, loader, minecraft, version):
        self.path, self.loader, self.minecraft, self.version = path, loader, minecraft, version
        self.port = port(minecraft)

    @property
    def plugin(self):
        return self.loader == "folia"

    def loaders(self):
        """Modrinth's loader names. The plugin uses Paper's API, so it runs on Paper, and on Folia where Folia exists."""
        if self.plugin:
            return ["paper", "folia"] if key(self.minecraft) >= [1, 19, 4] else ["paper"]
        if self.loader == "fabric" and self.port.get("quilt"):
            return ["fabric", "quilt"]
        return [self.loader]

    def version_number(self):
        return f"{self.version}+{self.minecraft}-{self.loader}"

    def name(self):
        return f"GEGI {self.version} for {LOADER_NAMES[self.loader]} {self.minecraft}"

    def release_type(self):
        lower = self.version.lower()
        return "alpha" if "alpha" in lower else "beta" if ("beta" in lower or "-" in lower) else "release"


def find_jars(directory, version=None):
    uploads = {}
    for path in sorted(glob.glob(os.path.join(directory, "**", "gegi-*.jar"), recursive=True)):
        match = JAR.match(os.path.basename(path))
        if not match or match.group("version").endswith(("-sources", "-javadoc", "-dev")):
            continue
        if version and match.group("version") != version:
            continue
        upload = Upload(path, match.group("loader"), match.group("minecraft"), match.group("version"))
        slot = (upload.loader, upload.minecraft)
        if slot in uploads:
            fail(f"two jars for {upload.loader} {upload.minecraft}: {uploads[slot].path} and {path}")
        uploads[slot] = upload
    if not uploads:
        fail(f"no gegi-<loader>-<minecraft>-{version or '<version>'}.jar under {directory}")
    return sorted(uploads.values(), key=lambda u: (key(u.minecraft), u.loader))


def changelog(tag, version):
    """The annotated tag's message, or a plain line for a lightweight tag."""
    try:
        kind = subprocess.run(["git", "-C", ROOT, "for-each-ref", f"refs/tags/{tag}", "--format=%(objecttype)"],
                              capture_output=True, text=True, check=True).stdout.strip()
        if kind == "tag":
            text = subprocess.run(["git", "-C", ROOT, "for-each-ref", f"refs/tags/{tag}", "--format=%(contents)"],
                                  capture_output=True, text=True, check=True).stdout.strip()
            text = re.sub(r"-----BEGIN (PGP|SSH) SIGNATURE-----.*", "", text, flags=re.S).strip()
            if text:
                return text
    except (OSError, subprocess.CalledProcessError):
        pass
    return f"GEGI {version}."


# ---- HTTP


class HttpError(Exception):
    def __init__(self, status, body):
        super().__init__(f"HTTP {status}: {body[:500]}")
        self.status = status


def request(method, url, headers=None, body=None, content_type=None, retry=True):
    headers = dict(headers or {})
    headers["User-Agent"] = USER_AGENT
    if content_type:
        headers["Content-Type"] = content_type
    for attempt in range(1, 6):
        req = urllib.request.Request(url, data=body, method=method, headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=120) as response:
                data = response.read()
                return json.loads(data) if data else None
        except urllib.error.HTTPError as e:
            text = e.read().decode("utf-8", "replace")
            # A 429 was not processed. Anything else is retried only when the caller says it is safe to.
            transient = e.code == 429 or (retry and e.code >= 500)
            if not transient or attempt == 5:
                raise HttpError(e.code, text) from None
        except (urllib.error.URLError, TimeoutError) as e:
            if not retry or attempt == 5:
                raise
        wait = 20 * attempt
        print(f"  {method} {url}: trying again in {wait}s (attempt {attempt} of 5)")
        time.sleep(wait)


def multipart(fields, files):
    boundary = "gegi-" + uuid.uuid4().hex
    out = io.BytesIO()
    for name, value in fields:
        out.write(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n'
                  f"Content-Type: application/json; charset=utf-8\r\n\r\n".encode())
        out.write(value.encode("utf-8"))
        out.write(b"\r\n")
    for name, path in files:
        out.write(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"; filename="{os.path.basename(path)}"\r\n'
                  f"Content-Type: application/java-archive\r\n\r\n".encode())
        with open(path, "rb") as f:
            out.write(f.read())
        out.write(b"\r\n")
    out.write(f"--{boundary}--\r\n".encode())
    return out.getvalue(), f"multipart/form-data; boundary={boundary}"


def env(name, required=True):
    value = os.environ.get(name, "").strip()
    if required and not value:
        fail(f"{name} is not set")
    return value


# ---- Modrinth


def modrinth_tags():
    game_versions = {v["version"] for v in request("GET", f"{MODRINTH_API}/tag/game_version")}
    loaders = {l["name"] for l in request("GET", f"{MODRINTH_API}/tag/loader")}
    return game_versions, loaders


def modrinth_unknown(uploads, game_versions, loaders):
    problems = []
    for u in uploads:
        if u.minecraft not in game_versions:
            problems.append(f"Modrinth does not list Minecraft {u.minecraft} ({os.path.basename(u.path)})")
        for loader in u.loaders():
            if loader not in loaders:
                problems.append(f"Modrinth does not know the loader {loader} ({os.path.basename(u.path)})")
    return sorted(set(problems))


# Modrinth asks every mod version where it runs, and will not review a project
# while any version leaves that unanswered. The answer lives on each VERSION
# (Modrinth's v3 "environment" field, which v2 has no way to send with an
# upload), so it is set after the jars are up, on every version the project has.
# Every fabric.mod.json says "environment": "client" and every mods.toml says
# side = "CLIENT", so the mod is client-only. Modrinth does not ask plugins.
MODRINTH_API_V3 = re.sub(r"/v2/?$", "/v3", MODRINTH_API)
MODRINTH_ENVIRONMENT = {
    "mod": "client_only",
    "plugin": None,
}


def modrinth_environment(label, info, auth):
    """Declare where every version of the project runs, where it does not say so already."""
    want = MODRINTH_ENVIRONMENT[label]
    if not want:
        summary(f"- environment: Modrinth does not ask {label} projects for one")
        return
    versions = request("GET", f"{MODRINTH_API_V3}/project/{info['id']}/version", headers=auth) or []
    changed = 0
    for v in versions:
        if v.get("environment") == want:
            continue
        request("PATCH", f"{MODRINTH_API_V3}/version/{v['id']}", headers=auth,
                body=json.dumps({"environment": want}).encode("utf-8"), content_type="application/json")
        changed += 1
    after = request("GET", f"{MODRINTH_API_V3}/project/{info['id']}/version", headers=auth) or []
    missing = sorted(v.get("version_number", v["id"]) for v in after if v.get("environment") != want)
    if missing:
        fail(f"Modrinth still lists these versions without the {want} environment: {', '.join(missing)}")
    summary(f"- environment {want} on all {len(after)} versions ({changed} changed)")
    # The project page keeps its own answer too, and asks its owner to confirm it
    # ("side_types_migration_review_status"). This is the same edit its Environment
    # settings page makes when that answer is saved.
    project = request("GET", f"{MODRINTH_API_V3}/project/{info['id']}", headers=auth) or {}
    was = (project.get("environment"), project.get("side_types_migration_review_status"))
    if was != ([want], "reviewed"):
        request("PATCH", f"{MODRINTH_API_V3}/project/{info['id']}", headers=auth,
                body=json.dumps({"environment": want, "side_types_migration_review_status": "reviewed"}).encode("utf-8"),
                content_type="application/json")
        summary(f"- project environment set to {want}, reviewed (was {was[0]}, {was[1]})")
    else:
        summary(f"- project environment already {want}, reviewed")
    nags = (request("GET", f"{MODRINTH_API_V3}/project/{info['id']}/validate", headers=auth) or {}).get("nags", [])
    for nag in nags:
        summary(f"- Modrinth checklist: {json.dumps(nag, sort_keys=True)}")
    if any("select_environment" in json.dumps(nag) for nag in nags):
        fail("Modrinth's checklist still asks for an environment")
    summary(f"- Modrinth checklist: no environment item left ({len(nags)} other items)")


def modrinth(uploads, tag, version):
    token = env("MODRINTH_TOKEN")
    auth = {"Authorization": token}
    game_versions, loaders = modrinth_tags()
    problems = modrinth_unknown(uploads, game_versions, loaders)
    if problems:
        fail("nothing uploaded: " + "; ".join(problems))
    text = changelog(tag, version)

    targets = []
    for label, variable, wanted in (("mod", "MODRINTH_MOD_ID", False), ("plugin", "MODRINTH_PLUGIN_ID", True)):
        files = [u for u in uploads if u.plugin == wanted]
        project = env(variable, required=False)
        if not files:
            continue
        if not project:
            summary(f"Modrinth {label}: {variable} is not set, so its {len(files)} jars are not uploaded")
            continue
        info = request("GET", f"{MODRINTH_API}/project/{project}", headers=auth)
        existing = {v["version_number"] for v in request("GET", f"{MODRINTH_API}/project/{info['id']}/version", headers=auth)}
        targets.append((label, info, existing, files))

    for label, info, existing, files in targets:
        summary(f"### Modrinth {label}: {info.get('title', info['id'])}")
        for u in files:
            if u.version_number() in existing:
                summary(f"- {u.version_number()}: already on Modrinth, skipped")
                continue
            data = {
                "name": u.name(),
                "version_number": u.version_number(),
                "changelog": text,
                "dependencies": [],
                "game_versions": [u.minecraft],
                "version_type": u.release_type(),
                "loaders": u.loaders(),
                "featured": False,
                "status": "listed",
                "project_id": info["id"],
                "file_parts": ["file"],
                "primary_file": "file",
            }
            body, content_type = multipart([("data", json.dumps(data))], [("file", u.path)])
            try:
                created = request("POST", f"{MODRINTH_API}/version", headers=auth, body=body, content_type=content_type, retry=False)
            except (HttpError, urllib.error.URLError, TimeoutError) as e:
                # The upload may have gone through before the connection failed: look before trying again.
                time.sleep(30)
                now = {v["version_number"] for v in request("GET", f"{MODRINTH_API}/project/{info['id']}/version", headers=auth)}
                if u.version_number() not in now:
                    if isinstance(e, HttpError) and e.status < 500 and e.status != 429:
                        fail(f"Modrinth refused {os.path.basename(u.path)}: {e}")
                    created = request("POST", f"{MODRINTH_API}/version", headers=auth, body=body, content_type=content_type, retry=False)
                else:
                    created = {"id": "(created on the first try)"}
            summary(f"- {u.version_number()}: uploaded ({', '.join(u.loaders())}; {u.minecraft}) as {created.get('id')}")
        modrinth_environment(label, info, auth)


# ---- CurseForge (and dev.bukkit.org, which runs the same upload API)


def curseforge_game_versions(base, token):
    headers = {"X-Api-Token": token}
    types = {t["id"]: t.get("slug", "") for t in request("GET", f"{base}/api/game/version-types", headers=headers)}
    return [dict(v, type=types.get(v.get("gameVersionTypeID"), "")) for v in request("GET", f"{base}/api/game/versions", headers=headers)]


def curseforge_ids(u, versions, bukkit):
    def named(name, type_prefix=None):
        return [v["id"] for v in versions if v.get("name") == name and (type_prefix is None or v["type"].startswith(type_prefix))]

    if bukkit:
        # dev.bukkit.org lists some Minecraft versions only by their minor ("1.21").
        ids = named(u.minecraft) or named(".".join(u.minecraft.split(".")[:2]))
        if not ids:
            raise LookupError(f"dev.bukkit.org does not list Minecraft {u.minecraft}")
        return ids[:1]
    ids = named(u.minecraft, "minecraft")[:1]
    if not ids:
        raise LookupError(f"CurseForge does not list Minecraft {u.minecraft}")
    for loader in u.loaders():
        found = named(LOADER_NAMES[loader], "modloader")
        if not found:
            raise LookupError(f"CurseForge does not know the loader {LOADER_NAMES[loader]}")
        ids += found[:1]
    ids += named("Client", "environment")[:1]
    ids += named(f"Java {u.port['java']}", "java")[:1]
    return ids


def curseforge(uploads, tag, version):
    text = changelog(tag, version)
    sites = []
    for label, base, token_name, project_name, wanted in (
            ("CurseForge mod", CURSEFORGE_API, "CURSEFORGE_TOKEN", "CURSEFORGE_MOD_ID", False),
            ("dev.bukkit.org plugin", BUKKIT_API, "BUKKIT_TOKEN", "BUKKIT_PLUGIN_ID", True)):
        files = [u for u in uploads if u.plugin == wanted]
        project = env(project_name, required=False)
        if not files:
            continue
        if not project:
            summary(f"{label}: {project_name} is not set, so its {len(files)} jars are not uploaded")
            continue
        token = env(token_name)
        versions = curseforge_game_versions(base, token)
        planned, problems = [], []
        for u in files:
            try:
                planned.append((u, curseforge_ids(u, versions, bukkit=wanted)))
            except LookupError as e:
                problems.append(str(e))
        if problems:
            fail(f"nothing uploaded to {label}: " + "; ".join(sorted(set(problems))))
        sites.append((label, base, token, project, planned))

    for label, base, token, project, planned in sites:
        summary(f"### {label} {project}")
        for u, ids in planned:
            metadata = {"changelog": text, "changelogType": "markdown", "displayName": u.name(),
                        "gameVersions": ids, "releaseType": u.release_type()}
            body, content_type = multipart([("metadata", json.dumps(metadata))], [("file", u.path)])
            try:
                created = request("POST", f"{base}/api/projects/{project}/upload-file", headers={"X-Api-Token": token},
                                  body=body, content_type=content_type, retry=False)
            except HttpError as e:
                fail(f"{label} refused {os.path.basename(u.path)} after the files listed above were uploaded: {e}")
            summary(f"- {os.path.basename(u.path)}: uploaded as file {created.get('id')} ({', '.join(u.loaders())}; {u.minecraft})")


# ---- plan


def plan(uploads, tag):
    game_versions, loaders = modrinth_tags()
    for label, want in MODRINTH_ENVIRONMENT.items():
        summary(f"Modrinth {label} environment: {want or 'not asked'}")
    summary("| Jar | Modrinth project | Version number | Loaders | Minecraft |")
    summary("|---|---|---|---|---|")
    for u in uploads:
        summary(f"| {os.path.basename(u.path)} | {'plugin' if u.plugin else 'mod'} | {u.version_number()} | {', '.join(u.loaders())} | {u.minecraft} |")
    problems = modrinth_unknown(uploads, game_versions, loaders)
    for p in problems:
        print(f"::warning::{p}")
    if tag:
        print("\nChangelog:\n" + changelog(tag, uploads[0].version))
    print(f"\n{len(uploads)} jars; {'all known to Modrinth' if not problems else f'{len(problems)} problems'}. "
          "CurseForge's lists need a token, so they are checked when its job runs.")
    return 1 if problems else 0


def environment_only():
    """Set just the environment of every version, without uploading anything."""
    token = env("MODRINTH_TOKEN")
    auth = {"Authorization": token}
    done = 0
    for label, variable in (("mod", "MODRINTH_MOD_ID"), ("plugin", "MODRINTH_PLUGIN_ID")):
        project = env(variable, required=False)
        if not project:
            summary(f"Modrinth {label}: {variable} is not set, skipped")
            continue
        info = request("GET", f"{MODRINTH_API}/project/{project}", headers=auth)
        summary(f"### Modrinth {label}: {info.get('title', info['id'])}")
        modrinth_environment(label, info, auth)
        done += 1
    if not done:
        fail("neither MODRINTH_MOD_ID nor MODRINTH_PLUGIN_ID is set")
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("check").add_argument("tag")
    sub.add_parser("environment")
    for name in ("plan", "modrinth", "curseforge"):
        p = sub.add_parser(name)
        p.add_argument("--jars", required=True)
        p.add_argument("--tag", required=name != "plan")
    args = parser.parse_args()
    if args.command == "check":
        check(args.tag)
        return 0
    if args.command == "environment":
        return environment_only()
    version = check(args.tag) if args.tag else None
    uploads = find_jars(args.jars, version)
    if args.command == "plan":
        return plan(uploads, args.tag)
    if args.command == "modrinth":
        modrinth(uploads, args.tag, version)
    else:
        curseforge(uploads, args.tag, version)
    return 0


if __name__ == "__main__":
    sys.exit(main())
