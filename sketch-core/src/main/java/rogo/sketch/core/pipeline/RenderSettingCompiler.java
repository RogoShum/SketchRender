package rogo.sketch.core.pipeline;

import rogo.sketch.core.driver.state.component.ShaderState;
import rogo.sketch.core.driver.state.component.RenderTargetState;
import rogo.sketch.core.driver.state.CompiledRenderState;
import rogo.sketch.core.driver.state.RenderStatePatch;
import rogo.sketch.core.driver.state.RenderStateCompiler;
import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.packet.ComputePipelineKey;
import rogo.sketch.core.packet.ExecutionDomain;
import rogo.sketch.core.packet.ExecutionKey;
import rogo.sketch.core.packet.OffscreenGraphicsPipelineKey;
import rogo.sketch.core.packet.TransferPlanKey;
import rogo.sketch.core.packet.RasterPipelineKey;
import rogo.sketch.core.packet.ResourceBindingPlan;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.ResourceAccess;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.resource.ResourceViewRole;
import rogo.sketch.core.resource.descriptor.ImageUsage;
import rogo.sketch.core.resource.descriptor.ResolvedImageResource;
import rogo.sketch.core.resource.vision.Texture;
import rogo.sketch.core.shader.variant.ShaderTemplate;
import rogo.sketch.core.shader.variant.ShaderVariantKey;
import rogo.sketch.core.util.KeyId;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class RenderSettingCompiler {
    private static final KeyId UNBOUND_SHADER = KeyId.of("sketch:unbound_shader");
    private static final KeyId EMPTY_VERTEX_LAYOUT = KeyId.of("sketch:empty_vertex_layout");

    private RenderSettingCompiler() {
    }

    public static CompiledRenderSetting compile(RenderSetting renderSetting) {
        return compile(renderSetting, null);
    }

    public static CompiledRenderSetting compile(
            RenderSetting renderSetting,
            @Nullable GraphicsResourceManager resourceManager) {
        return compile(renderSetting, resourceManager, ShaderVariantKey.EMPTY);
    }

    public static CompiledRenderSetting compile(
            RenderSetting renderSetting,
            @Nullable GraphicsResourceManager resourceManager,
            @Nullable ShaderVariantKey stageVariantKey) {
        RenderSetting setting = renderSetting != null
                ? renderSetting
                : RenderSetting.fromPartial(null, PartialRenderSetting.EMPTY);
        RenderStatePatch effectiveRenderState = applyTargetBinding(setting);
        TargetBindingDescriptor targetBindingDescriptor = TargetBindingDescriptor.from(setting.targetBinding());
        ShaderState shaderState = extractShaderState(effectiveRenderState);
        ShaderVariantKey effectiveShaderVariant = resolveEffectiveShaderVariant(shaderState, stageVariantKey);
        effectiveRenderState = applyShaderVariant(
                effectiveRenderState,
                shaderState,
                effectiveShaderVariant,
                resourceManager);
        ShaderState effectiveShaderState = extractShaderState(effectiveRenderState);
        CompiledRenderState compiledRenderState = RenderStateCompiler.compile(effectiveRenderState);

        ResourceBindingPlan bindingPlan = ResourceBindingPlan.from(
                setting.resourceBinding(),
                resolveShaderResourceBindings(effectiveShaderState, resourceManager));
        validateShaderBindingSlots(bindingPlan, effectiveShaderState, resourceManager);
        validateResourceAccessConflicts(bindingPlan, setting.aliasPolicy());
        validateExplicitImageUsage(bindingPlan, resourceManager);

        PipelineStateDescriptor pipelineDescriptor = new PipelineStateDescriptor(
                setting.renderParameter(),
                effectiveRenderState,
                compiledRenderState,
                setting.shouldSwitchRenderState(),
                effectiveShaderState != null ? effectiveShaderState.getShaderId() : UNBOUND_SHADER,
                effectiveShaderState != null ? effectiveShaderVariant : ShaderVariantKey.EMPTY,
                setting.renderParameter() != null && setting.renderParameter().getLayout() != null
                        ? KeyId.of("sketch:vertex_layout_" + Integer.toHexString(setting.renderParameter().getLayout().hashCode()))
                        : EMPTY_VERTEX_LAYOUT,
                targetBindingDescriptor.passCompatibilityKey(),
                bindingPlan.layoutKey(),
                compiledRenderState.pipelineRasterState() != null ? compiledRenderState.pipelineRasterState().hashCode() : 0);

        ExecutionKey stateKey = switch (setting.executionDomain()) {
            case COMPUTE -> new ComputePipelineKey(
                    effectiveRenderState,
                    rogo.sketch.core.driver.state.ComputeStateCompiler.compile(effectiveRenderState),
                    bindingPlan,
                    setting.shouldSwitchRenderState(),
                    effectiveShaderState != null ? effectiveShaderState.getShaderId() : UNBOUND_SHADER,
                    effectiveShaderState != null ? effectiveShaderVariant : ShaderVariantKey.EMPTY,
                    bindingPlan.layoutKey());
            case OFFSCREEN_GRAPHICS -> new OffscreenGraphicsPipelineKey(
                    setting.renderParameter(),
                    effectiveRenderState,
                    rogo.sketch.core.driver.state.RasterStateCompiler.compile(effectiveRenderState),
                    bindingPlan,
                    setting.shouldSwitchRenderState(),
                    effectiveShaderState != null ? effectiveShaderState.getShaderId() : UNBOUND_SHADER,
                    effectiveShaderState != null ? effectiveShaderVariant : ShaderVariantKey.EMPTY,
                    RasterPipelineKey.deriveVertexLayoutKey(setting.renderParameter()),
                    targetBindingDescriptor != null ? targetBindingDescriptor.renderTargetId() : PipelineConfig.DEFAULT_RENDER_TARGET_ID,
                    bindingPlan.layoutKey());
            case TRANSFER -> targetBindingDescriptor != null && targetBindingDescriptor.passCompatibilityKey() != null
                    ? TransferPlanKey.forRenderTarget(targetBindingDescriptor.passCompatibilityKey())
                    : TransferPlanKey.of(KeyId.of("sketch:transfer_plan"));
            case RASTER -> new RasterPipelineKey(
                    setting.renderParameter(),
                    effectiveRenderState,
                    rogo.sketch.core.driver.state.RasterStateCompiler.compile(effectiveRenderState),
                    bindingPlan,
                    setting.shouldSwitchRenderState(),
                    effectiveShaderState != null ? effectiveShaderState.getShaderId() : UNBOUND_SHADER,
                    effectiveShaderState != null ? effectiveShaderVariant : ShaderVariantKey.EMPTY);
        };
        PipelineStateDescriptor descriptorForSetting = setting.executionDomain() == ExecutionDomain.RASTER
                || setting.executionDomain() == ExecutionDomain.OFFSCREEN_GRAPHICS
                ? pipelineDescriptor
                : null;
        return new CompiledRenderSetting(
                setting,
                descriptorForSetting,
                ResourceBindingDescriptor.from(bindingPlan),
                targetBindingDescriptor,
                bindingPlan,
                stateKey);
    }

    private static ShaderState extractShaderState(@Nullable RenderStatePatch renderState) {
        if (renderState != null && renderState.get(ShaderState.TYPE) instanceof ShaderState state) {
            return state;
        }
        return null;
    }

    private static ShaderVariantKey resolveEffectiveShaderVariant(
            @Nullable ShaderState shaderState,
            @Nullable ShaderVariantKey stageVariantKey) {
        if (shaderState == null) {
            return ShaderVariantKey.EMPTY;
        }
        ShaderVariantKey baseVariant = shaderState.getVariantKey() != null
                ? shaderState.getVariantKey()
                : ShaderVariantKey.EMPTY;
        return baseVariant.merge(stageVariantKey);
    }

    private static RenderStatePatch applyShaderVariant(
            @Nullable RenderStatePatch renderState,
            @Nullable ShaderState shaderState,
            ShaderVariantKey effectiveShaderVariant,
            @Nullable GraphicsResourceManager resourceManager) {
        if (shaderState == null || effectiveShaderVariant == null) {
            return renderState;
        }
        if (Objects.equals(shaderState.getVariantKey(), effectiveShaderVariant)) {
            return renderState;
        }
        String[] flags = effectiveShaderVariant.getFlags().toArray(String[]::new);
        ShaderState effectiveShaderState = new ShaderState(resourceManager, shaderState.getShaderId(), flags);
        return (renderState != null ? renderState : RenderStatePatch.empty()).with(effectiveShaderState);
    }

    private static Map<KeyId, Map<KeyId, Integer>> resolveShaderResourceBindings(
            ShaderState shaderState,
            @Nullable GraphicsResourceManager resourceManager) {
        if (shaderState == null || shaderState.getShaderId() == null) {
            return Collections.emptyMap();
        }
        ShaderTemplate template = resolveShaderTemplate(shaderState, resourceManager);
        if (template == null || template.isDisposed()) {
            return Collections.emptyMap();
        }
        try {
            return template.resolveResourceBindings(shaderState.getVariantKey());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to resolve shader resource bindings for " + shaderState.getShaderId(), e);
        }
    }

    private static void validateShaderBindingSlots(
            ResourceBindingPlan bindingPlan,
            ShaderState shaderState,
            @Nullable GraphicsResourceManager resourceManager) {
        if (bindingPlan == null || bindingPlan.isEmpty() || shaderState == null) {
            return;
        }
        ShaderTemplate template = resolveShaderTemplate(shaderState, resourceManager);
        if (template == null || !template.hasDeclaredResourceBindings()) {
            return;
        }
        for (ResourceBindingPlan.BindingEntry entry : bindingPlan.entries()) {
            if (entry.bindingSlot() < 0) {
                throw new IllegalArgumentException(
                        "Render setting binds " + entry.resourceType() + "." + entry.bindingName()
                                + " but shader_template " + shaderState.getShaderId()
                                + " does not declare that binding in resourceBindings");
            }
        }
    }

    private static void validateResourceAccessConflicts(ResourceBindingPlan bindingPlan, String aliasPolicy) {
        if (bindingPlan == null || bindingPlan.isEmpty() || allowsReadWriteAlias(aliasPolicy)) {
            return;
        }
        HashMap<KeyId, AccessSummary> accessByResource = new HashMap<>();
        for (ResourceBindingPlan.BindingEntry entry : bindingPlan.entries()) {
            if (entry == null || entry.resourceId() == null) {
                continue;
            }
            AccessSummary summary = accessByResource.computeIfAbsent(entry.resourceId(), ignored -> new AccessSummary());
            ResourceViewRole viewRole = entry.viewRole();
            ResourceAccess access = entry.access();
            if (viewRole == ResourceViewRole.SAMPLED_TEXTURE && access.reads()) {
                summary.sampledRead = true;
            }
            if (viewRole == ResourceViewRole.STORAGE_IMAGE && access.writes()) {
                summary.storageWrite = true;
            }
            if (summary.sampledRead && summary.storageWrite) {
                throw new IllegalArgumentException(
                        "Render setting reads and writes the same texture resource in one pass without aliasPolicy: "
                                + entry.resourceId()
                                + " (sampled texture READ + storage image WRITE/READ_WRITE)");
            }
        }
    }

    private static boolean allowsReadWriteAlias(String aliasPolicy) {
        if (aliasPolicy == null || aliasPolicy.isBlank()) {
            return false;
        }
        String normalized = aliasPolicy.trim().toUpperCase(Locale.ROOT);
        return "HIZ_ATLAS_READ_WRITE".equals(normalized) || "ALLOW_RESOURCE_ALIASING".equals(normalized);
    }

    private static void validateExplicitImageUsage(
            ResourceBindingPlan bindingPlan,
            @Nullable GraphicsResourceManager resourceManager) {
        if (bindingPlan == null || bindingPlan.isEmpty() || resourceManager == null) {
            return;
        }
        for (ResourceBindingPlan.BindingEntry entry : bindingPlan.entries()) {
            ImageUsage requiredUsage = requiredImageUsage(entry);
            if (requiredUsage == null) {
                continue;
            }
            Object resource = resourceManager.getResource(ResourceTypes.TEXTURE, entry.resourceId());
            if (!(resource instanceof Texture texture) || texture.descriptor() == null) {
                continue;
            }
            ResolvedImageResource descriptor = texture.descriptor();
            if (!descriptor.usagesExplicitlyDeclared()) {
                continue;
            }
            if (!descriptor.supports(requiredUsage)) {
                throw new IllegalArgumentException(
                        "Texture " + entry.resourceId()
                                + " explicitly declares usages " + descriptor.usages()
                                + " but render setting binding " + entry.bindingName()
                                + " requires " + requiredUsage
                                + " for view=" + entry.viewRole()
                                + ", access=" + entry.access());
            }
        }
    }

    private static ImageUsage requiredImageUsage(ResourceBindingPlan.BindingEntry entry) {
        if (entry == null) {
            return null;
        }
        return switch (entry.viewRole()) {
            case SAMPLED_TEXTURE -> ImageUsage.SAMPLED;
            case STORAGE_IMAGE -> ImageUsage.STORAGE;
            case TRANSFER_SRC -> ImageUsage.TRANSFER_SRC;
            case TRANSFER_DST -> ImageUsage.TRANSFER_DST;
            case ATTACHMENT -> null;
            case UNIFORM_BUFFER, STORAGE_BUFFER -> null;
        };
    }

    private static final class AccessSummary {
        private boolean sampledRead;
        private boolean storageWrite;
    }

    private static ShaderTemplate resolveShaderTemplate(
            ShaderState shaderState,
            @Nullable GraphicsResourceManager resourceManager) {
        if (shaderState == null || shaderState.getShaderId() == null) {
            return null;
        }
        if (shaderState.getTemplate() != null && shaderState.getTemplate().isAvailable()) {
            return shaderState.getTemplate().get();
        }
        return resourceManager != null
                ? resourceManager.getResource(ResourceTypes.SHADER_TEMPLATE, shaderState.getShaderId())
                : null;
    }

    private static rogo.sketch.core.driver.state.RenderStatePatch applyTargetBinding(RenderSetting setting) {
        if (setting == null) {
            return PartialRenderSetting.EMPTY.renderState();
        }
        var renderState = setting.renderState();
        return switch (setting.executionDomain()) {
            case RASTER, OFFSCREEN_GRAPHICS -> {
                TargetBinding targetBinding = setting.targetBinding();
                if (targetBinding == null) {
                    yield renderState;
                }
                yield (renderState != null ? renderState : rogo.sketch.core.driver.state.RenderStatePatch.empty())
                        .with(new RenderTargetState(targetBinding.renderTargetId(), targetBinding.drawBuffers()));
            }
            case COMPUTE, TRANSFER -> renderState;
        };
    }
}
