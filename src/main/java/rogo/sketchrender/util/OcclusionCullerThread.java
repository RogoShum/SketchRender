package rogo.sketchrender.util;

import net.minecraft.client.Minecraft;
import rogo.sketchrender.SketchRender;
import rogo.sketchrender.api.Config;

public class OcclusionCullerThread extends Thread {
    public static OcclusionCullerThread INSTANCE;
    private boolean finished = false;

    public OcclusionCullerThread() {
        if (INSTANCE != null) {
            INSTANCE.finished = true;
        }
        INSTANCE = this;
    }

    public static void shouldUpdate() {
        if (Config.getAsyncChunkRebuild() && SketchRender.hasSodium()) {
            SodiumSectionAsyncUtil.shouldUpdate();
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
