package rogo.sketch.core.vertex;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.data.Usage;
import rogo.sketch.core.data.builder.VertexStreamBuilder;
import rogo.sketch.core.data.format.ComponentSpec;
import rogo.sketch.core.data.format.DataFormat;
import rogo.sketch.core.data.format.VertexBufferKey;
import rogo.sketch.core.data.format.VertexLayoutSpec;
import rogo.sketch.core.pipeline.parmeter.RasterizationParameter;
import rogo.sketch.core.resource.buffer.VertexBufferObject;
import rogo.sketch.core.resource.buffer.VertexResource;
import rogo.sketch.core.util.KeyId;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for shared VertexResource instances.
 * Caches and manages the lifecycle of VAO/VBO combinations based on format
 * keys.
 */
public class VertexResourceManager {
    private static VertexResourceManager instance;
    private final Map<VertexBufferKey, VertexResource> resourceCache = new ConcurrentHashMap<>();
    private final Map<RasterizationParameter.BuilderKey, VertexStreamBuilder> builderCache = new ConcurrentHashMap<>();
    private final Map<RasterizationParameter.BuilderBatchKey, Map<KeyId, VertexStreamBuilder>> builderBatchCache = new ConcurrentHashMap<>();

    public VertexResourceManager() {
    }

    public static VertexResourceManager globalInstance() {
        if (instance == null) {
            instance = new VertexResourceManager();
        }
        return instance;
    }

    /**
     * Get or create a VertexResource for the given key.
     * Use this when the key might involve a shared source resource (e.g. BakedMesh)
     * that is needed for creation.
     */
    public VertexResource get(VertexBufferKey key, @Nullable VertexResource sourceProvider) {
        if (key == null)
            return null;

        return resourceCache.computeIfAbsent(key, k -> createVertexResource(k, sourceProvider));
    }

    /**
     * Get or create a VertexResource for the given key (Static Format + Instance
     * Layout).
     * WARNING: Must only be called from the OpenGL render thread.
     */
    public VertexResource get(VertexBufferKey key) {
        return get(key, null);
    }

    /**
     * Convenience method for non-instanced resources.
     */
    public VertexResource get(RasterizationParameter parameter) {
        return get(VertexBufferKey.fromParameter(parameter));
    }

    private VertexResource createVertexResource(VertexBufferKey key, @Nullable VertexResource sourceProvider) {
        RasterizationParameter param = key.renderParameter();
        PrimitiveType primitiveType = param.primitiveType();

        // 1. Create Base VAO
        VertexResource resource = new VertexResource(primitiveType, primitiveType.requiresIndexBuffer());

        // 2. Handle Shared Components (if source provided and key matches)
        if (key.sourceResourceID() > 0) {
            if (sourceProvider != null) {
                // Verify ID matches handle? (Assuming handle logic consistent)
                // if (sourceProvider.getHandle() == key.sourceResourceID()) ...
                // For now, trust the caller provided the correct source for this key.
                resource.shareComponentsFrom(sourceProvider);
                resource.setIndexBuffer(sourceProvider.getIndexBuffer());
            } else {
                // Warning: Key expects source but none provided.
                // This implies we are trying to create a shared-VAO but lost the source object.
                // We might fail or create empty? Log warning.
                System.err.println("Warning: Creating VertexResource for shared key " + key + " but no source provider given.");
            }
        }

        // 3. Attach VBO components based on ComponentSpec list
        // Note: shareComponentsFrom might have already attached static components.
        // We only need to attach components that are NOT already there, or override?
        // Actually, VertexBufferKey components list describes the FULL layout.
        // If we shared from source, we got the Immutable/Static ones.
        // We still need to create the Mutable/Dynamic ones.

        for (ComponentSpec spec : key.components()) {
            // shorter logic: check if component already exists (shared)?
            if (resource.hasComponent(spec.getId())) {
                continue;
            }

            if (spec.isImmutable()) {
                // Immutable component NOT provided by source?
                // This means fallback to legacy creation or valid new static buffer
                VertexBufferObject vbo = new VertexBufferObject(param.usage());
                resource.attachVBO(spec, vbo);
            } else {
                // Mutable component - create new VBO for data filling
                Usage usage = spec.isInstanced() ? Usage.DYNAMIC_DRAW : param.usage();
                VertexBufferObject vbo = new VertexBufferObject(usage);
                resource.attachVBO(spec, vbo);
            }
        }

        return resource;
    }

    public void remove(VertexBufferKey key) {
        VertexResource resource = resourceCache.remove(key);
        if (resource != null) {
            resource.dispose();
        }
    }

    public void clearAll() {
        resourceCache.values().forEach(VertexResource::dispose);
        resourceCache.clear();
    }

    /**
     * Create a VertexDataBuilder for a single DataFormat.
     */
    public VertexStreamBuilder createBuilder(DataFormat format, PrimitiveType primitiveType, boolean instanced) {
        return builderCache.computeIfAbsent(new RasterizationParameter.BuilderKey(format, primitiveType, instanced),
                k -> new VertexStreamBuilder(format, primitiveType));
    }

    /**
     * Create a VertexDataBuilder for a single ComponentSpec.
     */
    public VertexStreamBuilder createBuilder(ComponentSpec spec, PrimitiveType primitiveType) {
        return createBuilder(spec.getFormat(), primitiveType, spec.isInstanced());
    }

    /**
     * Create builders for all MUTABLE components in a VertexLayoutSpec.
     */
    public Map<KeyId, VertexStreamBuilder> createBuilder(VertexLayoutSpec spec, PrimitiveType primitiveType) {
        Map<KeyId, VertexStreamBuilder> builders = new ConcurrentHashMap<>();
        for (ComponentSpec component : spec.getDynamicSpecs()) {
            builders.put(component.getId(), createBuilder(component, primitiveType));
        }

        return builders;
    }

    /**
     * Create builders for a RasterizationParameter (based on its layout).
     */
    public Map<KeyId, VertexStreamBuilder> createBuilder(RasterizationParameter parameter) {
        return builderBatchCache.computeIfAbsent(parameter.builderBatchKey(), k -> createBuilder(parameter.getLayout(), parameter.primitiveType()));
    }

    public String getCacheStats() {
        return String.format("VertexResourceManager: %d cached resources", resourceCache.size());
    }
}