package rogo.sketch.core.pipeline.flow.v2;

import rogo.sketch.core.graphics.ecs.GraphicsBuiltinComponents;
import rogo.sketch.core.graphics.ecs.FunctionCommands;
import rogo.sketch.core.graphics.ecs.GraphicsUniformSubject;
import rogo.sketch.core.graphics.ecs.GraphicsEntityAssembler;
import rogo.sketch.core.graphics.ecs.GraphicsWorld;
import rogo.sketch.core.packet.ClearPacket;
import rogo.sketch.core.packet.CopyTexturePacket;
import rogo.sketch.core.packet.ExecutionKey;
import rogo.sketch.core.packet.GenerateMipmapPacket;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.packet.TransferPlanKey;
import rogo.sketch.core.pipeline.CompiledRenderSetting;
import rogo.sketch.core.pipeline.GraphicsStage;
import rogo.sketch.core.pipeline.PartialRenderSetting;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.pipeline.RenderSettingCompiler;
import rogo.sketch.core.pipeline.StageRouteCompiler;
import rogo.sketch.core.pipeline.flow.RenderFlowType;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.shader.uniform.FrameUniformSnapshot;
import rogo.sketch.core.shader.uniform.UniformValueSnapshot;
import rogo.sketch.core.shader.variant.ShaderVariantKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FunctionStageFlowScene<C extends RenderContext> implements StageFlowScene<C> {
    private final PipelineType pipelineType;
    private final GraphicsResourceManager resourceManager;
    private final ShaderVariantKey stageVariantKey;
    private final FunctionEntityStateCache stateCache = new FunctionEntityStateCache();

    public FunctionStageFlowScene(
            GraphicsStage stage,
            PipelineType pipelineType,
            GraphicsResourceManager resourceManager) {
        this.pipelineType = pipelineType;
        this.resourceManager = resourceManager;
        this.stageVariantKey = stage != null ? stage.getStageVariantKey() : ShaderVariantKey.EMPTY;
    }

    @Override
    public PipelineType pipelineType() {
        return pipelineType;
    }

    @Override
    public void prepareForFrame(GraphicsWorld world, StageEntityView view, C context, FrameUniformSnapshot frameUniformSnapshot) {
        List<StageEntityView.Entry> entries = view != null ? view.functionEntries() : List.of();
        stateCache.retainOnly(view != null ? view.functionEntityIds() : List.of());
        for (StageEntityView.Entry entry : entries) {
            if (entry == null || entry.shouldDiscard()) {
                continue;
            }
            FunctionEntityStateCache.Entry state = stateCache.upsert(entry.entityId());
            CompiledRenderSetting compiledRenderSetting = resolveCompiledRenderSetting(entry);
            if (compiledRenderSetting == null && entry.renderParameter() != null) {
                RenderSetting renderSetting = RenderSetting.fromPartial(entry.renderParameter(), PartialRenderSetting.EMPTY);
                compiledRenderSetting = StageRouteCompiler.compile(
                        RenderSettingCompiler.compile(renderSetting, resourceManager),
                        entry.renderParameter(),
                        resourceManager,
                        entry.stageRoute(),
                        stageVariantKey);
                state.setRenderSetting(renderSetting);
            } else if (compiledRenderSetting != null) {
                state.setRenderSetting(compiledRenderSetting.renderSetting());
            }
            state.setCompiledRenderSetting(compiledRenderSetting);
        }
    }

    @Override
    public void tick(GraphicsWorld world, StageEntityView view, C context) {
        if (view == null) {
            return;
        }
        for (StageEntityView.Entry entry : view.functionEntries()) {
            entry.tick();
        }
    }

    @Override
    public void asyncTick(GraphicsWorld world, StageEntityView view, C context) {
        if (view == null) {
            return;
        }
        for (StageEntityView.Entry entry : view.functionEntries()) {
            entry.asyncTick();
        }
    }

    @Override
    public void swapData(GraphicsWorld world, StageEntityView view) {
        if (view == null) {
            return;
        }
        for (StageEntityView.Entry entry : view.functionEntries()) {
            entry.swapData();
        }
    }

    @Override
    public void cleanupDiscardedEntities(GraphicsWorld world, GraphicsEntityAssembler assembler, StageEntityView view) {
        if (view == null || assembler == null) {
            return;
        }
        for (StageEntityView.Entry entry : view.functionEntries()) {
            if (entry.shouldDiscard()) {
                assembler.destroy(entry.entityId());
            }
        }
    }

    @Override
    public Map<ExecutionKey, List<RenderPacket>> createRenderPackets(
            StageEntityView view,
            RenderFlowType flowType,
            RenderPostProcessors postProcessors,
            C context,
            FrameUniformSnapshot frameUniformSnapshot) {
        if (view == null || view.isEmpty()) {
            return Map.of();
        }

        List<StageEntityView.Entry> activeEntries = new ArrayList<>();
        for (StageEntityView.Entry entry : view.functionEntries()) {
            if (entry == null || entry.shouldDiscard() || !entry.shouldRender()) {
                continue;
            }
            FunctionEntityStateCache.Entry cached = stateCache.upsert(entry.entityId());
            if (cached.compiledRenderSetting() == null) {
                CompiledRenderSetting compiledRenderSetting = resolveCompiledRenderSetting(entry);
                if (compiledRenderSetting == null && entry.renderParameter() != null) {
                    RenderSetting renderSetting = RenderSetting.fromPartial(entry.renderParameter(), PartialRenderSetting.EMPTY);
                    compiledRenderSetting = StageRouteCompiler.compile(
                            RenderSettingCompiler.compile(renderSetting, resourceManager),
                            entry.renderParameter(),
                            resourceManager,
                            entry.stageRoute(),
                            stageVariantKey);
                    cached.setRenderSetting(renderSetting);
                } else if (compiledRenderSetting != null) {
                    cached.setRenderSetting(compiledRenderSetting.renderSetting());
                }
                cached.setCompiledRenderSetting(compiledRenderSetting);
            }
            if (cached.compiledRenderSetting() == null) {
                continue;
            }
            activeEntries.add(entry);
        }
        if (activeEntries.isEmpty()) {
            return Map.of();
        }

        activeEntries.sort(Comparator
                .comparingInt((StageEntityView.Entry entry) -> functionPriority(entry.functionInvoke()))
                .thenComparingLong(StageEntityView.Entry::orderHint));

        Map<ExecutionKey, List<RenderPacket>> packets = new LinkedHashMap<>();
        for (StageEntityView.Entry entry : activeEntries) {
            FunctionEntityStateCache.Entry cached = stateCache.upsert(entry.entityId());
            CompiledRenderSetting compiledRenderSetting = cached.compiledRenderSetting();
            if (compiledRenderSetting == null) {
                continue;
            }
            List<FunctionCommands.Command> commands = extractCommands(entry.functionInvoke());
            if (commands.isEmpty()) {
                continue;
            }
            List<RenderPacket> statePackets = packets.computeIfAbsent(
                    compiledRenderSetting.pipelineStateKey(),
                    ignored -> new ArrayList<>());
            for (FunctionCommands.Command command : commands) {
                RenderPacket packet = compileCommand(view.stageId(), compiledRenderSetting, entry, command);
                if (packet != null) {
                    statePackets.add(packet);
                }
            }
        }
        return packets;
    }

    @Override
    public void clear() {
    }

    private CompiledRenderSetting resolveCompiledRenderSetting(StageEntityView.Entry entry) {
        return StageRouteCompiler.compile(
                entry != null ? entry.buildRenderDescriptor() : null,
                entry != null ? entry.renderParameter() : null,
                resourceManager,
                entry != null ? entry.stageRoute() : null,
                stageVariantKey);
    }

    private int functionPriority(GraphicsBuiltinComponents.FunctionInvokeComponent component) {
        return component != null ? component.priority() : 100;
    }

    private List<FunctionCommands.Command> extractCommands(GraphicsBuiltinComponents.FunctionInvokeComponent component) {
        if (component == null || component.payload() == null) {
            return List.of();
        }
        Object payload = component.payload();
        if (payload instanceof FunctionCommands.Command[] commands) {
            List<FunctionCommands.Command> copied = new ArrayList<>(commands.length);
            for (FunctionCommands.Command command : commands) {
                if (command != null) {
                    copied.add(command);
                }
            }
            return List.copyOf(copied);
        }
        if (payload instanceof List<?> rawList) {
            List<FunctionCommands.Command> copied = new ArrayList<>(rawList.size());
            for (Object value : rawList) {
                if (value instanceof FunctionCommands.Command command) {
                    copied.add(command);
                }
            }
            return List.copyOf(copied);
        }
        return List.of();
    }

    private RenderPacket compileCommand(
            rogo.sketch.core.util.KeyId stageId,
            CompiledRenderSetting compiledRenderSetting,
            StageEntityView.Entry entry,
            FunctionCommands.Command command) {
        if (compiledRenderSetting == null || command == null) {
            return null;
        }
        List<GraphicsUniformSubject> completionSubjects = entry.uniformSubject() != null ? List.of(entry.uniformSubject()) : List.of();
        if (command instanceof FunctionCommands.ClearCommand clearCommand) {
            return new ClearPacket(
                    stageId,
                    pipelineType,
                    TransferPlanKey.forRenderTarget(clearCommand.renderTargetId()),
                    compiledRenderSetting.resourceBindingPlan(),
                    UniformValueSnapshot.empty(),
                    completionSubjects,
                    clearCommand.renderTargetId(),
                    clearCommand.colorAttachments(),
                    clearCommand.clearColor(),
                    clearCommand.clearDepth(),
                    clearCommand.clearColorValue(),
                    clearCommand.clearDepthValue(),
                    clearCommand.colorMask(),
                    clearCommand.restorePreviousRenderTarget());
        }
        if (command instanceof FunctionCommands.GenMipmapCommand genMipmapCommand) {
            return new GenerateMipmapPacket(
                    stageId,
                    pipelineType,
                    TransferPlanKey.forTexture(genMipmapCommand.textureId()),
                    compiledRenderSetting.resourceBindingPlan(),
                    UniformValueSnapshot.empty(),
                    completionSubjects,
                    genMipmapCommand.textureId());
        }
        if (command instanceof FunctionCommands.CopyTextureCommand copyTextureCommand) {
            return new CopyTexturePacket(
                    stageId,
                    pipelineType,
                    TransferPlanKey.forTexture(copyTextureCommand.destinationTextureId()),
                    compiledRenderSetting.resourceBindingPlan(),
                    UniformValueSnapshot.empty(),
                    completionSubjects,
                    copyTextureCommand.sourceTextureId(),
                    copyTextureCommand.destinationTextureId(),
                    copyTextureCommand.width(),
                    copyTextureCommand.height(),
                    copyTextureCommand.depthCopy());
        }
        return null;
    }
}
