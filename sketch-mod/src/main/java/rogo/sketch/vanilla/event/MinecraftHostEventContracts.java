package rogo.sketch.vanilla.event;

import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.extension.event.HostEventContract;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.vanilla.McRenderContext;

/**
 * Minecraft-specific host event contracts that stay on the mod side while the
 * module system consumes them through the generic host-event registrar.
 */
public final class MinecraftHostEventContracts {
    public static final HostEventContract<WorldEnterEvent> WORLD_ENTER =
            HostEventContract.of("minecraft_world_enter", WorldEnterEvent.class);
    public static final HostEventContract<WorldLeaveEvent> WORLD_LEAVE =
            HostEventContract.of("minecraft_world_leave", WorldLeaveEvent.class);
    public static final HostEventContract<ResourceReloadEvent> RESOURCE_RELOAD =
            HostEventContract.of("minecraft_resource_reload", ResourceReloadEvent.class);
    public static final HostEventContract<RenderStagePreEvent> RENDER_STAGE_PRE =
            HostEventContract.of("minecraft_render_stage_pre", RenderStagePreEvent.class);

    private MinecraftHostEventContracts() {
    }

    public record WorldEnterEvent(
            GraphicsPipeline<?> pipeline,
            @Nullable ClientLevel level
    ) {
    }

    public record WorldLeaveEvent(
            GraphicsPipeline<?> pipeline,
            @Nullable ClientLevel level
    ) {
    }

    public record ResourceReloadEvent(
            GraphicsPipeline<?> pipeline
    ) {
    }

    public record RenderStagePreEvent(
            GraphicsPipeline<?> pipeline,
            KeyId stageId,
            McRenderContext context
    ) {
    }
}
