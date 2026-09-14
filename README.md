# Open Arcade

**Browse itch.io and Newgrounds web games, and play them, inside Minecraft.**

An Open Arcade button in Options, or `/arcade` in chat, opens a menu of web games:
recommended picks (Open Doctrines first), live shelves from itch.io, a search box,
and a box for pasting any itch.io or Newgrounds link. Click a game and it plays right there, in
Minecraft's window, keyboard and mouse included.

Minecraft 26.2 · Fabric · Quilt · NeoForge · Forge · Folia (server plugin) · MIT

## Features

- **The menu.** Recommended, Popular, Newest, Free and Strategy tabs read from
  itch.io's own browse feeds, with thumbnails. A Browse sites tab links to the itch.io
  and Newgrounds game pages. Search filters the current tab.
- **Games run in the game.** Open Arcade carries its own Chromium: the page draws
  into a Minecraft screen and every key, click and scroll goes to it. Escape belongs to
  the game you are playing; press it twice to leave. *Open in browser* is always one
  click away.
- **Three ways in. The Open Arcade button at the top right of Options; typing
  `/arcade` (or `/openarcade`) in chat, handled on your side and never sent to the
  server; and the Config button in your loader's mod list (Mod Menu on Fabric and
  Quilt, the built-in list on NeoForge and Forge).
- **No dependencies.** Nothing to install besides the loader. There is no Fabric API,
  Architectury or config library. Mod Menu support is there when Mod Menu is, and
  unused when it is not.
- **Folia and Paper.** A server has no screen, so the plugin's `/arcade` sends the same
  recommendations as clickable chat links.

## Playing inside Minecraft

The first time you play, Open Arcade asks before downloading Chromium (about 100 MB,
300 MB unpacked: the [jcefmaven](https://github.com/jcefmaven/jcefmaven) natives, from
Maven Central or jcefmaven's GitHub releases). Say no and games open in your own browser instead. Chromium is kept
per player, not per instance, so every Minecraft version and modpack shares one copy:

| OS | Where |
|---|---|
| Windows | `%LOCALAPPDATA%\OpenArcade` |
| macOS | `~/Library/Application Support/OpenArcade` |
| Linux | `$XDG_DATA_HOME/openarcade` or `~/.local/share/openarcade` |

Delete that folder to remove it. It also holds the browser profile, so an itch.io
login stays logged in.

Chromium runs in a separate process on Minecraft's own Java (`openarcade-browser.jar`,
inside the mod jar), with no window. It paints frames into shared memory, which the mod
uploads as a texture; input goes back over a pipe. The process exits with the game, and
a crash in a web page cannot take Minecraft down with it.

Where Chromium cannot start (Windows on ARM has no windowless Chromium build), the game
screen says why, and *Open in browser* opens the game in your own browser.

## What it connects to

- `itch.io/games/.../platform-web.xml`, itch.io's public RSS feeds for its browse pages.
- `img.itch.zone`, for thumbnails.

- Maven Central or jcefmaven's GitHub releases, once, when you agree to download Chromium.
- The games you play, in Chromium, like any browser.

Nothing else. No accounts, no telemetry. Only itch.io and Newgrounds links open from
the menu; anything else is refused.

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

- **Chromium.** `./gradlew :browser:smokeTest` starts the helper exactly as the mod
  does, loads a page, and checks that a click, a key press, a typed character and a
  resize each reach it and come back as a frame. CI runs it on Linux (Java 8 and 25),
  Windows and macOS.

## License

MIT. See [LICENSE](LICENSE).

Open Arcade is not affiliated with itch.io, Newgrounds or Mojang.
