package net.pr1nted.gegi.client;

/** The one question the CI compatibility mixin asks, kept apart from the self-test itself. */
public final class SelfTestHold {
    private SelfTestHold() {}

    /** True while GEGI's self-test is running in a world, so the test harness should wait. */
    public static boolean holdRuntimeTest() {
        return SelfTest.isRunningInWorld();
    }
}
