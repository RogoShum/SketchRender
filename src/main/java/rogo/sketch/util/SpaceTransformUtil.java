package rogo.sketch.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import rogo.sketch.feature.culling.CullingStateManager;

public class SpaceTransformUtil {

    public static Vector3f getScreenSpaceDirection(Vector2f screenSpace, boolean scaledWindow) {
        return new Vector3f(screenSpaceToWorldSpace(new Vector3f(screenSpace.x, screenSpace.y, 1), scaledWindow)
                .sub(screenSpaceToWorldSpace(screenSpace, scaledWindow))).normalize();
    }

    public static Vector3f screenSpaceToWorldSpace(Vector2f screenSpace, boolean scaledWindow) {
        return screenSpaceToWorldSpace(new Vector3f(screenSpace.x, screenSpace.y, -0.2f), scaledWindow);
    }

    public static Vector3f screenSpaceToWorldSpace(Vector3f screenSpace, boolean scaledWindow) {
        float screenWidth = Minecraft.getInstance().getWindow().getWidth();
        float screenHeight = Minecraft.getInstance().getWindow().getHeight();

        if (scaledWindow) {
            screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
            screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        }

        float normalizedX = (screenSpace.x / screenWidth);
        float normalizedY = 1.0f - (screenSpace.y / screenHeight);

        Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Matrix4f viewMatrix = new Matrix4f(CullingStateManager.VIEW_MATRIX);
        viewMatrix.translate((float) -cameraPos.x, (float) -cameraPos.y, (float) -cameraPos.z);
        Matrix4f projMatrix = new Matrix4f(CullingStateManager.PROJECTION_MATRIX);
        Matrix4f invViewProjMatrix = new Matrix4f(projMatrix.mul(viewMatrix).invert());
        Vector4f ndcWorld = invViewProjMatrix.transform(new Vector4f(normalizedX * 2.0f - 1.0f, normalizedY * 2.0f - 1.0f, screenSpace.z, 1.0f));

        return new Vector3f(ndcWorld.x / ndcWorld.w, ndcWorld.y / ndcWorld.w, ndcWorld.z / ndcWorld.w);
    }

    public static Vector3f worldSpaceToScreenSpace(Vector3f worldSpace, boolean scaledWindow) {
        Vector4f screenSpace = worldSpaceToNdcSpace(worldSpace);
        screenSpace.x = (float) ((screenSpace.x + 1.0) / 2.0);
        screenSpace.y = 1.0f - (float) ((screenSpace.y + 1.0) / 2.0);
        screenSpace.z = (float) ((screenSpace.z + 1.0) / 2.0);

        float screenWidth = Minecraft.getInstance().getWindow().getWidth();
        float screenHeight = Minecraft.getInstance().getWindow().getHeight();

        if (scaledWindow) {
            screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
            screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        }

        float far = Minecraft.getInstance().gameRenderer.getDepthFar() * 0.5f;
        return new Vector3f(screenWidth * screenSpace.x, screenHeight * screenSpace.y, screenSpace.z * far);
    }

    public static Vector4f worldSpaceToNdcSpace(Vector3f worldSpace) {
        Matrix4f viewMatrix = new Matrix4f(CullingStateManager.VIEW_MATRIX);
        Matrix4f projMatrix = new Matrix4f(CullingStateManager.PROJECTION_MATRIX);

        Vector4f ndcWorld = viewMatrix.transform(
                new Vector4f(worldSpace.x
                        , worldSpace.y
                        , worldSpace.z
                        , 1.0f));
        ndcWorld = projMatrix.transform(ndcWorld);

        if (ndcWorld.w == 0.0) {
            throw new RuntimeException("Not supposed to be happened");
        }

        return new Vector4f(ndcWorld.x / ndcWorld.w, ndcWorld.y / ndcWorld.w, ndcWorld.z / ndcWorld.w, ndcWorld.w);
    }
}
