package rogo.sketch.core.pipeline.flow.plan;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.graphics.ecs.GraphicsEntityId;
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
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        Set<GraphicsEntityId> allowedEntityIds = new HashSet<>();
        for (StageEntityView.Entry entry : visibleEntries) {
            if (entry != null) {
                allowed.put(entry, Boolean.TRUE);
                allowedEntityIds.add(entry.entityId());
            }
        }
        if (allowed.isEmpty()) {
            return groups;
        }
        for (PreparedStageGeometryView.PreparedResourceGroupSlice preparedGroup : preparedGroups) {
            if (preparedGroup == null || preparedGroup.preparedEntries().isEmpty()) {
                continue;
            }
            boolean sameVisibleSize = preparedGroup.preparedEntries().size() == allowed.size();
            boolean allVisibleByIdentity = sameVisibleSize;
            if (sameVisibleSize) {
                for (StageEntityView.Entry entry : preparedGroup.preparedEntries()) {
                    if (!allowed.containsKey(entry)) {
                        allVisibleByIdentity = false;
                    }
                    if (!isAllowed(entry, allowed, allowedEntityIds)) {
                        allVisibleByIdentity = false;
                        break;
                    }
                }
            }
            List<StageEntityView.Entry> selected = allVisibleByIdentity ? preparedGroup.preparedEntries() : List.of();
            if (!allVisibleByIdentity) {
                selected = new ArrayList<>();
                Set<GraphicsEntityId> preparedEntityIds = entityIdsOf(preparedGroup.preparedEntries());
                IdentityHashMap<StageEntityView.Entry, Boolean> preparedIdentities = identitiesOf(preparedGroup.preparedEntries());
                for (StageEntityView.Entry entry : visibleEntries) {
                    if (entry != null
                            && (preparedIdentities.containsKey(entry) || preparedEntityIds.contains(entry.entityId()))) {
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

    private boolean isAllowed(
            StageEntityView.Entry entry,
            IdentityHashMap<StageEntityView.Entry, Boolean> allowed,
            Set<GraphicsEntityId> allowedEntityIds) {
        return entry != null && (allowed.containsKey(entry) || allowedEntityIds.contains(entry.entityId()));
    }

    private IdentityHashMap<StageEntityView.Entry, Boolean> identitiesOf(List<StageEntityView.Entry> entries) {
        IdentityHashMap<StageEntityView.Entry, Boolean> identities = new IdentityHashMap<>();
        if (entries == null) {
            return identities;
        }
        for (StageEntityView.Entry entry : entries) {
            if (entry != null) {
                identities.put(entry, Boolean.TRUE);
            }
        }
        return identities;
    }

    private Set<GraphicsEntityId> entityIdsOf(List<StageEntityView.Entry> entries) {
        Set<GraphicsEntityId> entityIds = new HashSet<>();
        if (entries == null) {
            return entityIds;
        }
        for (StageEntityView.Entry entry : entries) {
            if (entry != null) {
                entityIds.add(entry.entityId());
            }
        }
        return entityIds;
    }

    private ShaderProgramHandle extractShaderProvider(CompiledRenderSetting compiledRenderSetting) {
        return ShaderProgramResolver.resolveProgramHandleIfAvailable(compiledRenderSetting);
    }

}

