package rogo.sketch.backend.vulkan;

import org.lwjgl.vulkan.VkDevice;
import rogo.sketch.core.pipeline.module.diagnostic.SketchDiagnostics;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.shader.ShaderType;
import rogo.sketch.core.shader.variant.ShaderTemplate;
import rogo.sketch.core.shader.variant.ShaderVariantKey;
import rogo.sketch.core.shader.variant.ShaderVariantSpec;
import rogo.sketch.core.util.KeyId;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.vkDestroyShaderModule;

final class VulkanShaderVariantCache {
    private static final String DIAG_MODULE = "vulkan-shader-variant-cache";

    private final VkDevice device;
    private final Map<GraphicsVariantKey, GraphicsVariantModules> graphicsVariants = new ConcurrentHashMap<>();
    private final Map<ComputeVariantKey, ComputeVariantModules> computeVariants = new ConcurrentHashMap<>();

    VulkanShaderVariantCache(VkDevice device) {
        this.device = device;
    }

    GraphicsVariantModules resolveGraphicsVariant(KeyId shaderTemplateId, ShaderVariantKey variantKey, KeyId resourceLayoutKey) {
        ShaderVariantSpec spec = resolveVariantSpec(shaderTemplateId, variantKey);
        if (spec == null) {
            return null;
        }

        if (!spec.processedSources().containsKey(ShaderType.VERTEX) || !spec.processedSources().containsKey(ShaderType.FRAGMENT)) {
            SketchDiagnostics.get().warn(
                    DIAG_MODULE,
                    "Vulkan graphics pipeline requires vertex+fragment stages: " + shaderTemplateId);
            return null;
        }
        if (spec.processedSources().containsKey(ShaderType.GEOMETRY)
                || spec.processedSources().containsKey(ShaderType.TESS_CONTROL)
                || spec.processedSources().containsKey(ShaderType.TESS_EVALUATION)
                || spec.processedSources().containsKey(ShaderType.COMPUTE)) {
            SketchDiagnostics.get().warn(
                    DIAG_MODULE,
                    "Vulkan graphics pipeline does not yet support geometry/tess/compute mix for " + shaderTemplateId);
            return null;
        }

        GraphicsVariantKey cacheKey = new GraphicsVariantKey(
                shaderTemplateId,
                variantKey != null ? variantKey : ShaderVariantKey.EMPTY,
                spec.activeVertexLayout().hashCode(),
                resourceLayoutKey != null ? resourceLayoutKey : KeyId.of("sketch:empty_resource_layout"));
        return graphicsVariants.computeIfAbsent(cacheKey, ignored -> createGraphicsVariant(spec));
    }

    ComputeVariantModules resolveComputeVariant(KeyId shaderTemplateId, ShaderVariantKey variantKey, KeyId resourceLayoutKey) {
        if (shaderTemplateId == null) {
            return null;
        }

        ShaderVariantSpec spec = resolveVariantSpec(shaderTemplateId, variantKey);
        if (spec == null) {
            return null;
        }

        if (!spec.processedSources().containsKey(ShaderType.COMPUTE)) {
            SketchDiagnostics.get().warn(
                    DIAG_MODULE,
                    "Vulkan compute pipeline requires compute stage: " + shaderTemplateId);
            return null;
        }
        if (spec.processedSources().containsKey(ShaderType.VERTEX)
                || spec.processedSources().containsKey(ShaderType.FRAGMENT)
                || spec.processedSources().containsKey(ShaderType.GEOMETRY)
                || spec.processedSources().containsKey(ShaderType.TESS_CONTROL)
                || spec.processedSources().containsKey(ShaderType.TESS_EVALUATION)) {
            SketchDiagnostics.get().warn(
                    DIAG_MODULE,
                    "Vulkan compute pipeline requires compute-only stages: " + shaderTemplateId);
            return null;
        }

        ComputeVariantKey cacheKey = new ComputeVariantKey(
                shaderTemplateId,
                variantKey != null ? variantKey : ShaderVariantKey.EMPTY,
                resourceLayoutKey != null ? resourceLayoutKey : KeyId.of("sketch:empty_resource_layout"));
        return computeVariants.computeIfAbsent(cacheKey, ignored -> createComputeVariant(spec));
    }

    void destroy() {
        for (GraphicsVariantModules modules : graphicsVariants.values()) {
            if (modules.vertexShaderModule() != VK_NULL_HANDLE) {
                vkDestroyShaderModule(device, modules.vertexShaderModule(), null);
            }
            if (modules.fragmentShaderModule() != VK_NULL_HANDLE) {
                vkDestroyShaderModule(device, modules.fragmentShaderModule(), null);
            }
        }
        graphicsVariants.clear();
        for (ComputeVariantModules modules : computeVariants.values()) {
            if (modules.computeShaderModule() != VK_NULL_HANDLE) {
                vkDestroyShaderModule(device, modules.computeShaderModule(), null);
            }
        }
        computeVariants.clear();
    }

    private GraphicsVariantModules createGraphicsVariant(ShaderVariantSpec spec) {
        long vertexModule = VK_NULL_HANDLE;
        long fragmentModule = VK_NULL_HANDLE;
        try {
            VulkanShaderInterfaceDecorator.DecoratedSources decoratedSources =
                    VulkanShaderInterfaceDecorator.decorateGraphicsVariant(spec);
            vertexModule = VulkanShaderCompiler.createShaderModule(
                    device,
                    ShaderType.VERTEX,
                    decoratedSources.vertexSource(),
                    spec.templateId() + "#" + spec.variantKey().toCompactString() + ".vert");
            fragmentModule = VulkanShaderCompiler.createShaderModule(
                    device,
                    ShaderType.FRAGMENT,
                    decoratedSources.fragmentSource(),
                    spec.templateId() + "#" + spec.variantKey().toCompactString() + ".frag");
            return new GraphicsVariantModules(spec, vertexModule, fragmentModule);
        } catch (RuntimeException ex) {
            if (fragmentModule != VK_NULL_HANDLE) {
                vkDestroyShaderModule(device, fragmentModule, null);
            }
            if (vertexModule != VK_NULL_HANDLE) {
                vkDestroyShaderModule(device, vertexModule, null);
            }
            SketchDiagnostics.get().error(
                    DIAG_MODULE,
                    "Failed to compile Vulkan graphics shaders for " + spec.templateId() + " / " + spec.variantKey(),
                    ex);
            return new GraphicsVariantModules(spec, VK_NULL_HANDLE, VK_NULL_HANDLE);
        }
    }

    private ComputeVariantModules createComputeVariant(ShaderVariantSpec spec) {
        long computeModule = VK_NULL_HANDLE;
        try {
            String decoratedComputeSource = VulkanShaderInterfaceDecorator.decorateComputeVariant(spec);
            computeModule = VulkanShaderCompiler.createShaderModule(
                    device,
                    ShaderType.COMPUTE,
                    decoratedComputeSource,
                    spec.templateId() + "#" + spec.variantKey().toCompactString() + ".comp");
            return new ComputeVariantModules(spec, computeModule);
        } catch (RuntimeException ex) {
            if (computeModule != VK_NULL_HANDLE) {
                vkDestroyShaderModule(device, computeModule, null);
            }
            SketchDiagnostics.get().error(
                    DIAG_MODULE,
                    "Failed to compile Vulkan compute shader for " + spec.templateId() + " / " + spec.variantKey(),
                    ex);
            return new ComputeVariantModules(spec, VK_NULL_HANDLE);
        }
    }

    private ShaderVariantSpec resolveVariantSpec(KeyId shaderTemplateId, ShaderVariantKey variantKey) {
        if (shaderTemplateId == null) {
            return null;
        }
        Object resource = GraphicsResourceManager.getInstance().getResource(ResourceTypes.SHADER_TEMPLATE, shaderTemplateId);
        if (!(resource instanceof ShaderTemplate shaderTemplate)) {
            SketchDiagnostics.get().warn(
                    DIAG_MODULE,
                    "Missing ShaderTemplate for Vulkan pipeline: " + shaderTemplateId);
            return null;
        }

        try {
            return shaderTemplate.resolveVariantSpec(variantKey);
        } catch (IOException e) {
            SketchDiagnostics.get().error(
                    DIAG_MODULE,
                    "Failed to resolve shader variant spec for " + shaderTemplateId + " / " + variantKey,
                    e);
            return null;
        }
    }

    private record GraphicsVariantKey(
            KeyId shaderTemplateId,
            ShaderVariantKey variantKey,
            int activeLayoutHash,
            KeyId resourceLayoutKey
    ) {
    }

    private record ComputeVariantKey(
            KeyId shaderTemplateId,
            ShaderVariantKey variantKey,
            KeyId resourceLayoutKey
    ) {
    }

    record GraphicsVariantModules(
            ShaderVariantSpec spec,
            long vertexShaderModule,
            long fragmentShaderModule
    ) {
        boolean isValid() {
            return vertexShaderModule != VK_NULL_HANDLE && fragmentShaderModule != VK_NULL_HANDLE;
        }
    }

    record ComputeVariantModules(
            ShaderVariantSpec spec,
            long computeShaderModule
    ) {
        boolean isValid() {
            return computeShaderModule != VK_NULL_HANDLE;
        }
    }
}

