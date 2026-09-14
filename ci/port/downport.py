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
# no NativeImage.getPointer() (versions/1.20.6 adds NativeImageAccessor), and no
# JSpecify on the classpath.

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

    if name == "GameScreen.java":
        t = t.replace("MemoryUtil.memByteBuffer(texture.getPixels().getPointer(), width * height * 4)",
                      "MemoryUtil.memByteBuffer(((NativeImageAccessor) (Object) texture.getPixels()).openarcade$pixels(), width * height * 4)")
        t = t.replace("import net.pr1nted.openarcade.catalog.GameEntry;",
                      "import net.pr1nted.openarcade.catalog.GameEntry;\nimport net.pr1nted.openarcade.mixin.NativeImageAccessor;")

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


# ---- 1.20.6 -> 1.19.4 --------------------------------------------------------------
# No GuiGraphics (1.20): drawing is GuiComponent's static methods on a PoseStack, a
# texture is bound with RenderSystem.setShaderTexture before blit, and Screen.render
# does not draw the menu background by itself (1.20.2). No NeoForge exists yet.

def to_1_19_4(path, text):
    name = os.path.basename(path)
    t = text
    t = t.replace("import net.minecraft.client.gui.GuiGraphics;",
                  "import com.mojang.blaze3d.systems.RenderSystem;\nimport com.mojang.blaze3d.vertex.PoseStack;")
    t = sub(t, r"\bGuiGraphics graphics\b", "PoseStack graphics")
    t = t.replace("graphics.drawCenteredString(", "drawCenteredString(graphics, ")
    t = t.replace("graphics.drawString(", "drawString(graphics, ")
    t = t.replace("graphics.fill(", "fill(graphics, ")
    t = t.replace("graphics.enableScissor(", "enableScissor(")
    t = t.replace("graphics.disableScissor()", "disableScissor()")
    # graphics.blit(id, rest...) -> bind id, then GuiComponent.blit(pose, rest...)
    t = sub(t, r"(\n(\s*))graphics\.blit\(([^,]+),\s*", r"\1RenderSystem.setShaderTexture(0, \3);\1blit(graphics, ")
    # OptionsScreen overrides repositionElements only from 1.20; before, a resize re-runs init,
    # which places the button anyway, and an @Inject into the inherited method finds no target.
    if name == "OptionsScreenMixin.java":
        t = sub(t, r"\n    @Inject\(method = \"repositionElements\".*?\n    }\n", "\n", flags=re.S)
    # One scroll amount, the vertical one (the horizontal one came in 1.20.2).
    t = t.replace("public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {",
                  "public boolean mouseScrolled(double x, double y, double scrollY) {\n        double scrollX = 0; // 1.19.4 reports one wheel, the vertical one")
    t = t.replace("super.mouseScrolled(x, y, scrollX, scrollY)", "super.mouseScrolled(x, y, scrollY)")
    if name == "ArcadeScreen.java":
        t = t.replace("        super.render(graphics, mouseX, mouseY, a);\n",
                      "        this.renderBackground(graphics);\n        super.render(graphics, mouseX, mouseY, a);\n", 1)
    if name in ("ArcadeScreen.java", "GameScreen.java"):
        t = t.replace("drawn with 1.20.6's GUI", "drawn with 1.19.4's GUI")
    return t


# ---- 1.19.4 -> 1.18.2 --------------------------------------------------------------
# No Button.builder, Tooltip or EditBox hints (1.19.3), no Component.literal (1.19),
# no ClientPacketListener.sendCommand: a typed command reaches LocalPlayer.chat("/...")
# (1.19). No repositionElements or rebuildWidgets, widgets keep x and y as fields,
# NativeImage reads streams only, and scissoring is RenderSystem's, in window pixels.

def to_1_18_2(path, text):
    name = os.path.basename(path)
    t = text
    t = t.replace("Component.literal(", "new TextComponent(")
    t = t.replace("import net.minecraft.network.chat.Component;",
                  "import net.minecraft.network.chat.Component;\nimport net.minecraft.network.chat.TextComponent;")
    t = sub(t, r"\.withStyle\(s -> s\.withColor\(([A-Z_]+)\)\)", r".withStyle(s -> s.withColor(TextColor.fromRgb(\1 & 0xFFFFFF)))")
    if "TextColor.fromRgb" in t and "import net.minecraft.network.chat.TextColor;" not in t:
        t = t.replace("import net.minecraft.network.chat.Component;", "import net.minecraft.network.chat.Component;\nimport net.minecraft.network.chat.TextColor;", 1)
    # Button.builder(label, onPress).bounds(x, y, w, h)[.tooltip(...)].build() -> new Button(x, y, w, h, label, onPress)
    t = sub(t, r"Button\.builder\(((?:[^()]|\((?:[^()]|\([^()]*\))*\))*?),\s*((?:[^()]|\((?:[^()]|\([^()]*\))*\))*?)\)\s*\.bounds\(((?:[^()]|\([^()]*\))*)\)\s*(?:\.tooltip\((?:[^()]|\((?:[^()]|\([^()]*\))*\))*\)\s*)?\.build\(\)",
            r"new Button(\3, \1, \2)")
    t = t.replace("import net.minecraft.client.gui.components.Tooltip;\n", "")
    t = sub(t, r"(\w+)\.getX\(\)", r"\1.x")
    t = sub(t, r"(\w+)\.getY\(\)", r"\1.y")

    if name == "ClientPacketListenerMixin.java":
        t = """package net.pr1nted.openarcade.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.pr1nted.openarcade.client.ArcadeClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * /arcade without a command API. Before 1.19 a typed command is sent as chat starting
 * with "/", through LocalPlayer.chat, so ours is caught there and never reaches the
 * server. (The class keeps its name so every version lists the same mixins.)
 */
@Mixin(LocalPlayer.class)
public abstract class ClientPacketListenerMixin {

    @Inject(method = "chat", at = @At("HEAD"), cancellable = true)
    private void openarcade$interceptCommand(String message, CallbackInfo ci) {
        if (message.startsWith("/") && ArcadeClient.handleCommand(message.substring(1))) ci.cancel();
    }
}
"""
    if name == "SelfTest.java":
        t = t.replace('minecraft.player.connection.sendCommand("arcade");', 'minecraft.player.chat("/arcade");')
    if name == "ArcadeScreen.java":
        t = t.replace("this.rebuildWidgets();", "this.clearWidgets();\n                        this.init();")
        t = sub(t, r"\n(\s*)(\w+)\.setHint\(([^;]*)\);", r'\n\1\2.setSuggestion(\2.getValue().isEmpty() ? \3.getString() : "");')
        t = t.replace("search.setResponder(model::setQuery);",
                      "search.setResponder(text -> {\n            model.setQuery(text);\n            search.setSuggestion(text.isEmpty() ? Lang.string(\"openarcade.search\") : \"\");\n        });")
        t = t.replace("link.setResponder(model::setPasted);",
                      "link.setResponder(text -> {\n            model.setPasted(text);\n            link.setSuggestion(text.isEmpty() ? Lang.string(\"openarcade.link\") : \"\");\n        });")
        t = t.replace("        enableScissor(left, LIST_TOP, right, bottom);", "        scissor(left, LIST_TOP, right, bottom);")
        t = t.replace("        disableScissor();", "        RenderSystem.disableScissor();")
        t = t.replace("    private void drawThumbnail(",
                      "    /** GuiComponent had no scissor yet: RenderSystem's takes window pixels, from the bottom. */\n"
                      "    private void scissor(int x1, int y1, int x2, int y2) {\n"
                      "        double scale = this.minecraft.getWindow().getGuiScale();\n"
                      "        int windowHeight = this.minecraft.getWindow().getHeight();\n"
                      "        RenderSystem.enableScissor((int) (x1 * scale), (int) (windowHeight - y2 * scale),\n"
                      "                (int) ((x2 - x1) * scale), (int) ((y2 - y1) * scale));\n"
                      "    }\n\n"
                      "    private void drawThumbnail(", 1)
    if name == "Thumbnails.java":
        t = t.replace("NativeImage.read(png)", "NativeImage.read(new java.io.ByteArrayInputStream(png))")
    if name == "OpenArcadeForge.java":
        # Forge 40 calls it a config GUI: ConfigScreenHandler.ConfigScreenFactory came with 1.19.
        t = t.replace("import net.minecraftforge.client.ConfigScreenHandler;", "import net.minecraftforge.client.ConfigGuiHandler;")
        t = t.replace("ConfigScreenHandler.ConfigScreenFactory", "ConfigGuiHandler.ConfigGuiFactory")
        t = t.replace("// Forge 45 has no constructor injection", "// Forge 40 has no constructor injection")
    if name in ("ArcadeScreen.java", "GameScreen.java"):
        t = t.replace("drawn with 1.19.4's GUI", "drawn with 1.18.2's GUI")
    return t


# ---- 1.18.2 -> 1.17.1 --------------------------------------------------------------
# The client API the mod uses is 1.18.2's. Java 16 (the mixin levels follow it, see
# main), and Forge 37 still kept its client classes in net.minecraftforge.fmlclient.

def to_1_17_1(path, text):
    name = os.path.basename(path)
    t = text
    if name == "OpenArcadeForge.java":
        t = t.replace("import net.minecraftforge.client.ConfigGuiHandler;", "import net.minecraftforge.fmlclient.ConfigGuiHandler;")
        t = t.replace("// Forge 40 has no constructor injection", "// Forge 37 has no constructor injection")
    if name in ("ArcadeScreen.java", "GameScreen.java"):
        t = t.replace("drawn with 1.18.2's GUI", "drawn with 1.17.1's GUI")
    return t


# ---- 1.17.1 -> 1.16.5 --------------------------------------------------------------
# Java 8: no records, pattern-matching instanceof, arrow switches, List.of/Set.of,
# strip/isBlank, Path.of or Files.writeString. And 1.16.5's API: log4j instead of
# slf4j, addButton, the widget lists as fields, a texture is bound through the texture
# manager (no shader textures yet), a screenshot is sized by hand, and Forge 36's
# config screen is an ExtensionPoint.

def _java8_instanceof(t):
    t = re.sub(r"\n(\s*)if \(!\((\w+) instanceof ([A-Z][\w.]*) (\w+)\)\) return;",
               lambda m: f"\n{m.group(1)}if (!({m.group(2)} instanceof {m.group(3)})) return;\n{m.group(1)}{m.group(3)} {m.group(4)} = ({m.group(3)}) {m.group(2)};", t)
    t = re.sub(r"\n(\s*)if \((\w+) instanceof ([A-Z][\w.]*) (\w+) && \4 instanceof ([A-Z][\w.]*) (\w+)\) \{",
               lambda m: (f"\n{m.group(1)}if ({m.group(2)} instanceof {m.group(3)} && {m.group(2)} instanceof {m.group(5)}) {{"
                          f"\n{m.group(1)}    {m.group(3)} {m.group(4)} = ({m.group(3)}) {m.group(2)};"
                          f"\n{m.group(1)}    {m.group(5)} {m.group(6)} = ({m.group(5)}) (Object) {m.group(2)};"), t)

    def cond(m):
        ind, e, typ, v, rest = m.group(1), m.group(2), m.group(3), m.group(4), m.group(5)
        rest = re.sub(rf"\b{v}\.", f"(({typ}) {e}).", rest)
        return f"\n{ind}if ({e} instanceof {typ} && {rest}) {{\n{ind}    {typ} {v} = ({typ}) {e};"
    t = re.sub(r"\n(\s*)if \((\w+) instanceof ([A-Z][\w.]*) (\w+) && (.*?)\) \{", cond, t, flags=re.S)
    t = re.sub(r"\n(\s*)if \((\w+) instanceof ([A-Z][\w.]*) (\w+)\) ([^\n{]+;)",
               lambda m: (f"\n{m.group(1)}if ({m.group(2)} instanceof {m.group(3)}) {{"
                          f"\n{m.group(1)}    {m.group(3)} {m.group(4)} = ({m.group(3)}) {m.group(2)};"
                          f"\n{m.group(1)}    {m.group(5)}\n{m.group(1)}}}"), t)
    return t


def _java8_switch(t):
    t = re.sub(r"\n(\s*)case (\w+) -> \{ \}", r"\n\1case \2:\n\1    break;", t)
    t = re.sub(r"\n(\s*)case (\w+) -> \{(.*?)\n\1\}",
               lambda m: f"\n{m.group(1)}case {m.group(2)}: {{{m.group(3)}\n{m.group(1)}}}\n{m.group(1)}break;", t, flags=re.S)
    return t


def _java8_records(t):
    """A one-line record, `record Name(A a, B b) {}`, as the class Java 8 needs, with the same accessors."""
    def cls(m):
        ind, mods, name, params = m.group(1), m.group(2) or "", m.group(3), m.group(4)
        fields = [p.strip().rsplit(" ", 1) for p in params.split(",") if p.strip()]
        lines = [f"{ind}{mods}static final class {name} {{"]
        lines += [f"{ind}    private final {typ} {var};" for typ, var in fields]
        lines += ["", f"{ind}    {name}({params}) {{"]
        lines += [f"{ind}        this.{var} = {var};" for _, var in fields]
        lines += [f"{ind}    }}"]
        for typ, var in fields:
            lines += ["", f"{ind}    {typ} {var}() {{", f"{ind}        return {var};", f"{ind}    }}"]
        lines += [f"{ind}}}"]
        return "\n".join(lines)
    return re.sub(r"^([ \t]*)((?:private |public |protected )?)(?:static )?record (\w+)\(([^)]*)\) \{\}", cls, t, flags=re.M)


def to_1_16_5(path, text):
    name = os.path.basename(path)
    t = _java8_records(text)
    # Java 8
    t = t.replace('Set.of("arcade", "openarcade")',
                  'java.util.Collections.unmodifiableSet(new java.util.HashSet<>(java.util.Arrays.asList("arcade", "openarcade")))')
    t = t.replace("command.strip()", "command.trim()")
    t = t.replace("TARGET.isBlank()", "TARGET.trim().isEmpty()")
    t = t.replace("Path.of(", "java.nio.file.Paths.get(")
    t = t.replace('SELF_TEST ? List.of("McRuntimeTestMixin") : List.of()',
                  'SELF_TEST ? java.util.Collections.singletonList("McRuntimeTestMixin") : java.util.Collections.<String>emptyList()')
    t = re.sub(r'Files\.writeString\((.*?), ("[^"]*")\);', r"Files.write(\1, \2.getBytes(java.nio.charset.StandardCharsets.UTF_8));", t)
    t = t.replace("    record Ready(ResourceLocation id, int width, int height) {}",
                  """    static final class Ready {
        private final ResourceLocation id;
        private final int width;
        private final int height;

        Ready(ResourceLocation id, int width, int height) {
            this.id = id;
            this.width = width;
            this.height = height;
        }

        ResourceLocation id() {
            return id;
        }

        int width() {
            return width;
        }

        int height() {
            return height;
        }
    }""")
    t = _java8_instanceof(t)
    t = _java8_switch(t)
    # Gson 2.8.0 (Minecraft and Paper 1.16.5) has no JsonParser.parseReader.
    t = t.replace("JsonParser.parseReader(", "new JsonParser().parse(")

    # 1.16.5's API
    if name == "Constants.java":
        t = t.replace("import org.slf4j.Logger;", "import org.apache.logging.log4j.LogManager;\nimport org.apache.logging.log4j.Logger;")
        t = t.replace("import org.slf4j.LoggerFactory;\n", "")
        t = t.replace("LoggerFactory.getLogger(", "LogManager.getLogger(")
    t = t.replace("addRenderableWidget(", "addButton(")
    t = sub(t, r"\n(\s*)this\.clearWidgets\(\);", r"\n\1this.buttons.clear();\n\1this.children.clear();")
    t = sub(t, r"RenderSystem\.setShaderTexture\(0, ([^;]+)\);", r"this.minecraft.getTextureManager().bind(\1);")
    t = t.replace("Screenshot.takeScreenshot(minecraft.getMainRenderTarget())",
                  "Screenshot.takeScreenshot(minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight(), minecraft.getMainRenderTarget())")
    if name == "OpenArcadeForge.java":
        t = """package net.pr1nted.openarcade.forge;

import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.pr1nted.openarcade.Constants;
import net.pr1nted.openarcade.client.ArcadeClient;

/** Forge. The Config button in Forge's mod list opens the arcade. */
@Mod(Constants.MOD_ID)
public final class OpenArcadeForge {
    public OpenArcadeForge() {
        // Forge 36 registers a mod's config screen as an extension point.
        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.CONFIGGUIFACTORY,
                () -> (minecraft, parent) -> ArcadeClient.screen(parent));
        Constants.LOG.info("{} loaded on Forge", Constants.MOD_NAME);
    }
}
"""
    if name in ("ArcadeScreen.java", "GameScreen.java"):
        t = t.replace("drawn with 1.17.1's GUI", "drawn with 1.16.5's GUI")
    return t


ERAS = {("1.21.11", "1.20.6"): to_1_20_6, ("1.20.6", "1.19.4"): to_1_19_4, ("1.19.4", "1.18.2"): to_1_18_2,
        ("1.18.2", "1.17.1"): to_1_17_1, ("1.17.1", "1.16.5"): to_1_16_5}


# ---- 1.16.5 -> 1.15.2 --------------------------------------------------------------
# No PoseStack in GUI drawing (1.16), buttons, text boxes and confirm screens take String
# labels, no MutableComponent or TextColor, Font.substrByWidth. MC-Runtime-Test has no
# build for 1.15, so the self-test also makes its own world and quits (see SELF_DRIVEN).

SELF_DRIVEN = """
    /**
     * Where MC-Runtime-Test has no build (1.13 to 1.15), nothing joins a world for the
     * test: with OPENARCADE_SELFTEST_CREATE_WORLD=1 it creates a flat creative world from
     * the title screen, and quits the game once it has passed.
     */
    static final boolean CREATE_WORLD = "1".equals(System.getenv("OPENARCADE_SELFTEST_CREATE_WORLD"));
    private static boolean worldRequested;

    private static void createWorldIfAsked(Minecraft minecraft) {
        if (!CREATE_WORLD || worldRequested || minecraft.level != null) return;
        if (!(minecraft.screen instanceof net.minecraft.client.gui.screens.TitleScreen)) return;
        worldRequested = true;
        String id = "openarcade-selftest";
        if (minecraft.getLevelSource().levelExists(id)) minecraft.getLevelSource().deleteLevel(id);
        Constants.LOG.info("[self-test] creating a world");
        minecraft.selectLevel(id, "Open Arcade self-test", new net.minecraft.world.level.LevelSettings(
                0L, net.minecraft.world.level.GameType.CREATIVE, false, false, net.minecraft.world.level.LevelType.FLAT));
    }
"""


def to_1_15_2(path, text):
    name = os.path.basename(path)
    t = text
    t = t.replace("import com.mojang.blaze3d.vertex.PoseStack;\n", "")
    t = sub(t, r"render\(PoseStack graphics, ", "render(")
    t = t.replace("super.render(graphics, ", "super.render(")
    t = t.replace("this.renderBackground(graphics);", "this.renderBackground();")
    for fn in ("drawCenteredString", "drawString", "fill", "blit", "drawThumbnail"):
        t = t.replace(f"{fn}(graphics, ", f"{fn}(")
    t = t.replace("private void drawThumbnail(PoseStack graphics, ", "private void drawThumbnail(")
    t = t.replace("plainSubstrByWidth(", "substrByWidth(")
    # RenderSystem has no scissor before 1.16: clip with OpenGL directly.
    t = t.replace("import com.mojang.blaze3d.systems.RenderSystem;\n", "import org.lwjgl.opengl.GL11;\n")
    t = t.replace("RenderSystem.enableScissor(", "enableGlScissor(")
    t = t.replace("RenderSystem.disableScissor();", "GL11.glDisable(GL11.GL_SCISSOR_TEST);")
    t = t.replace("GuiComponent had no scissor yet: RenderSystem's takes window pixels, from the bottom.",
                  "RenderSystem has no scissor yet: OpenGL's takes window pixels, from the bottom.")
    if "enableGlScissor(" in t:
        t = t.replace("    private void scissor(int x1, int y1, int x2, int y2) {",
                      "    private static void enableGlScissor(int x, int y, int width, int height) {\n"
                      "        GL11.glEnable(GL11.GL_SCISSOR_TEST);\n"
                      "        GL11.glScissor(x, y, width, height);\n"
                      "    }\n\n"
                      "    private void scissor(int x1, int y1, int x2, int y2) {", 1)

    # String labels
    t = sub(t, r"new Button\(([^;]*?), new TextComponent\(([^;]*?)\), ", r"new Button(\1, \2, ")
    t = sub(t, r"new Button\(([^;]*?),(\s*)Lang\.text\(", r"new Button(\1,\2Lang.string(")
    t = t.replace("CommonComponents.GUI_DONE", 'net.minecraft.client.resources.language.I18n.get("gui.done")')
    t = t.replace("import net.minecraft.network.chat.CommonComponents;\n", "")
    # Mod Menu before 2.0 (1.16 and older) kept its API under io.github.prospector.
    t = t.replace("import com.terraformersmc.modmenu.api.", "import io.github.prospector.modmenu.api.")
    t = sub(t, r"new EditBox\(([^;]*?), Lang\.text\(", r"new EditBox(\1, Lang.string(")
    t = sub(t, r"Lang\.text\((\"[^\"]*\")\)\.withStyle\(s -> s\.withColor\(TextColor\.fromRgb\([^)]*\)\)\)\.getString\(\)", r"Lang.string(\1)")
    t = t.replace("import net.minecraft.network.chat.TextColor;\n", "")
    t = t.replace("drawCenteredString(this.font, this.title, ", "drawCenteredString(this.font, this.title.getString(), ")
    if name == "Lang.java":
        t = t.replace("import net.minecraft.network.chat.MutableComponent;\n", "")
        t = t.replace("public static MutableComponent text(", "public static Component text(")
    if name == "ArcadeClient.java":
        # ConfirmScreen's buttons are Strings here
        t = t.replace('Lang.text("openarcade.consent.download"),', 'Lang.string("openarcade.consent.download"),')
        t = t.replace('Lang.text("openarcade.consent.browser")', 'Lang.string("openarcade.consent.browser")')
    if name == "SelfTest.java":
        t = t.replace("    static void tick(Minecraft minecraft) {\n        if (!ENABLED || step == Step.DONE) return;",
                      SELF_DRIVEN + "\n    static void tick(Minecraft minecraft) {\n        if (!ENABLED || step == Step.DONE) return;\n        createWorldIfAsked(minecraft);")
        t = t.replace('Constants.LOG.info("OPEN ARCADE SELF-TEST PASSED");\n                        step = Step.DONE;',
                      'Constants.LOG.info("OPEN ARCADE SELF-TEST PASSED");\n                        step = Step.DONE;\n'
                      '                        if (CREATE_WORLD) minecraft.stop();')
    if name in ("ArcadeScreen.java", "GameScreen.java"):
        t = t.replace("drawn with 1.16.5's GUI", "drawn with 1.15.2's GUI")
    return t


ERAS[("1.16.5", "1.15.2")] = to_1_15_2


# ---- 1.15.2 -> 1.14.4 --------------------------------------------------------------
# The API the mod uses is 1.15.2's (which already clips with OpenGL's scissor).
# Mod Menu 1.7 is published as io.github.prospector.

MODMENU_1_7 = """package net.pr1nted.openarcade.fabric;

import io.github.prospector.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.screens.Screen;
import net.pr1nted.openarcade.Constants;
import net.pr1nted.openarcade.client.ArcadeClient;

import java.util.function.Function;

/** Mod Menu's Config button opens the arcade. Mod Menu 1.7 names the mod and returns a plain factory. */
public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public String getModId() {
        return Constants.MOD_ID;
    }

    @Override
    public Function<Screen, ? extends Screen> getConfigScreenFactory() {
        return ArcadeClient::screen;
    }
}
"""


# Forge 28 (1.14.4) ships no Mixin, so on Forge the Options button, /arcade, the tick
# and the pixel address come from Forge's events and reflection. Fabric keeps its mixins.
PIXELS_1_14_4 = """package net.pr1nted.openarcade.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.pr1nted.openarcade.mixin.NativeImageAccessor;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Where a NativeImage keeps its pixels. Through the mixin accessor where Mixin runs
 * (Fabric), and by reflection where it does not: Forge 28 ships no Mixin. A
 * NativeImage has exactly one long instance field, the address.
 */
final class Pixels {
    private Pixels() {}

    private static Field field;

    static long address(NativeImage image) {
        Object o = image;
        if (o instanceof NativeImageAccessor) return ((NativeImageAccessor) o).openarcade$pixels();
        try {
            if (field == null) {
                for (Field f : NativeImage.class.getDeclaredFields()) {
                    if (f.getType() == long.class && !Modifier.isStatic(f.getModifiers())) {
                        f.setAccessible(true);
                        field = f;
                        break;
                    }
                }
                if (field == null) throw new IllegalStateException("NativeImage has no pixel address field");
            }
            return field.getLong(image);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("cannot read NativeImage's pixel address", e);
        }
    }
}
"""

FORGE_EVENTS_1_14_4 = """package net.pr1nted.openarcade.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.pr1nted.openarcade.client.ArcadeClient;

/**
 * Forge 28 ships no Mixin, so on Forge the Options button, /arcade and the tick come
 * from Forge's own events instead of the mixins Fabric uses. Nothing extra to install.
 */
public final class ForgeEvents {

    @SubscribeEvent
    public void onScreenInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (event.getGui() instanceof OptionsScreen) {
            event.addWidget(ArcadeClient.optionsButtonFor(event.getGui()));
        }
    }

    @SubscribeEvent
    public void onChat(ClientChatEvent event) {
        String message = event.getMessage();
        if (message.startsWith("/") && ArcadeClient.handleCommand(message.substring(1))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) ArcadeClient.tick(Minecraft.getInstance());
    }
}
"""

OPTIONS_BUTTON_1_14_4 = """    /** The Open Arcade button for an Options screen, remembered for the self-test. */
    public static net.minecraft.client.gui.components.Button optionsButtonFor(Screen options) {
        optionsButton = new net.minecraft.client.gui.components.Button(options.width - 108, 8, 100, 20,
                Lang.string("openarcade.button"), b -> open(options));
        return optionsButton;
    }

    /** The button optionsButtonFor made last, or null. */
    public static net.minecraft.client.gui.components.Button lastOptionsButton() {
        return optionsButton;
    }

"""


def _forge_events_1_14_4(name, t):
    if name == "GameScreen.java":
        t = t.replace("import net.pr1nted.openarcade.mixin.NativeImageAccessor;\n", "")
        t = t.replace("((NativeImageAccessor) (Object) texture.getPixels()).openarcade$pixels()", "Pixels.address(texture.getPixels())")
    elif name == "ArcadeClient.java":
        t = t.replace("    private static Catalog catalog;\n",
                      "    private static Catalog catalog;\n    private static net.minecraft.client.gui.components.Button optionsButton;\n", 1)
        t = t.replace("    public static void open(Screen parent) {", OPTIONS_BUTTON_1_14_4 + "    public static void open(Screen parent) {", 1)
    elif name == "OptionsScreenMixin.java":
        t = re.sub(r"        Screen self = this;\n        openarcade\$button = this\.addButton\(new Button\(this\.width - 108, 8, 100, 20, *\n"
                   r" *Lang\.string\(\"openarcade\.button\"\), button -> ArcadeClient\.open\(self\)\)\);",
                   "        openarcade$button = this.addButton(ArcadeClient.optionsButtonFor(this));", t)
    elif name == "SelfTest.java":
        t = t.replace('                    minecraft.player.chat("/arcade");\n',
                      "                    // Through a chat screen, as a player types it: that is where Forge fires ClientChatEvent,\n"
                      "                    // and it reaches LocalPlayer.chat, where Fabric's mixin listens.\n"
                      "                    net.minecraft.client.gui.screens.ChatScreen chat = new net.minecraft.client.gui.screens.ChatScreen(\"\");\n"
                      "                    chat.init(minecraft, minecraft.window.getGuiScaledWidth(), minecraft.window.getGuiScaledHeight());\n"
                      '                    chat.sendMessage("/arcade", false);\n')
        t = t.replace("if (screen instanceof OptionsScreen && screen instanceof OptionsButtonHolder) {",
                      "if (screen instanceof OptionsScreen && ArcadeClient.lastOptionsButton() != null) {")
        t = t.replace("                        OptionsButtonHolder holder = (OptionsButtonHolder) (Object) screen;\n"
                      "                        Button button = holder.openarcade$optionsButton();\n"
                      '                        if (button == null || !options.children().contains(button)) fail("no Open Arcade button in Options");\n',
                      "                        Button button = ArcadeClient.lastOptionsButton();\n"
                      '                        if (!options.children().contains(button)) fail("no Open Arcade button in Options");\n')
    elif name == "OpenArcadeForge.java":
        t = t.replace("import net.minecraftforge.fml.ExtensionPoint;", "import net.minecraftforge.common.MinecraftForge;\nimport net.minecraftforge.fml.ExtensionPoint;", 1)
        t = t.replace('        Constants.LOG.info("{} loaded on Forge", Constants.MOD_NAME);',
                      '        MinecraftForge.EVENT_BUS.register(new ForgeEvents());\n        Constants.LOG.info("{} loaded on Forge", Constants.MOD_NAME);', 1)
    return t


def to_1_14_4(path, text):
    name = os.path.basename(path)
    t = text
    # No Minecraft.getWindow() yet: the window is the public field.
    t = t.replace(".getWindow()", ".window")
    if name == "ModMenuIntegration.java":
        # Mod Menu 1.7: getModId() is required, and the factory is a plain Function.
        t = MODMENU_1_7
    # No RenderSystem at all in 1.14.4: drop an import that nothing uses any more.
    if "RenderSystem." not in t:
        t = t.replace("import com.mojang.blaze3d.systems.RenderSystem;\n", "")
    if name in ("ArcadeScreen.java", "GameScreen.java"):
        t = t.replace("drawn with 1.15.2's GUI", "drawn with 1.14.4's GUI")
    return _forge_events_1_14_4(name, t)


ERAS[("1.15.2", "1.14.4")] = to_1_14_4


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
    # NeoForge 20.6's loader is javafml 3; from 1.21 it is 4.
    if versions.key(target) < [1, 21]:
        text = set_property(text, "neoforge_loader_version_range", "[2,)")
    open(props, "w", encoding="utf-8").write(text)

    # Read before opening for writing: open(..., "w") empties the file first.
    settings = os.path.join(dst, "settings.gradle")
    text = open(settings, encoding="utf-8").read()
    open(settings, "w", encoding="utf-8").write(re.sub(r"rootProject\.name = '[^']*'", f"rootProject.name = '{target}'", text))
    plugin_yml = os.path.join(dst, "folia", "src", "main", "resources", "plugin.yml")
    if os.path.isfile(plugin_yml):
        text = open(plugin_yml, encoding="utf-8").read()
        # Bukkit took a patch version in api-version only from 1.20.5; before, "1.19" and not "1.19.4".
        api = target if versions.key(target) >= [1, 20, 5] else ".".join(target.split(".")[:2])
        open(plugin_yml, "w", encoding="utf-8").write(re.sub(r"^api-version: .*$", f"api-version: '{api}'", text, flags=re.M))
    # Paper's API was com.destroystokyo.paper before 1.17.
    group = loaders.get("paper_api_group")
    folia_build = os.path.join(dst, "folia", "build.gradle")
    if group and os.path.isfile(folia_build):
        text = open(folia_build, encoding="utf-8").read()
        text = re.sub(r'"[\w.]+:paper-api:\$\{paper_api_version\}"', '"${paper_api_group}:paper-api:${paper_api_version}"', text)
        open(folia_build, "w", encoding="utf-8").write(text)
        text = open(props, encoding="utf-8").read()
        if re.search(r"^paper_api_group=", text, flags=re.M):
            text = set_property(text, "paper_api_group", group)
        else:
            text = text.rstrip("\n") + f"\npaper_api_group={group}\n"
        open(props, "w", encoding="utf-8").write(text)
    port_json = os.path.join(dst, "port.json")
    port = json.load(open(port_json, encoding="utf-8"))
    port["minecraft"] = target
    port["java"] = loaders["java_version"]
    # MC-Runtime-Test 4.5.1 has builds for 1.12.2 and 1.16.5 on, none for 1.13 to 1.15:
    # there the client runs in CI's self-driven job and the mod makes its own world.
    if [1, 13] <= versions.key(target) < [1, 16]:
        for client in port.get("clients", []):
            client["mcrt"] = "none"
    if not loaders.get("paper_api_version") and os.path.isdir(os.path.join(dst, "folia")):
        # PaperMC publishes no plugin API this old (the plugin uses Paper's Adventure API),
        # so this version has the client mod only.
        port["folia"] = False
        shutil.rmtree(os.path.join(dst, "folia"))
        text = open(settings, encoding="utf-8").read()
        open(settings, "w", encoding="utf-8").write(text.replace("include('folia')\n", ""))
    json.dump(port, open(port_json, "w", encoding="utf-8"), indent=2)
    open(port_json, "a", encoding="utf-8").write("\n")

    # Mixin configs. Up to 1.20.6 the loaders ship Mixin 0.8.5 (Forge 50 and NeoForge 20.6
    # included, on Java 21), which knows no level above JAVA_17 and refuses to start the
    # game on one it does not know. So the shared and Forge configs ask for the build's
    # Java, at most 17, and the CI-only config, whose one mixin needs nothing new, JAVA_8.
    if versions.key(target) <= [1, 20, 6]:
        level = f"JAVA_{min(int(loaders['java_version']), 17)}"
        for rel, level in (("common/src/main/resources/openarcade.mixins.json", level),
                           ("forge/src/main/resources/openarcade.forge.mixins.json", level),
                           ("common/src/main/resources/openarcade.ci.mixins.json", "JAVA_8")):
            path = os.path.join(dst, rel)
            if os.path.isfile(path):
                text = open(path, encoding="utf-8").read()
                open(path, "w", encoding="utf-8").write(
                    re.sub(r'"compatibilityLevel":\s*"[^"]*"', f'"compatibilityLevel": "{level}"', text))

    # Mod Menu 1.7 and older (Minecraft 1.14) is published as io.github.prospector:modmenu.
    if versions.key(target) < [1, 15]:
        build_file = os.path.join(dst, "build.gradle")
        if os.path.isfile(build_file):
            text = open(build_file, encoding="utf-8").read()
            open(build_file, "w", encoding="utf-8").write(text.replace('"com.terraformersmc:modmenu:', '"io.github.prospector:modmenu:')
                                                          .replace("includeGroup 'com.terraformersmc'", "includeGroup 'io.github.prospector'")
                                                          .replace("        mixinConfig 'openarcade.forge.mixins.json', 'openarcade.ci.mixins.json'\n",
                                                                   "        // No mixin configs: Forge 28 ships no Mixin, and with a Mixin mod installed they would\n"
                                                                   "        // add a second button and a second tick next to the Forge events that do the work.\n"))
        # Forge 28 has no Mixin: the files its events and reflection need.
        for rel, body in (("common/src/main/java/net/pr1nted/openarcade/client/Pixels.java", PIXELS_1_14_4),
                          ("forge/src/main/java/net/pr1nted/openarcade/forge/ForgeEvents.java", FORGE_EVENTS_1_14_4)):
            if os.path.isdir(os.path.join(dst, os.path.dirname(rel))):
                open(os.path.join(dst, rel), "w", encoding="utf-8").write(body)

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
