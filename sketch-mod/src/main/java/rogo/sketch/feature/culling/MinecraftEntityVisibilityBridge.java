package rogo.sketch.feature.culling;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.primitives.AABBf;
import rogo.sketch.SketchRender;
import rogo.sketch.module.culling.CullingModuleDescriptor;
import rogo.sketch.module.culling.entity.EntityFeatureSchema;

import static net.minecraftforge.common.extensions.IForgeBlockEntity.INFINITE_EXTENT_AABB;

public final class MinecraftEntityVisibilityBridge {
    private static final ThreadLocal<AABBf> SCRATCH_BOUNDS = ThreadLocal.withInitial(AABBf::new);
    private static volatile CullingVisibilitySettingsSnapshot settingsSnapshot = CullingVisibilitySettingsSnapshot.disabled();

    private MinecraftEntityVisibilityBridge() {
    }

    public static void updateSettingsSnapshot(CullingVisibilitySettingsSnapshot snapshot) {
        settingsSnapshot = snapshot != null ? snapshot : CullingVisibilitySettingsSnapshot.disabled();
    }

    public static boolean shouldSkipEntity(Entity entity) {
        CullingVisibilitySettingsSnapshot snapshot = settingsSnapshot;
        MinecraftCullingDebugState debugState = MinecraftCullingDebugState.getInstance();
        MinecraftShaderCapabilityService shaderCapabilities = MinecraftShaderCapabilityService.getInstance();
        MinecraftHiZState hiZState = MinecraftHiZState.getInstance();
        debugState.incrementEntityTotal();
        if (entity == null || !snapshot.cullEntity() || shaderCapabilities.renderingShadowPass()) {
            return false;
        }
        if (entity instanceof Player || entity.isCurrentlyGlowing() || entity.noCulling) {
            return false;
        }
        if (hiZState.camera() == null || entity.distanceToSqr(hiZState.camera().getPosition()) < 4.0D) {
            return false;
        }
        if (snapshot.entitySkipContains(entity.getType().getDescriptionId())) {
            return false;
        }
        var queries = snapshot.queries();
        AABBf bounds = toBounds(SketchRender.getObjectAABB(entity));
        if (queries == null || bounds == null) {
            return false;
        }
        if (!queries.shouldSkipEntity(entity, bounds, EntityFeatureSchema.FLAG_NONE, rogo.sketch.vanilla.PipelineUtil.pipeline().currentLogicTick())) {
            return false;
        }
        debugState.incrementEntityHidden();
        return true;
    }

    public static boolean shouldSkipBlockEntity(BlockEntity blockEntity, BlockPos pos) {
        CullingVisibilitySettingsSnapshot snapshot = settingsSnapshot;
        MinecraftCullingDebugState debugState = MinecraftCullingDebugState.getInstance();
        MinecraftShaderCapabilityService shaderCapabilities = MinecraftShaderCapabilityService.getInstance();
        MinecraftHiZState hiZState = MinecraftHiZState.getInstance();
        debugState.incrementBlockEntityTotal();
        if (blockEntity == null || pos == null || !snapshot.cullBlockEntity() || shaderCapabilities.renderingShadowPass()) {
            return false;
        }
        if (hiZState.camera() == null) {
            return false;
        }
        double renderDistance = MinecraftEntityVisibilityBridge.renderDistanceSquared();
        if (hiZState.camera().getPosition().distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) > renderDistance * 2.0D) {
            return false;
        }
        if (snapshot.blockEntitySkipContains(BlockEntityType.getKey(blockEntity.getType()).toString())) {
            return false;
        }
        AABB bounds = SketchRender.getObjectAABB(blockEntity);
        if (bounds == null || bounds == INFINITE_EXTENT_AABB) {
            return false;
        }
        var queries = snapshot.queries();
        if (queries == null || !queries.shouldSkipBlockEntity(
                blockEntity,
                toBounds(bounds),
                EntityFeatureSchema.FLAG_NONE,
                rogo.sketch.vanilla.PipelineUtil.pipeline().currentLogicTick())) {
            return false;
        }
        debugState.incrementBlockEntityHidden();
        return true;
    }

    private static double renderDistanceSquared() {
        int renderDistance = net.minecraft.client.Minecraft.getInstance().options.getEffectiveRenderDistance() * 16;
        return (double) renderDistance * renderDistance;
    }

    private static @Nullable AABBf toBounds(@Nullable AABB bounds) {
        if (bounds == null) {
            return null;
        }
        AABBf scratch = SCRATCH_BOUNDS.get();
        scratch.setMin((float) bounds.minX, (float) bounds.minY, (float) bounds.minZ);
        scratch.setMax((float) bounds.maxX, (float) bounds.maxY, (float) bounds.maxZ);
        return scratch;
    }
}
