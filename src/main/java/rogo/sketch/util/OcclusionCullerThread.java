package rogo.sketch.util;

import net.minecraft.client.Minecraft;
import rogo.sketch.SketchRender;
import rogo.sketch.Config;
import rogo.sketch.compat.sodium.SodiumSectionAsyncUtil;

public class OcclusionCullerThread extends Thread {
    public static OcclusionCullerThread INSTANCE;
    private boolean finished = false;

    public OcclusionCullerThread() {
        if (INSTANCE != null) {
            INSTANCE.finished = true;
        }
        INSTANCE = this;
    }

    public static void notifyUpdate() {
        if (Config.getAsyncChunkRebuild() && SketchRender.hasSodium()) {
            SodiumSectionAsyncUtil.notifyUpdate();
        }
    }

    @Override
    public void run() {
        while (!finished) {
            try {
                if (Config.getAsyncChunkRebuild() || SketchRender.hasSodium()) {
                    SodiumSectionAsyncUtil.asyncSearchRebuildSection();
                }

                if (Minecraft.getInstance().level == null) {
                    finished = true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
