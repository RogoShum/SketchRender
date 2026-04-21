package rogo.sketch.feature.culling;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import rogo.sketch.SketchRender;
import rogo.sketch.compat.sodium.SodiumSectionAsyncUtil;
import rogo.sketch.util.ShaderPackLoader;
import rogo.sketch.vanilla.VanillaShaderPackLoader;

/**
 * Host-owned shader/shadow capability state used by Minecraft culling seams.
 */
public final class MinecraftShaderCapabilityService {
    private static final MinecraftShaderCapabilityService INSTANCE = new MinecraftShaderCapabilityService();

    private volatile ShaderPackLoader shaderLoader;
    private volatile boolean usingShaderPack;
    private volatile boolean runtimeStateResetPending;

    private MinecraftShaderCapabilityService() {
    }

    public static MinecraftShaderCapabilityService getInstance() {
        return INSTANCE;
    }

    public void init() {
        if (SketchRender.hasIris()) {
            try {
                shaderLoader = Class.forName("rogo.sketch.util.IrisLoaderImpl")
                        .asSubclass(ShaderPackLoader.class)
                        .newInstance();
            } catch (InstantiationException | IllegalAccessException | ClassNotFoundException ex) {
                throw new RuntimeException(ex);
            }
        } else {
            shaderLoader = new VanillaShaderPackLoader();
        }
    }

    public void onWorldReload(Level world) {
        if (world != Minecraft.getInstance().level) {
            cleanup();
        }
    }

    public void cleanup() {
        if (SketchRender.hasSodium()) {
            SodiumSectionAsyncUtil.pauseAsync();
        }
    }

    public void checkShader() {
        ShaderPackLoader loader = shaderLoader;
        if (loader == null) {
            return;
        }
        boolean clear = false;
        if (loader.enabledShader() && !usingShaderPack) {
            usingShaderPack = true;
            clear = true;
        } else if (!loader.enabledShader() && usingShaderPack) {
            usingShaderPack = false;
            clear = true;
        }
        if (clear) {
            runtimeStateResetPending = true;
        }
    }

    public void flushPendingRuntimeReset() {
        if (runtimeStateResetPending) {
            cleanup();
            runtimeStateResetPending = false;
        }
    }

    public boolean renderingShadowPass() {
        ShaderPackLoader loader = shaderLoader;
        return loader != null && loader.renderingShadowPass();
    }

    public boolean enabledShader() {
        ShaderPackLoader loader = shaderLoader;
        return loader != null && loader.enabledShader();
    }

    public boolean entityCullingHostActive() {
        return !renderingShadowPass();
    }

    public boolean terrainCullingHostActive() {
        return !renderingShadowPass();
    }

    public ShaderPackLoader shaderLoader() {
        return shaderLoader;
    }
}
