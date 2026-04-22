package rogo.sketch.core.pipeline.flow.plan;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.graphics.ecs.GraphicsUniformSubject;
import rogo.sketch.core.pipeline.CompiledRenderSetting;
import rogo.sketch.core.pipeline.flow.v2.PreparedStageGeometryView;
import rogo.sketch.core.pipeline.flow.v2.ResourceGroupSlice;
import rogo.sketch.core.pipeline.flow.v2.StageEntityView;
import rogo.sketch.core.shader.ShaderProgramHandle;
import rogo.sketch.core.shader.ShaderProgramResolver;
import rogo.sketch.core.shader.uniform.DrawUniformSet;
import rogo.sketch.core.shader.uniform.FrameUniformSet;
import rogo.sketch.core.shader.uniform.FrameUniformSnapshot;
import rogo.sketch.core.shader.uniform.PassUniformSet;
import rogo.sketch.core.shader.uniform.ResourceUniformSet;
import rogo.sketch.core.shader.uniform.UniformCaptureTiming;
import rogo.sketch.core.shader.uniform.UniformGroupSet;
import rogo.sketch.core.shader.uniform.UniformHookGroup;
import rogo.sketch.core.shader.uniform.UniformValueSnapshot;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ResourceGroupCompiler {
    public List<ResourceGroupSlice> compile(
            CompiledRenderSetting compiledRenderSetting,
            Object sourceSlice,
            List<StageEntityView.Entry> entries) {
        return compile(compiledRenderSetting, sourceSlice, entries, FrameUniformSnapshot.empty());
    }

    public List<ResourceGroupSlice> compile(
            CompiledRenderSetting compiledRenderSetting,
            Object sourceSlice,
            List<StageEntityView.Entry> entries,
            @Nullable FrameUniformSnapshot frameUniformSnapshot) {
        return finalizePrepared(prepare(compiledRenderSetting, sourceSlice, entries, frameUniformSnapshot), entries);
    }

    public List<PreparedStageGeometryView.PreparedResourceGroupSlice> prepare(
            CompiledRenderSetting compiledRenderSetting,
            Object sourceSlice,
            List<StageEntityView.Entry> entries) {
        return prepare(compiledRenderSetting, sourceSlice, entries, FrameUniformSnapshot.empty());
    }

    public List<PreparedStageGeometryView.PreparedResourceGroupSlice> prepare(
            CompiledRenderSetting compiledRenderSetting,
            Object sourceSlice,
            List<StageEntityView.Entry> entries,
            @Nullable FrameUniformSnapshot frameUniformSnapshot) {
        List<PreparedStageGeometryView.PreparedResourceGroupSlice> groups = new ArrayList<>();
        if (compiledRenderSetting == null || entries == null || entries.isEmpty()) {
            return groups;
        }

        ShaderProgramHandle shaderProvider = extractShaderProvider(compiledRenderSetting);
        if (shaderProvider == null || shaderProvider.uniformHooks() == null) {
            groups.add(new PreparedStageGeometryView.PreparedResourceGroupSlice(
                    sourceSlice,
                    compiledRenderSetting.pipelineStateKey(),
                    compiledRenderSetting.resourceBindingPlan(),
                    rogo.sketch.core.packet.ResourceSetKey.from(
                            compiledRenderSetting.resourceBindingPlan(),
                            UniformGroupSet.empty().resourceUniforms()),
                    UniformGroupSet.empty(),
                    entries));
            return groups;
        }

        UniformHookGroup hookGroup = shaderProvider.uniformHooks();
        UniformValueSnapshot frameSnapshot = frameUniformSnapshot != null
                ? frameUniformSnapshot.snapshotFor(hookGroup)
                : UniformValueSnapshot.empty();
        UniformGroupSet staticUniformGroups = uniformGroups(frameSnapshot, UniformValueSnapshot.empty());
        if (entries.stream().noneMatch(entry -> entry != null && entry.uniformSubject() != null)) {
            groups.add(new PreparedStageGeometryView.PreparedResourceGroupSlice(
                    sourceSlice,
                    compiledRenderSetting.pipelineStateKey(),
                    compiledRenderSetting.resourceBindingPlan(),
                    rogo.sketch.core.packet.ResourceSetKey.from(
                            compiledRenderSetting.resourceBindingPlan(),
                            staticUniformGroups.resourceUniforms()),
                    staticUniformGroups,
                    entries));
            return groups;
        }

        var cachedMatchingHooks = hookGroup.getAllMatchingHooks(
                GraphicsUniformSubject.class,
                UniformCaptureTiming.BUILD_ASYNC_SAFE);
        Map<UniformValueSnapshot, List<StageEntityView.Entry>> grouped = new LinkedHashMap<>();
        for (StageEntityView.Entry entry : entries) {
            GraphicsUniformSubject uniformSubject = entry != null ? entry.uniformSubject() : null;
            if (uniformSubject == null) {
                continue;
            }
            UniformValueSnapshot snapshot = UniformValueSnapshot.captureFrom(
                    hookGroup,
                    uniformSubject,
                    cachedMatchingHooks,
                    UniformCaptureTiming.BUILD_ASYNC_SAFE);
            grouped.computeIfAbsent(snapshot, ignored -> new ArrayList<>()).add(entry);
        }

        for (Map.Entry<UniformValueSnapshot, List<StageEntityView.Entry>> groupedEntry : grouped.entrySet()) {
            UniformGroupSet uniformGroups = uniformGroups(frameSnapshot, groupedEntry.getKey());
            groups.add(new PreparedStageGeometryView.PreparedResourceGroupSlice(
                    sourceSlice,
                    compiledRenderSetting.pipelineStateKey(),
                    compiledRenderSetting.resourceBindingPlan(),
                    rogo.sketch.core.packet.ResourceSetKey.from(
                            compiledRenderSetting.resourceBindingPlan(),
                            uniformGroups.resourceUniforms()),
                    uniformGroups,
                    groupedEntry.getValue()));
        }
        return groups;
    }

    private UniformGroupSet uniformGroups(
            UniformValueSnapshot frameSnapshot,
            UniformValueSnapshot resourceSnapshot) {
        if ((frameSnapshot == null || frameSnapshot.isEmpty())
                && (resourceSnapshot == null || resourceSnapshot.isEmpty())) {
            return UniformGroupSet.empty();
        }
        return new UniformGroupSet(
                frameSnapshot != null && !frameSnapshot.isEmpty() ? new FrameUniformSet(frameSnapshot) : FrameUniformSet.empty(),
                PassUniformSet.empty(),
                resourceSnapshot != null && !resourceSnapshot.isEmpty() ? new ResourceUniformSet(resourceSnapshot) : ResourceUniformSet.empty(),
                DrawUniformSet.empty());
    }

    public List<ResourceGroupSlice> finalizePrepared(
            List<PreparedStageGeometryView.PreparedResourceGroupSlice> preparedGroups,
            List<StageEntityView.Entry> visibleEntries) {
        List<ResourceGroupSlice> groups = new ArrayList<>();
        if (preparedGroups == null || preparedGroups.isEmpty() || visibleEntries == null || visibleEntries.isEmpty()) {
            return groups;
        }
        IdentityHashMap<StageEntityView.Entry, Boolean> allowed = new IdentityHashMap<>();
        for (StageEntityView.Entry entry : visibleEntries) {
            if (entry != null) {
                allowed.put(entry, Boolean.TRUE);
            }
        }
        if (allowed.isEmpty()) {
            return groups;
        }
        for (PreparedStageGeometryView.PreparedResourceGroupSlice preparedGroup : preparedGroups) {
            if (preparedGroup == null || preparedGroup.preparedEntries().isEmpty()) {
                continue;
            }
            boolean allVisible = preparedGroup.preparedEntries().size() == allowed.size();
            if (allVisible) {
                for (StageEntityView.Entry entry : preparedGroup.preparedEntries()) {
                    if (!allowed.containsKey(entry)) {
                        allVisible = false;
                        break;
                    }
                }
            }
            List<StageEntityView.Entry> selected = preparedGroup.preparedEntries();
            if (!allVisible) {
                selected = new ArrayList<>();
                for (StageEntityView.Entry entry : preparedGroup.preparedEntries()) {
                    if (allowed.containsKey(entry)) {
                        selected.add(entry);
                    }
                }
            }
            if (selected.isEmpty()) {
                continue;
            }
            groups.add(new ResourceGroupSlice(
                    preparedGroup.sourceSlice(),
                    preparedGroup.stateKey(),
                    preparedGroup.bindingPlan(),
                    preparedGroup.resourceSetKey(),
                    preparedGroup.uniformGroups(),
                    selected));
        }
        return groups;
    }

    private ShaderProgramHandle extractShaderProvider(CompiledRenderSetting compiledRenderSetting) {
        return ShaderProgramResolver.resolveProgramHandleIfAvailable(compiledRenderSetting);
    }

}

