package rogo.sketch.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.PipelineConfig;

public class McGraphicsPipeline extends GraphicsPipeline<McRenderContext> {
    private final static McRenderContext EMPTY_CONTEXT = new McRenderContext(Minecraft.getInstance().levelRenderer, new PoseStack(), new Matrix4f(), Minecraft.getInstance().gameRenderer.getMainCamera(), new Frustum(new Matrix4f(), new Matrix4f()), 0, 0);

    public McGraphicsPipeline(PipelineConfig pipelineConfig) {
        super(pipelineConfig, EMPTY_CONTEXT);
    }
}