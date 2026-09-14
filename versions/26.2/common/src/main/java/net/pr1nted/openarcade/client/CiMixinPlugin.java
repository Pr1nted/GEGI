package net.pr1nted.openarcade.client;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Adds the CI-only mixin (McRuntimeTestMixin) only when the self-test is switched on.
 * Listing it unconditionally made every player's log warn that MC-Runtime-Test's class
 * could not be found. Reads the switch itself rather than through SelfTest: this runs
 * while mixins are being set up, before any Minecraft class may be loaded.
 */
public final class CiMixinPlugin implements IMixinConfigPlugin {

    private static final boolean SELF_TEST = "1".equals(System.getenv("OPENARCADE_SELFTEST"))
            || Boolean.getBoolean("openarcade.selftest");

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return SELF_TEST;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return SELF_TEST ? List.of("McRuntimeTestMixin") : List.of();
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
