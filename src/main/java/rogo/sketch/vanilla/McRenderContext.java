package rogo.sketch.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import rogo.sketch.render.RenderContext;

public class McRenderContext extends RenderContext {
    private final PoseStack vanillaModelView;

    private Camera camera;
    private Frustum frustum;
    private LevelRenderer levelRenderer;

    public McRenderContext(LevelRenderer levelRenderer, PoseStack vanillaModelView, Matrix4f projectionMatrix, Camera camera, Frustum frustum, int renderTick, float partialTicks) {
        this.levelRenderer = levelRenderer;
        this.vanillaModelView = vanillaModelView;
        net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles cameraSetup = net.minecraftforge.client.ForgeHooksClient.onCameraSetup(Minecraft.getInstance().gameRenderer
                , camera, Minecraft.getInstance().getFrameTime());
        this.viewMatrix().identity();
        Vec3 cameraPos = camera.getPosition();
        this.viewMatrix().rotate(Axis.ZP.rotationDegrees(cameraSetup.getRoll()));
        this.viewMatrix().rotate(Axis.XP.rotationDegrees(camera.getXRot()));
        this.viewMatrix().rotate(Axis.YP.rotationDegrees(camera.getYRot() + 180.0F));
        this.viewMatrix().translate((float) -cameraPos.x, (float) -cameraPos.y, (float) -cameraPos.z);
        this.projectionMatrix().mul(projectionMatrix);
        this.modelMatrix().identity();
        this.camera = camera;
        this.frustum = frustum;
        this.renderTick = renderTick;
        this.partialTicks = partialTicks;
    }

    public PoseStack vanillaModelView() {
        return vanillaModelView;
    }

    public Camera camera() {
        return camera;
    }

    public void setCamera(Camera camera) {
        this.camera = camera;
    }

    public Frustum frustum() {
        return frustum;
    }

    public void setFrustum(Frustum frustum) {
        this.frustum = frustum;
    }

    public LevelRenderer levelRenderer() {
        return levelRenderer;
    }

    public void setLevelRenderer(LevelRenderer levelRenderer) {
        this.levelRenderer = levelRenderer;
    }
}