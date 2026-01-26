package rogo.sketch.vanilla;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.apache.commons.compress.utils.Lists;
import rogo.sketch.SketchRender;

import java.io.IOException;
import java.util.List;

public class ShaderManager implements ResourceManagerReloadListener {
    private final List<ShaderInstance> shaders = Lists.newArrayList();
    public static ShaderInstance REMOVE_COLOR_SHADER;

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        resetShader(resourceManager);
    }

    public void resetShader(ResourceManager resourceManager) {
        for (ShaderInstance autoCloseable : shaders) {
            try {
                autoCloseable.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        shaders.clear();

        try {
            REMOVE_COLOR_SHADER = loadShader(resourceManager, new ResourceLocation(SketchRender.MOD_ID, "remove_color"), DefaultVertexFormat.POSITION_COLOR_TEX);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private ShaderInstance loadShader(ResourceProvider resourceProvider, ResourceLocation shaderLocation, VertexFormat vertexFormat) throws IOException {
        ShaderInstance instance = new ShaderInstance(resourceProvider, shaderLocation, vertexFormat);
        shaders.add(instance);
        return instance;
    }
}