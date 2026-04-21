package rogo.sketch.core.pipeline.flow.v2;

import rogo.sketch.core.api.graphics.ComputeDispatchContext;
import rogo.sketch.core.graphics.ecs.GraphicsEntityAssembler;
import rogo.sketch.core.graphics.ecs.GraphicsUniformSubject;
import rogo.sketch.core.graphics.ecs.GraphicsWorld;
import rogo.sketch.core.packet.ComputePipelineKey;
import rogo.sketch.core.packet.DispatchPacket;
import rogo.sketch.core.packet.ExecutionKey;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.packet.ResourceSetKey;
import rogo.sketch.core.api.ShaderResource;
import rogo.sketch.core.pipeline.CompiledRenderSetting;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.flow.RenderFlowType;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;
import rogo.sketch.core.pipeline.information.ComputeInstanceInfo;
import rogo.sketch.core.shader.ShaderProgramHandle;
import rogo.sketch.core.shader.ShaderProgramResolver;
import rogo.sketch.core.shader.uniform.UniformGroupSet;
import rogo.sketch.core.shader.uniform.UniformHookGroup;
import rogo.sketch.core.shader.uniform.UniformUpdateDomain;
import rogo.sketch.core.shader.uniform.UniformValueSnapshot;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ComputeStageFlowScene<C extends RenderContext> implements StageFlowScene<C> {
    private final PipelineType pipelineType;
    private final ComputeEntityStateCache stateCache = new ComputeEntityStateCache();

    public ComputeStageFlowScene(PipelineType pipelineType) {
        this.pipelineType = pipelineType;
    }

    @Override
    public PipelineType pipelineType() {
        return pipelineType;
    }

    @Override
    public void prepareForFrame(GraphicsWorld world, StageEntityView view, C context) {
        List<StageEntityView.Entry> entries = view != null ? view.computeEntries() : List.of();
        stateCache.retainOnly(view != null ? view.computeEntityIds() : List.of());
        for (StageEntityView.Entry entry : entries) {
            if (entry == null || entry.shouldDiscard()) {
                continue;
            }
            ComputeEntityStateCache.Entry state = stateCache.upsert(entry.entityId());
            long descriptorVersion = entry.descriptorVersionValue();
            if (state.compiledRenderSetting() == null || state.descriptorVersion() != descriptorVersion) {
                state.setCompiledRenderSetting(entry.buildRenderDescriptor());
                state.setDescriptorVersion(descriptorVersion);
            }
        }
    }

    @Override
    public void tick(GraphicsWorld world, StageEntityView view, C context) {
        if (view == null) {
            return;
        }
        for (StageEntityView.Entry entry : view.computeEntries()) {
            entry.tick();
        }
    }

    @Override
    public void asyncTick(GraphicsWorld world, StageEntityView view, C context) {
        if (view == null) {
            return;
        }
        for (StageEntityView.Entry entry : view.computeEntries()) {
            entry.asyncTick();
        }
    }

    @Override
    public void swapData(GraphicsWorld world, StageEntityView view) {
        if (view == null) {
            return;
        }
        for (StageEntityView.Entry entry : view.computeEntries()) {
            entry.swapData();
        }
    }

    @Override
    public void cleanupDiscardedEntities(GraphicsWorld world, GraphicsEntityAssembler assembler, StageEntityView view) {
        if (view == null || assembler == null) {
            return;
        }
        for (StageEntityView.Entry entry : view.computeEntries()) {
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
            C context) {
        if (view == null || view.isEmpty()) {
            return Map.of();
        }

        List<StageEntityView.Entry> activeEntries = new ArrayList<>();
        for (StageEntityView.Entry entry : view.computeEntries()) {
            if (entry == null || entry.shouldDiscard() || !entry.shouldRender() || entry.dispatchCommand() == null) {
                continue;
            }
            ComputeEntityStateCache.Entry cached = stateCache.upsert(entry.entityId());
            if (cached.compiledRenderSetting() == null) {
                cached.setCompiledRenderSetting(entry.buildRenderDescriptor());
                cached.setDescriptorVersion(entry.descriptorVersionValue());
            }
            if (cached.compiledRenderSetting() == null) {
                continue;
            }
            activeEntries.add(entry);
        }
        if (activeEntries.isEmpty()) {
            return Map.of();
        }

        Map<GroupKey, List<StageEntityView.Entry>> groupedEntries = new LinkedHashMap<>();
        Map<GroupKey, GroupMeta> metaByGroup = new LinkedHashMap<>();
        Map<HookCacheKey, rogo.sketch.core.shader.uniform.UniformHook<?>[]> hookCache = new LinkedHashMap<>();

        for (StageEntityView.Entry entry : activeEntries) {
            ComputeEntityStateCache.Entry cached = stateCache.upsert(entry.entityId());
            CompiledRenderSetting compiledRenderSetting = cached.compiledRenderSetting();
            if (compiledRenderSetting == null) {
                continue;
            }
            UniformValueSnapshot uniformSnapshot = resolveUniformSnapshot(compiledRenderSetting, entry, hookCache);
            ResourceSetKey resourceSetKey = ResourceSetKey.from(
                    compiledRenderSetting.resourceBindingPlan(),
                    UniformGroupSet.fromSnapshot(uniformSnapshot).resourceUniforms());
            GroupKey groupKey = new GroupKey(
                    (ComputePipelineKey) compiledRenderSetting.pipelineStateKey(),
                    resourceSetKey,
                    uniformSnapshot);
            groupedEntries.computeIfAbsent(groupKey, ignored -> new ArrayList<>()).add(entry);
            metaByGroup.putIfAbsent(groupKey, new GroupMeta(compiledRenderSetting, resourceSetKey, uniformSnapshot));
        }

        Map<ExecutionKey, List<RenderPacket>> packets = new LinkedHashMap<>();
        for (Map.Entry<GroupKey, List<StageEntityView.Entry>> grouped : groupedEntries.entrySet()) {
            GroupMeta meta = metaByGroup.get(grouped.getKey());
            if (meta == null) {
                continue;
            }
            ComputePipelineKey stateKey = (ComputePipelineKey) meta.compiledRenderSetting().pipelineStateKey();
            List<RenderPacket> statePackets = packets.computeIfAbsent(stateKey, ignored -> new ArrayList<>());
            for (StageEntityView.Entry entry : grouped.getValue()) {
                DispatchGroupCounts dispatchGroups = resolveDispatchGroupCounts(entry.dispatchCommand());
                ComputeInstanceInfo computeInfo = new ComputeInstanceInfo(
                        entry.entityId(),
                        meta.compiledRenderSetting().renderSetting(),
                        entry.dispatchCommand());
                statePackets.add(new DispatchPacket(
                        view.stageId(),
                        pipelineType,
                        stateKey,
                        meta.compiledRenderSetting().resourceBindingPlan(),
                        meta.resourceSetKey(),
                        meta.uniformSnapshot(),
                        List.of(entry.uniformSubject()),
                        dispatchGroups.x(),
                        dispatchGroups.y(),
                        dispatchGroups.z(),
                        computeInfo,
                        entry.dispatchCommand()));
            }
        }
        return packets;
    }

    @Override
    public void clear() {
    }

    private UniformValueSnapshot resolveUniformSnapshot(
            CompiledRenderSetting compiledRenderSetting,
            StageEntityView.Entry entry,
            Map<HookCacheKey, rogo.sketch.core.shader.uniform.UniformHook<?>[]> hookCache) {
        if (compiledRenderSetting == null) {
            return UniformValueSnapshot.empty();
        }
        ShaderProgramHandle programHandle = ShaderProgramResolver.resolveProgramHandleIfAvailable(compiledRenderSetting);
        if (programHandle == null || programHandle.uniformHooks() == null) {
            return UniformValueSnapshot.empty();
        }
        UniformHookGroup hookGroup = programHandle.uniformHooks();
        GraphicsUniformSubject uniformSubject = entry != null ? entry.uniformSubject() : null;
        if (uniformSubject == null) {
            return UniformValueSnapshot.empty();
        }
        HookCacheKey cacheKey = new HookCacheKey(programHandle.getHandle(), GraphicsUniformSubject.class);
        rogo.sketch.core.shader.uniform.UniformHook<?>[] cachedHooks = hookCache.computeIfAbsent(
                cacheKey,
                ignored -> hookGroup.getAllMatchingHooks(GraphicsUniformSubject.class));
        return UniformValueSnapshot.captureFrom(
                hookGroup,
                uniformSubject,
                cachedHooks,
                UniformUpdateDomain.BUILD_SNAPSHOT);
    }

    private record GroupKey(
            ComputePipelineKey pipelineStateKey,
            ResourceSetKey resourceSetKey,
            UniformValueSnapshot uniformSnapshot
    ) {
    }

    private record GroupMeta(
            CompiledRenderSetting compiledRenderSetting,
            ResourceSetKey resourceSetKey,
            UniformValueSnapshot uniformSnapshot
    ) {
    }

    private record HookCacheKey(int handle, Class<?> graphicsClass) {
    }

    private static DispatchGroupCounts resolveDispatchGroupCounts(
            rogo.sketch.core.api.graphics.ComputeDispatchCommand dispatchCommand) {
        if (dispatchCommand == null) {
            return new DispatchGroupCounts(1, 1, 1);
        }
        DispatchGroupCaptureContext captureContext = new DispatchGroupCaptureContext();
        dispatchCommand.dispatch(captureContext);
        return captureContext.toCounts();
    }

    private record DispatchGroupCounts(int x, int y, int z) {
    }

    private static final class DispatchGroupCaptureContext implements ComputeDispatchContext {
        private static final UniformHookGroup CAPTURE_UNIFORM_HOOKS = new CaptureUniformHookGroup();
        private int x = 1;
        private int y = 1;
        private int z = 1;

        @Override
        public rogo.sketch.core.pipeline.RenderContext renderContext() {
            return null;
        }

        @Override
        public rogo.sketch.core.shader.ShaderProgramHandle programHandle() {
            return null;
        }

        @Override
        public UniformHookGroup uniformHookGroup() {
            return CAPTURE_UNIFORM_HOOKS;
        }

        @Override
        public void dispatch(int numGroupsX, int numGroupsY, int numGroupsZ) {
            x = Math.max(1, numGroupsX);
            y = Math.max(1, numGroupsY);
            z = Math.max(1, numGroupsZ);
        }

        @Override
        public void memoryBarrier(int barriers) {
        }

        @Override
        public void shaderStorageBarrier() {
        }

        @Override
        public void allBarriers() {
        }

        private DispatchGroupCounts toCounts() {
            return new DispatchGroupCounts(x, y, z);
        }
    }

    private static final class CaptureUniformHookGroup extends UniformHookGroup {
        @Override
        public <T> ShaderResource<T> getUniform(final String uniformName) {
            return new CaptureShaderResource<>(uniformName);
        }
    }

    private record CaptureShaderResource<T>(String uniformName) implements ShaderResource<T> {
        @Override
        public KeyId id() {
            return KeyId.of(uniformName);
        }

        @Override
        public void set(T value) {
        }
    }
}
