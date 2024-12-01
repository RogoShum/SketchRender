package rogo.sketchrender.util;

import net.minecraft.client.Minecraft;
import rogo.sketchrender.api.Config;
import rogo.sketchrender.api.CullingStateManager;
import rogo.sketchrender.api.ModLoader;

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
        if (Config.getAsyncChunkRebuild()) {
            if(ModLoader.hasSodium()) {
                SodiumSectionAsyncUtil.shouldUpdate();
            } else if (VanillaAsyncUtil.injectedAsyncMixin) {
                //VanillaAsyncUtil.shouldUpdate();
            }
        }
    }

    @Override
    public void run() {
        while (!finished) {
            try {
                if (CullingStateManager.CHUNK_CULLING_MAP != null && CullingStateManager.CHUNK_CULLING_MAP.isDone()) {
                    if (Config.getAsyncChunkRebuild()) {
                        if (ModLoader.hasSodium()) {
                            SodiumSectionAsyncUtil.asyncSearchRebuildSection();
                        } else if(VanillaAsyncUtil.injectedAsyncMixin) {
                            //VanillaAsyncUtil.asyncSearchRebuildSection();
                        }
                    }
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
