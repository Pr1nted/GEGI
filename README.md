# Open Arcade

**Browse and open itch.io and Newgrounds web games from inside Minecraft.**

An Open Arcade button in Options, or `/arcade` in chat, opens a menu of web games:
recommended picks (Open Doctrines first), live shelves from itch.io, a search box,
and a box for pasting any itch.io or Newgrounds link. Click a game and it opens in
your web browser.

Minecraft 26.2 · Fabric · Quilt · NeoForge · Forge · Folia (server plugin) · MIT

## Features

- **The menu.** Recommended, Popular, Newest, Free and Strategy tabs read from
  itch.io's own browse feeds, with thumbnails. A Browse sites tab links to the itch.io
  and Newgrounds game pages. Search filters the current tab.
- **Three ways in.** The Open Arcade button at the top right of Options; typing
  `/arcade` (or `/openarcade`) in chat, handled on your side and never sent to the
  server; and the Config button in your loader's mod list (Mod Menu on Fabric and
  Quilt, the built-in list on NeoForge and Forge).
- **No dependencies.** Nothing to install besides the loader. There is no Fabric API,
  Architectury or config library. Mod Menu support is there when Mod Menu is, and
  unused when it is not.
- **Folia and Paper.** A server has no screen, so the plugin's `/arcade` sends the same
  recommendations as clickable chat links.

## What it connects to

- `itch.io/games/.../platform-web.xml`, itch.io's public RSS feeds for its browse pages.
- `img.itch.zone`, for thumbnails.

Nothing else. No accounts, no telemetry. Games open in your own browser, and only
itch.io and Newgrounds links open at all; anything else is refused.

**Newgrounds** blocks automated requests to its listings, so the mod does not fetch
Newgrounds lists. It links to Newgrounds' game pages, and any Newgrounds game link you
paste opens.

## Install

| Loader | Jar | Needs |
|---|---|---|
| Fabric | `openarcade-fabric-26.2-<version>.jar` | Fabric Loader 0.19.3+ |
| Quilt | the Fabric jar | Quilt Loader |
| NeoForge | `openarcade-neoforge-26.2-<version>.jar` | NeoForge 26.2.0.88+ |
| Forge | `openarcade-forge-26.2-<version>.jar` | Forge 65.1.3+ |
| Folia / Paper | `openarcade-folia-26.2-<version>.jar` in `plugins/` | Folia or Paper 26.2 |

The mod is client-side: it does nothing on a dedicated server and is not needed there.

## Build

Java 25.

```bash
./gradlew build
```

Jars land in `<loader>/build/libs/`. The project is
[MultiLoader-Template](https://github.com/jaredlll08/MultiLoader-Template) with a Forge
module and a Folia module added. Shared code lives in `common/` and uses only vanilla
Minecraft: the Options button, the command and the tick hook are mixins, which is why
no loader API is needed.

## CI

`.github/workflows/ci.yml` builds every loader, then **runs the real game**:

- **Fabric, NeoForge, Forge.** The 26.2 client starts headless with
  [MC-Runtime-Test](https://github.com/headlesshq/mc-runtime-test) and joins a world.
- **Quilt.** The same, installed with the Quilt installer (`ci/quilt-runtime.sh`).
- **Folia.** A Folia 26.2 server starts with the plugin and must log that it is enabled
  (`ci/folia-smoke.sh`).

In the client jobs the mod runs its own self-test in the world
(`OPENARCADE_SELFTEST=1`): it types `/arcade`, waits for the menu to draw, opens
Options, clicks the Open Arcade button and checks the menu opened again. Anything that
does not happen fails the job.

## License

MIT. See [LICENSE](LICENSE).

Open Arcade is not affiliated with itch.io, Newgrounds or Mojang.
