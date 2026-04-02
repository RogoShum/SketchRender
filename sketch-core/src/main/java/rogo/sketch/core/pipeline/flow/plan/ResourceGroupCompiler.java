package rogo.sketch.core.pipeline.flow.plan;

import rogo.sketch.core.api.ShaderProvider;
import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.CompiledRenderSetting;
import rogo.sketch.core.pipeline.RenderSettingCompiler;
import rogo.sketch.core.pipeline.flow.RenderBatch;
import rogo.sketch.core.pipeline.flow.v2.ResourceGroupSlice;
import rogo.sketch.core.pipeline.flow.v2.VisibleBatchSlice;
import rogo.sketch.core.resource.ResourceReference;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.shader.uniform.UniformGroupSet;
import rogo.sketch.core.shader.uniform.UniformHook;
import rogo.sketch.core.shader.uniform.UniformHookGroup;
import rogo.sketch.core.shader.uniform.UniformValueSnapshot;
import rogo.sketch.core.shader.variant.ShaderTemplate;
import rogo.sketch.core.driver.state.gl.ShaderState;
import rogo.sketch.core.pipeline.information.InstanceInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ResourceGroupCompiler {
    /**
     * Legacy bridge for {@link RenderBatch}-backed visible slices.
     * The V2 raster/translucent main path should prefer the
     * {@link #compile(CompiledRenderSetting, Object, List)} overload.
     */
    @Deprecated(forRemoval = false)
    public List<ResourceGroupSlice> compile(VisibleBatchSlice<?> visibleBatchSlice) {
        List<ResourceGroupSlice> groups = new ArrayList<>();
        if (visibleBatchSlice == null || visibleBatchSlice.bucket() == null || visibleBatchSlice.bucket().legacyBatch() == null) {
            return groups;
        }

        RenderBatch<?> batch = visibleBatchSlice.bucket().legacyBatch();
        CompiledRenderSetting compiledRenderSetting = RenderSettingCompiler.compile(batch.getRenderSetting());
        List<Graphics> visibleGraphics = new ArrayList<>(batch.getVisibleInstances().size());
        for (Object visible : batch.getVisibleInstances()) {
            if (visible instanceof InstanceInfo<?> info && info.getInstance() != null) {
                visibleGraphics.add(info.getInstance());
            }
        }
        return compile(compiledRenderSetting, visibleBatchSlice, visibleGraphics);
    }

    public List<ResourceGroupSlice> compile(
            CompiledRenderSetting compiledRenderSetting,
            Object sourceSlice,
            List<? extends Graphics> graphics) {
        List<ResourceGroupSlice> groups = new ArrayList<>();
        if (compiledRenderSetting == null || graphics == null || graphics.isEmpty()) {
            return groups;
        }

        ShaderProvider shaderProvider = extractShaderProvider(compiledRenderSetting);
        if (shaderProvider == null || shaderProvider.getUniformHookGroup() == null) {
            groups.add(new ResourceGroupSlice(
                    sourceSlice,
                    compiledRenderSetting.pipelineStateKey(),
                    compiledRenderSetting.resourceBindingPlan(),
                    rogo.sketch.core.packet.ResourceSetKey.from(
                            compiledRenderSetting.resourceBindingPlan(),
                            UniformGroupSet.empty().resourceUniforms()),
                    UniformGroupSet.empty(),
                    graphics));
            return groups;
        }

        UniformHookGroup hookGroup = shaderProvider.getUniformHookGroup();
        List<UniformHook<?>> cachedMatchingHooks = hookGroup.getAllMatchingHooks(graphics.get(0).getClass());
        Map<UniformValueSnapshot, List<Graphics>> grouped = new LinkedHashMap<>();
        for (Graphics graphic : graphics) {
            if (graphic == null) {
                continue;
            }
            UniformValueSnapshot snapshot = UniformValueSnapshot.captureFrom(hookGroup, graphic, cachedMatchingHooks);
            grouped.computeIfAbsent(snapshot, ignored -> new ArrayList<>()).add(graphic);
        }

        for (Map.Entry<UniformValueSnapshot, List<Graphics>> entry : grouped.entrySet()) {
            UniformGroupSet uniformGroups = UniformGroupSet.fromLegacy(entry.getKey());
            groups.add(new ResourceGroupSlice(
                    sourceSlice,
                    compiledRenderSetting.pipelineStateKey(),
                    compiledRenderSetting.resourceBindingPlan(),
                    rogo.sketch.core.packet.ResourceSetKey.from(
                            compiledRenderSetting.resourceBindingPlan(),
                            uniformGroups.resourceUniforms()),
                    uniformGroups,
                    entry.getValue()));
        }
        return groups;
    }

    private ShaderProvider extractShaderProvider(CompiledRenderSetting compiledRenderSetting) {
        try {
            if (compiledRenderSetting == null
                    || compiledRenderSetting.renderSetting() == null
                    || compiledRenderSetting.renderSetting().renderState() == null
                    || !(compiledRenderSetting.renderSetting().renderState().get(ResourceTypes.SHADER_TEMPLATE) instanceof ShaderState shaderState)) {
                return null;
            }
            ResourceReference<ShaderTemplate> reference = shaderState.getTemplate();
            if (reference != null && reference.isAvailable()) {
                return reference.get();
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
