package rogo.sketch.compat.sodium;

import me.jellysquid.mods.sodium.client.SodiumClientMod;

public class SodiumImplOptions {

    public static boolean canApplyTranslucencySorting() {
        return SodiumClientMod.canApplyTranslucencySorting();
    }

    public static boolean useBlockFaceCulling() {
        return SodiumClientMod.options().performance.useBlockFaceCulling;
    }

    public static boolean useNoErrorGLContext() {
        return SodiumClientMod.options().performance.useNoErrorGLContext;
    }
}