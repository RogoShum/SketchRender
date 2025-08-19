package rogo.sketch.render.shader;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.apache.commons.compress.utils.Lists;
import rogo.sketch.SketchRender;
import rogo.sketch.api.ShaderCollector;
import rogo.sketch.feature.culling.CullingShaderInstance;

import java.io.IOException;
import java.util.List;

public class ShaderManager implements ResourceManagerReloadListener {
    private final List<ShaderCollector> shaders = Lists.newArrayList();
    public static ShaderInstance REMOVE_COLOR_SHADER;
    public static ShaderInstance CULL_TEST_SHADER;

    public static ComputeShaderProgram COPY_HIERARCHY_DEPTH_CS;

    public void onShaderLoad(ShaderCollector a) {
        shaders.add(a);
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        ShaderModifier.clear();
        ShaderModifier.loadAll(resourceManager);
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
            REMOVE_COLOR_SHADER = new CullingShaderInstance(resourceManager, new ResourceLocation(SketchRender.MOD_ID, "remove_color"), DefaultVertexFormat.POSITION_COLOR_TEX);
            CULL_TEST_SHADER = new CullingShaderInstance(resourceManager, new ResourceLocation(SketchRender.MOD_ID, "culling_test"), DefaultVertexFormat.POSITION);

            COPY_HIERARCHY_DEPTH_CS = new ComputeShaderProgram(resourceManager, new ResourceLocation(SketchRender.MOD_ID, "hierarchy_depth_buffer"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}