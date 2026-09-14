**Play itch.io and Newgrounds web games without leaving Minecraft.**

Good Enough Game Integration (GEGI) adds a menu of web games to Minecraft. Pick one and it plays right there, in Minecraft's window, with your keyboard and mouse.

## Opening the menu

- The **GEGI** button at the top right of **Options**
- **/gegi** (or **/arcade**) in chat. It is handled on your side and never sent to the server
- The **Config** button in your mod list (Mod Menu on Fabric and Quilt, the built-in list on Forge and NeoForge)

## What's in it

- **Recommended**, **Popular**, **Newest**, **Free** and **Strategy** shelves, read from itch.io's own browse pages, with thumbnails
- A **search box** for the shelf you are on
- A box for **pasting any itch.io or Newgrounds game link**
- **Browse sites**, which links to the itch.io and Newgrounds game pages

Games that label themselves as adult (18+, NSFW and the like) are left off the shelves. itch.io's feeds carry no age rating, so this relies on what a game says about itself.

## Games run inside Minecraft

GEGI plays web games in its own copy of Chromium, drawn into a Minecraft screen. Every key, click and scroll goes to the game. Escape belongs to the game too: **press it twice to leave**. **Open in browser** is always one click away.

**Chromium is downloaded only if you agree.** The first time you play, GEGI asks first. The download is about 100 MB (300 MB unpacked), from Maven Central or jcefmaven's GitHub releases. If you say no, games open in your own browser instead. One copy is kept per player and shared by every Minecraft version and modpack:

| OS | Where |
|---|---|
| Windows | `%LOCALAPPDATA%\Gegi` |
| macOS | `~/Library/Application Support/Gegi` |
| Linux | `$XDG_DATA_HOME/gegi` or `~/.local/share/gegi` |

Delete that folder to remove it. Chromium runs as a separate process with no window of its own, so a crashing web page cannot take Minecraft down with it. It exits when the game does. Windows on ARM has no windowless Chromium build: there, games open in your browser.

## Recommended first: Open Doctrines

The first recommendation is **Open Doctrines**, a strategy game by the author of this mod. The other shelves come straight from itch.io.

## What it connects to

- itch.io's public browse feeds, and `img.itch.zone` for thumbnails
- Maven Central or jcefmaven's GitHub releases, once, if you agree to download Chromium
- The games you choose to play

Nothing else: no accounts, no telemetry. Only itch.io and Newgrounds links open from the menu. Newgrounds blocks automated requests to its lists, so GEGI links to Newgrounds rather than listing its games; any Newgrounds game link you paste still plays.

## Versions and loaders

One build for the last release of every Minecraft minor version:

| Minecraft | Loaders |
|---|---|
| 26.2, 26.1.2, 1.21.11, 1.20.6 | Fabric, Quilt, NeoForge, Forge |
| 1.19.4, 1.18.2, 1.17.1, 1.16.5, 1.15.2, 1.14.4 | Fabric, Quilt, Forge |
| 1.13.2, 1.12.2 | Forge |

**No dependencies**: no Fabric API, Architectury or config library. Mod Menu is supported when it is installed.

GEGI is client-side. A server does not need it. For servers there is a separate Paper and Folia plugin, *GEGI Plugin*: its /gegi sends the same recommendations as clickable chat links.

Every build is started as a real Minecraft client in CI before release, and its self-test opens the menu through the Options button and the command.

## License

MIT
