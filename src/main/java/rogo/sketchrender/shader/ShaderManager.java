package rogo.sketchrender.shader;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.apache.commons.compress.utils.Lists;
import rogo.sketchrender.SketchRender;
import rogo.sketchrender.api.ShaderCollector;
import rogo.sketchrender.culling.CullingShaderInstance;

import java.io.IOException;
import java.util.List;

public class ShaderManager implements ResourceManagerReloadListener {
    private final List<ShaderCollector> shaders = Lists.newArrayList();
    public static ShaderInstance CHUNK_CULLING_SHADER;
    public static ShaderInstance COPY_DEPTH_SHADER;
    public static ShaderInstance REMOVE_COLOR_SHADER;
    public static ShaderInstance INSTANCED_ENTITY_CULLING_SHADER;
    public static ShaderInstance CULL_TEST_SHADER;

    public static ComputeShader COPY_DEPTH_CS;
    public static ComputeShader COLLECT_CHUNK_PASS_CS;

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
            CHUNK_CULLING_SHADER = new CullingShaderInstance(resourceManager, new ResourceLocation(SketchRender.MOD_ID, "chunk_culling"), DefaultVertexFormat.POSITION);
            INSTANCED_ENTITY_CULLING_SHADER = new CullingShaderInstance(resourceManager, new ResourceLocation(SketchRender.MOD_ID, "instanced_entity_culling"), DefaultVertexFormat.POSITION);
            COPY_DEPTH_SHADER = new CullingShaderInstance(resourceManager, new ResourceLocation(SketchRender.MOD_ID, "copy_depth"), DefaultVertexFormat.POSITION);
            REMOVE_COLOR_SHADER = new CullingShaderInstance(resourceManager, new ResourceLocation(SketchRender.MOD_ID, "remove_color"), DefaultVertexFormat.POSITION_COLOR_TEX);
            CULL_TEST_SHADER = new CullingShaderInstance(resourceManager, new ResourceLocation(SketchRender.MOD_ID, "culling_test"), DefaultVertexFormat.POSITION);

            COPY_DEPTH_CS = new ComputeShader(resourceManager, new ResourceLocation(SketchRender.MOD_ID, "copy_depth"));
            COLLECT_CHUNK_PASS_CS = new ComputeShader(resourceManager, new ResourceLocation(SketchRender.MOD_ID, "collect_chunk_pass"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
