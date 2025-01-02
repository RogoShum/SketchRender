package rogo.sketchrender.util;

import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;
import rogo.sketchrender.compat.sodium.MeshUniform;
import rogo.sketchrender.culling.CullingStateManager;

public class TestChunkScreenDepth {
    public static float near = 0.05f;
    public static float far = 16.0f;

    public static float LinearizeDepth(float depth) {
        float z = depth * 2.0f - 1.0f;
        return (near * far) / (far + near - z * (far - near));
    }

    public static Vec3 blockToChunk(Vec3 blockPos) {
        return new Vec3(Math.floor(blockPos.x / 16.0), Math.floor(blockPos.y / 16.0), Math.floor(blockPos.z / 16.0));
    }

    public static Vec3 worldToScreenSpace(Vec3 pos) {
        Vector4f cameraSpace = CullingStateManager.PROJECTION_MATRIX.transform(
                CullingStateManager.VIEW_MATRIX.transform(
                        new Vector4f((float) pos.x, (float) pos.y, (float) pos.z, 1),
                        new Vector4f()
                ),
                new Vector4f()
        );
        Vec3 ndc;

        float w = cameraSpace.w;
        if (w <= 0.0) {
            ndc = new Vec3(
                    cameraSpace.x / -w,
                    cameraSpace.y / -w,
                    cameraSpace.z / w
            );
        } else {
            ndc = new Vec3(
                    cameraSpace.x / w,
                    cameraSpace.y / w,
                    cameraSpace.z / w
            );
        }

        if (Math.abs(ndc.x) > 1.0 || Math.abs(ndc.y) > 1.0) {
            float t = (-0.05f - cameraSpace.w) / (cameraSpace.w - 0.0f);
            Vec3 cameraDir = new Vec3(CullingStateManager.CAMERA.getLookVector());
            Vec3 cameraPos = CullingStateManager.CAMERA.getPosition();
            double distance = pos.subtract(cameraPos).length();

            Vec3 intersectionPoint = pos.add(cameraDir.scale(-t * distance));
            Vector4f clippedPos = CullingStateManager.PROJECTION_MATRIX.transform(
                    CullingStateManager.VIEW_MATRIX.transform(
                            new Vector4f((float) intersectionPoint.x, (float) intersectionPoint.y, (float) intersectionPoint.z, 1),
                            new Vector4f()
                    ),
                    new Vector4f()
            );

            ndc = new Vec3(ndc.x, ndc.y, clippedPos.z / clippedPos.w);
        }

        return ndc.add(new Vec3(1.0, 1.0, 1.0)).scale(0.5);
    }

    public static float getChunkDepth(Vec3 chunkBasePos, Vec3 cameraPos) {
        far = MeshUniform.getRenderDistance() * 64f;
        Vec3 chunkPos = chunkBasePos.scale(16);
        chunkPos = new Vec3(chunkPos.x + 8.0, chunkPos.y + 8.0, chunkPos.z + 8.0);

        float sizeOffset = 8.0f;

        Vec3[] aabb = new Vec3[]{
                chunkPos.add(new Vec3(-sizeOffset, -sizeOffset, -sizeOffset)), chunkPos.add(new Vec3(sizeOffset, -sizeOffset, -sizeOffset)),
                chunkPos.add(new Vec3(-sizeOffset, sizeOffset, -sizeOffset)), chunkPos.add(new Vec3(sizeOffset, sizeOffset, -sizeOffset)),
                chunkPos.add(new Vec3(-sizeOffset, -sizeOffset, sizeOffset)), chunkPos.add(new Vec3(sizeOffset, -sizeOffset, sizeOffset)),
                chunkPos.add(new Vec3(-sizeOffset, sizeOffset, sizeOffset)), chunkPos.add(new Vec3(sizeOffset, sizeOffset, sizeOffset))
        };

        float maxX = 0.0f;
        float maxY = 0.0f;
        float minX = 1.0f;
        float minY = 1.0f;

        float sectionDepth = 1.0f;

        for (int i = 0; i < 8; ++i) {
            Vec3 screenPos = worldToScreenSpace(aabb[i]);

            if (screenPos.x >= 0 && screenPos.x <= 1
                    && screenPos.y >= 0 && screenPos.y <= 1
                    && screenPos.z >= 0 && screenPos.z <= 1) {
            }

            maxX = (float) Math.max(screenPos.x, maxX);
            maxY = (float) Math.max(screenPos.y, maxY);
            minX = (float) Math.min(screenPos.x, minX);
            minY = (float) Math.min(screenPos.y, minY);

            sectionDepth = (float) Math.min(screenPos.z, sectionDepth);
        }

        return LinearizeDepth(sectionDepth);
    }
}
