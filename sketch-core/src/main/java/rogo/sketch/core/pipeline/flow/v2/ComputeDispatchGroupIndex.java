package rogo.sketch.core.pipeline.flow.v2;

import rogo.sketch.core.packet.PipelineStateKey;
import rogo.sketch.core.packet.ResourceSetKey;
import rogo.sketch.core.pipeline.CompiledRenderSetting;
import rogo.sketch.core.shader.ShaderProgramHandle;
import rogo.sketch.core.shader.ShaderProgramResolver;
import rogo.sketch.core.shader.uniform.UniformGroupSet;
import rogo.sketch.core.shader.uniform.UniformHook;
import rogo.sketch.core.shader.uniform.UniformHookGroup;
import rogo.sketch.core.shader.uniform.UniformValueSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ComputeDispatchGroupIndex {
    public List<ComputeDispatchSlice> build(List<ComputeInstanceStore.Entry> orderedEntries) {
        Map<GroupKey, List<ComputeInstanceStore.Entry>> groupedEntries = new LinkedHashMap<>();
        Map<GroupKey, ComputeDispatchSliceMeta> sliceMeta = new LinkedHashMap<>();
        Map<HookCacheKey, List<UniformHook<?>>> hookCache = new LinkedHashMap<>();

        for (ComputeInstanceStore.Entry entry : orderedEntries) {
            if (entry == null || entry.compiledRenderSetting() == null) {
                continue;
            }

            UniformValueSnapshot uniformSnapshot = resolveUniformSnapshot(entry, hookCache);
            ResourceSetKey resourceSetKey = ResourceSetKey.from(
                    entry.compiledRenderSetting().resourceBindingPlan(),
                    UniformGroupSet.fromSnapshot(uniformSnapshot).resourceUniforms());
            GroupKey groupKey = new GroupKey(
                    entry.compiledRenderSetting().pipelineStateKey(),
                    resourceSetKey,
                    uniformSnapshot);

            groupedEntries.computeIfAbsent(groupKey, ignored -> new ArrayList<>()).add(entry);
            sliceMeta.putIfAbsent(groupKey, new ComputeDispatchSliceMeta(
                    entry.compiledRenderSetting(),
                    resourceSetKey,
                    uniformSnapshot));
        }

        List<ComputeDispatchSlice> slices = new ArrayList<>(groupedEntries.size());
        for (Map.Entry<GroupKey, List<ComputeInstanceStore.Entry>> grouped : groupedEntries.entrySet()) {
            ComputeDispatchSliceMeta meta = sliceMeta.get(grouped.getKey());
            slices.add(new ComputeDispatchSlice(
                    meta.compiledRenderSetting(),
                    meta.resourceSetKey(),
                    meta.uniformSnapshot(),
                    grouped.getValue()));
        }
        return slices;
    }

    private UniformValueSnapshot resolveUniformSnapshot(
            ComputeInstanceStore.Entry entry,
            Map<HookCacheKey, List<UniformHook<?>>> hookCache) {
        ShaderProgramHandle programHandle = ShaderProgramResolver.resolveProgramHandleIfAvailable(entry.compiledRenderSetting());
        if (programHandle == null || programHandle.uniformHooks() == null) {
            return UniformValueSnapshot.empty();
        }

        UniformHookGroup hookGroup = programHandle.uniformHooks();
        HookCacheKey cacheKey = new HookCacheKey(programHandle.getHandle(), entry.graphics().getClass());
        List<UniformHook<?>> cachedHooks = hookCache.computeIfAbsent(
                cacheKey,
                ignored -> hookGroup.getAllMatchingHooks(entry.graphics().getClass()));
        return UniformValueSnapshot.captureFrom(hookGroup, entry.graphics(), cachedHooks);
    }

    private record GroupKey(
            PipelineStateKey pipelineStateKey,
            ResourceSetKey resourceSetKey,
            UniformValueSnapshot uniformSnapshot
    ) {
    }

    private record ComputeDispatchSliceMeta(
            CompiledRenderSetting compiledRenderSetting,
            ResourceSetKey resourceSetKey,
            UniformValueSnapshot uniformSnapshot
    ) {
    }

    private record HookCacheKey(int handle, Class<?> graphicsClass) {
    }
}

