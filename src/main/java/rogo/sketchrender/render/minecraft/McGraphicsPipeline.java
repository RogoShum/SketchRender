package rogo.sketchrender.render.minecraft;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;
import rogo.sketchrender.render.sketch.GraphicsPipeline;

public class McGraphicsPipeline extends GraphicsPipeline<McRenderContext> {
    private final static McRenderContext EMPTY_CONTEXT = new McRenderContext(Minecraft.getInstance().levelRenderer, new PoseStack(), new Matrix4f(), Minecraft.getInstance().gameRenderer.getMainCamera(), new Frustum(new Matrix4f(), new Matrix4f()), 0, 0);

    public McGraphicsPipeline(boolean throwOnSortFail) {
        super(throwOnSortFail, EMPTY_CONTEXT);
    }
}