package rogo.sketch.render.shader;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.apache.commons.compress.utils.Lists;
import rogo.sketch.SketchRender;
import rogo.sketch.api.ShaderCollector;

import java.io.IOException;
import java.util.List;

public class ShaderManager implements ResourceManagerReloadListener {
    private final List<ShaderCollector> shaders = Lists.newArrayList();
    public static ShaderInstance REMOVE_COLOR_SHADER;

    public void onShaderLoad(ShaderCollector a) {
        shaders.add(a);
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        resetShader(resourceManager);
    }

    public void resetShader(ResourceManager resourceManager) {
        for (ShaderCollector autoCloseable : shaders) {
            try {
                autoCloseable.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        shaders.clear();

        try {
            REMOVE_COLOR_SHADER = new ShaderInstance(resourceManager, new ResourceLocation(SketchRender.MOD_ID, "remove_color"), DefaultVertexFormat.POSITION_COLOR_TEX);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}