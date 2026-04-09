package rogo.sketch.core.pipeline.flow.plan;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.CompiledRenderSetting;
import rogo.sketch.core.pipeline.RenderSettingCompiler;
import rogo.sketch.core.pipeline.flow.v2.ResourceGroupSlice;
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

public final class ResourceGroupCompiler {
    public List<ResourceGroupSlice> compile(
            CompiledRenderSetting compiledRenderSetting,
            Object sourceSlice,
            List<? extends Graphics> graphics) {
        List<ResourceGroupSlice> groups = new ArrayList<>();
        if (compiledRenderSetting == null || graphics == null || graphics.isEmpty()) {
            return groups;
        }

        ShaderProgramHandle shaderProvider = extractShaderProvider(compiledRenderSetting);
        if (shaderProvider == null || shaderProvider.uniformHooks() == null) {
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

        UniformHookGroup hookGroup = shaderProvider.uniformHooks();
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
            UniformGroupSet uniformGroups = UniformGroupSet.fromSnapshot(entry.getKey());
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

    private ShaderProgramHandle extractShaderProvider(CompiledRenderSetting compiledRenderSetting) {
        return ShaderProgramResolver.resolveProgramHandleIfAvailable(compiledRenderSetting);
    }
}

