package rogo.sketch.module.culling.entity;

import org.jetbrains.annotations.Nullable;
import org.joml.primitives.AABBf;
import rogo.sketch.core.graphics.ecs.GraphicsEntityId;

import java.util.Objects;

/**
 * Core-owned schema for entity and block-entity culling inputs.
 * <p>
 * The v1 GPU payload intentionally preserves the existing shader contract:
 * center.xyz + extent.xyz packed as six floats. Additional metadata stays on
 * the CPU side for future layer/animation/schema work.
 * </p>
 */
public final class EntityFeatureSchema {
    public static final int DEFAULT_INITIAL_CAPACITY = 64;
    public static final int DEFAULT_TTL_TICKS = 20;
    public static final int CULLING_V1_FLOAT_COUNT = 6;
    public static final long CULLING_V1_STRIDE_BYTES = (long) CULLING_V1_FLOAT_COUNT * Float.BYTES;
    public static final int FLAG_NONE = 0;

    private EntityFeatureSchema() {
    }

    public enum SubjectKind {
        ENTITY,
        BLOCK_ENTITY
    }

    public static SubjectData fromBounds(
            Object subjectKey,
            SubjectKind subjectKind,
            AABBf bounds,
            int flags,
            @Nullable GraphicsEntityId transformEntityId) {
        Objects.requireNonNull(bounds, "bounds");
        float centerX = (bounds.minX + bounds.maxX) * 0.5f;
        float centerY = (bounds.minY + bounds.maxY) * 0.5f;
        float centerZ = (bounds.minZ + bounds.maxZ) * 0.5f;
        float extentX = bounds.maxX - bounds.minX;
        float extentY = bounds.maxY - bounds.minY;
        float extentZ = bounds.maxZ - bounds.minZ;
        return new SubjectData(
                subjectKey,
                subjectKind,
                centerX,
                centerY,
                centerZ,
                extentX,
                extentY,
                extentZ,
                flags,
                transformEntityId);
    }

    public record SubjectData(
            Object subjectKey,
            SubjectKind subjectKind,
            float centerX,
            float centerY,
            float centerZ,
            float extentX,
            float extentY,
            float extentZ,
            int flags,
            @Nullable GraphicsEntityId transformEntityId) {
        public SubjectData {
            Objects.requireNonNull(subjectKey, "subjectKey");
            Objects.requireNonNull(subjectKind, "subjectKind");
        }

        public AABBf bounds() {
            float halfExtentX = extentX * 0.5f;
            float halfExtentY = extentY * 0.5f;
            float halfExtentZ = extentZ * 0.5f;
            return new AABBf(
                    centerX - halfExtentX,
                    centerY - halfExtentY,
                    centerZ - halfExtentZ,
                    centerX + halfExtentX,
                    centerY + halfExtentY,
                    centerZ + halfExtentZ);
        }
    }
}
