#!/usr/bin/env python3
"""Makes an older Minecraft's port from the next newer one, for the API eras that
changed underneath the mod. The automatic porter only ever moves forward, onto a
release nobody has seen; going back, the differences are known, and are written
down here as rewrites, one era at a time.

  downport.py 1.21.11 1.20.6

Copies versions/<from> to versions/<to>, points the build at <to>'s loaders, and
rewrites the client code across the API boundary between the two. The result is a
starting point that compiles or says exactly where it does not; it is checked by
building it and running the real client in CI like any other port.
"""
import json
import os
import re
import shutil
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import versions  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def sub(text, pattern, replacement, flags=0, count=0):
    return re.sub(pattern, replacement, text, count=count, flags=flags)


# ---- 1.21.11 -> 1.20.6 --------------------------------------------------------------
# No input event records (1.21.9), no RenderPipelines (1.21.6), Identifier was still
# ResourceLocation (1.21.11), Util and OptionsScreen in their older packages,
# DynamicTexture without a label, a synchronous Screenshot, no playButtonClickSound,
# and no JSpecify on the classpath.

def to_1_20_6(path, text):
    name = os.path.basename(path)
    t = text

    # Everywhere
    t = t.replace("import org.jspecify.annotations.Nullable;\n", "")
    t = sub(t, r"@Nullable\s+", "")
    t = t.replace("net.minecraft.resources.Identifier", "net.minecraft.resources.ResourceLocation")
    t = sub(t, r"\bIdentifier\.fromNamespaceAndPath\(", "new ResourceLocation(")
    t = sub(t, r"\bIdentifier\b", "ResourceLocation")
    t = t.replace("import net.minecraft.util.Util;", "import net.minecraft.Util;")
    t = t.replace("net.minecraft.client.gui.screens.options.OptionsScreen", "net.minecraft.client.gui.screens.OptionsScreen")
    t = t.replace("import net.minecraft.client.renderer.RenderPipelines;\n", "")
    # blit(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, w, h, uw, vh, tw, th)
    #   -> blit(id, x, y, w, h, u, v, uw, vh, tw, th)
    t = sub(t, r"\.blit\(RenderPipelines\.GUI_TEXTURED,\s*([^,]+),\s*([^,]+),\s*([^,]+),\s*([^,]+),\s*([^,]+),\s*([^,]+),\s*([^,]+),",
            r".blit(\1, \2, \3, \6, \7, \4, \5,")
    t = sub(t, r"new DynamicTexture\(\(\) -> [^,]+,\s*", "new DynamicTexture(")

    # Input: event records -> the older primitive signatures
    t = t.replace("import net.minecraft.client.input.MouseButtonEvent;\n", "")
    t = t.replace("import net.minecraft.client.input.MouseButtonInfo;\n", "")
    t = t.replace("import net.minecraft.client.input.KeyEvent;\n", "")
    t = t.replace("import net.minecraft.client.input.CharacterEvent;\n", "")

    if name == "ArcadeScreen.java":
        t = t.replace("public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {\n"
                      "        if (super.mouseClicked(event, doubleClick)) return true;\n"
                      "        if (event.button() != 0) return false;\n"
                      "        GameEntry game = model.gameAt(this.width, this.height, event.x(), event.y());",
                      "public boolean mouseClicked(double mouseX, double mouseY, int button) {\n"
                      "        if (super.mouseClicked(mouseX, mouseY, button)) return true;\n"
                      "        if (button != 0) return false;\n"
                      "        GameEntry game = model.gameAt(this.width, this.height, mouseX, mouseY);")
        t = t.replace("AbstractWidget.playButtonClickSound(this.minecraft.getSoundManager());",
                      "this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));")
        t = t.replace("import net.minecraft.client.gui.components.AbstractWidget;\n",
                      "import net.minecraft.client.resources.sounds.SimpleSoundInstance;\n")
        t = t.replace("import net.minecraft.network.chat.Component;\n",
                      "import net.minecraft.network.chat.Component;\nimport net.minecraft.sounds.SoundEvents;\n")

    if name == "GameScreen.java":
        t = t.replace("private long lastEscape;", "private long lastEscape;\n    private long lastClick;")
        t = t.replace(
            "    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {\n"
            "        if (super.mouseClicked(event, doubleClick)) return true;\n"
            "        if (!inView(event.x(), event.y())) return false;\n"
            "        pressed = true;\n"
            "        runtime.send(BrowserProtocol.MOUSE_DOWN + \" \" + event.button() + \" \" + bx(event.x()) + \" \" + by(event.y()) + \" \" + (doubleClick ? 2 : 1));",
            "    public boolean mouseClicked(double x, double y, int button) {\n"
            "        if (super.mouseClicked(x, y, button)) return true;\n"
            "        if (!inView(x, y)) return false;\n"
            "        pressed = true;\n"
            "        // This Minecraft does not report double clicks; count them here, as the game's own widgets do.\n"
            "        long now = System.currentTimeMillis();\n"
            "        boolean doubleClick = now - lastClick < 250;\n"
            "        lastClick = now;\n"
            "        runtime.send(BrowserProtocol.MOUSE_DOWN + \" \" + button + \" \" + bx(x) + \" \" + by(y) + \" \" + (doubleClick ? 2 : 1));")
        t = t.replace(
            "    public boolean mouseReleased(MouseButtonEvent event) {\n"
            "        if (pressed) {\n"
            "            pressed = false;\n"
            "            runtime.send(BrowserProtocol.MOUSE_UP + \" \" + event.button() + \" \" + bx(event.x()) + \" \" + by(event.y()));\n"
            "            return true;\n"
            "        }\n"
            "        return super.mouseReleased(event);",
            "    public boolean mouseReleased(double x, double y, int button) {\n"
            "        if (pressed) {\n"
            "            pressed = false;\n"
            "            runtime.send(BrowserProtocol.MOUSE_UP + \" \" + button + \" \" + bx(x) + \" \" + by(y));\n"
            "            return true;\n"
            "        }\n"
            "        return super.mouseReleased(x, y, button);")
        t = t.replace(
            "    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {\n"
            "        if (pressed) {\n"
            "            runtime.send(BrowserProtocol.MOUSE_MOVE + \" \" + bx(event.x()) + \" \" + by(event.y()));\n"
            "            return true;\n"
            "        }\n"
            "        return super.mouseDragged(event, dx, dy);",
            "    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {\n"
            "        if (pressed) {\n"
            "            runtime.send(BrowserProtocol.MOUSE_MOVE + \" \" + bx(x) + \" \" + by(y));\n"
            "            return true;\n"
            "        }\n"
            "        return super.mouseDragged(x, y, button, dx, dy);")
        t = t.replace("public boolean keyPressed(KeyEvent event) {", "public boolean keyPressed(int key, int scancode, int modifiers) {")
        t = t.replace("public boolean keyReleased(KeyEvent event) {", "public boolean keyReleased(int key, int scancode, int modifiers) {")
        t = t.replace("if (event.key() == ESCAPE) {", "if (key == ESCAPE) {")
        t = t.replace("\" \" + event.key() + \" \" + event.modifiers() + \" \" + event.scancode()", "\" \" + key + \" \" + modifiers + \" \" + scancode")
        t = t.replace("public boolean charTyped(CharacterEvent event) {\n        runtime.send(BrowserProtocol.CHAR + \" \" + event.codepoint() + \" 0\");",
                      "public boolean charTyped(char character, int modifiers) {\n        runtime.send(BrowserProtocol.CHAR + \" \" + (int) character + \" 0\");")

    if name == "SelfTest.java":
        t = t.replace("MouseButtonEvent click = new MouseButtonEvent(button.getX() + 2, button.getY() + 2, new MouseButtonInfo(0, 0));\n"
                      "                        if (!button.mouseClicked(click, false))",
                      "if (!button.mouseClicked(button.getX() + 2, button.getY() + 2, 0))")

    if name == "DevScreenshot.java":
        t = t.replace("        MouseButtonEvent click = new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0));\n", "")
        t = t.replace("game.mouseClicked(click, false);", "game.mouseClicked(x, y, 0);")
        t = t.replace("game.mouseReleased(click);", "game.mouseReleased(x, y, 0);")
        t = t.replace(
            "        Screenshot.takeScreenshot(minecraft.getMainRenderTarget(), image -> {\n"
            "            try (NativeImage shot = image) {\n"
            "                shot.writeToFile(Path.of(target));\n"
            "                Constants.LOG.info(\"[screenshot] wrote {} {}\", target, detail);\n"
            "            } catch (IOException e) {\n"
            "                Constants.LOG.error(\"[screenshot] could not write {}\", target, e);\n"
            "            }\n"
            "            if (thenStop) minecraft.execute(minecraft::stop);\n"
            "        });",
            "        try (NativeImage shot = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {\n"
            "            shot.writeToFile(Path.of(target));\n"
            "            Constants.LOG.info(\"[screenshot] wrote {} {}\", target, detail);\n"
            "        } catch (IOException e) {\n"
            "            Constants.LOG.error(\"[screenshot] could not write {}\", target, e);\n"
            "        }\n"
            "        if (thenStop) minecraft.execute(minecraft::stop);")

    if name == "ArcadeScreen.java" or name == "GameScreen.java":
        t = t.replace("drawn with 1.21.11's GUI", "drawn with 1.20.6's GUI")
    return t


ERAS = {("1.21.11", "1.20.6"): to_1_20_6}


def set_property(text, name, value):
    return re.sub(rf"^{re.escape(name)}=.*$", f"{name}={value}", text, count=1, flags=re.M)


def main():
    source, target = sys.argv[1], sys.argv[2]
    rewrite = ERAS.get((source, target))
    if rewrite is None:
        sys.exit(f"no rewrites written down for {source} -> {target}")
    src = os.path.join(ROOT, "versions", source)
    dst = os.path.join(ROOT, "versions", target)
    if os.path.exists(dst):
        sys.exit(f"versions/{target} already exists")
    shutil.copytree(src, dst, ignore=shutil.ignore_patterns("build", ".gradle", "runs", "run", "out", "*.log"))

    loaders = versions.loaders(target)
    props = os.path.join(dst, "gradle.properties")
    text = open(props, encoding="utf-8").read()
    parts = versions.key(target)
    next_minor = f"1.{parts[1] + 1}" if parts[0] == 1 else f"{parts[0]}.{parts[1] + 1}"
    text = set_property(text, "minecraft_version", target)
    text = set_property(text, "minecraft_version_range", f"[{target}, {next_minor})")
    text = set_property(text, "java_version", loaders["java_version"])
    for key in ("neo_form_version", "fabric_loader_version", "neoforge_version", "forge_version",
                "forge_loader_version_range", "modmenu_version", "paper_api_version"):
        if loaders.get(key):
            text = set_property(text, key, loaders[key])
    open(props, "w", encoding="utf-8").write(text)

    # Read before opening for writing: open(..., "w") empties the file first.
    settings = os.path.join(dst, "settings.gradle")
    text = open(settings, encoding="utf-8").read()
    open(settings, "w", encoding="utf-8").write(re.sub(r"rootProject\.name = '[^']*'", f"rootProject.name = '{target}'", text))
    plugin_yml = os.path.join(dst, "folia", "src", "main", "resources", "plugin.yml")
    if os.path.isfile(plugin_yml):
        text = open(plugin_yml, encoding="utf-8").read()
        open(plugin_yml, "w", encoding="utf-8").write(re.sub(r"^api-version: .*$", f"api-version: '{target}'", text, flags=re.M))
    port_json = os.path.join(dst, "port.json")
    port = json.load(open(port_json, encoding="utf-8"))
    port["minecraft"] = target
    port["java"] = loaders["java_version"]
    json.dump(port, open(port_json, "w", encoding="utf-8"), indent=2)
    open(port_json, "a", encoding="utf-8").write("\n")

    changed = []
    for base, _, files in os.walk(dst):
        if os.sep + "build" + os.sep in base + os.sep:
            continue
        for f in files:
            if not f.endswith(".java"):
                continue
            path = os.path.join(base, f)
            before = open(path, encoding="utf-8").read()
            after = rewrite(path, before)
            if after != before:
                open(path, "w", encoding="utf-8").write(after)
                changed.append(os.path.relpath(path, dst))
    print(f"versions/{target} from versions/{source}: {len(changed)} source files rewritten")
    for c in changed:
        print("  ", c)


if __name__ == "__main__":
    main()
