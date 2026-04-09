package rogo.sketch.core.vertex;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.backend.BackendGeometryBinding;
import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.data.builder.VertexRecordWriter;
import rogo.sketch.core.data.format.ComponentSpec;
import rogo.sketch.core.data.format.VertexBufferKey;
import rogo.sketch.core.data.format.VertexLayoutSpec;
import rogo.sketch.core.data.layout.StructLayout;
import rogo.sketch.core.pipeline.parmeter.RasterizationParameter;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Coordinates geometry builder allocation plus backend-owned geometry binding
 * materialization requests.
 */
public class GeometryResourceCoordinator {
    private static GeometryResourceCoordinator instance;

    private final Map<VertexBufferKey, BackendGeometryBinding> installedBindings = new ConcurrentHashMap<>();
    private final Map<RasterizationParameter.BuilderKey, BuilderFactory> builderCache = new ConcurrentHashMap<>();
    private final Map<RasterizationParameter.BuilderBatchKey, BuilderBlueprint[]> builderBatchCache = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<PendingGeometryBindingRequest> pendingMaterialization = new ConcurrentLinkedQueue<>();

    public GeometryResourceCoordinator() {
    }

    public static GeometryResourceCoordinator globalInstance() {
        if (instance == null) {
            instance = new GeometryResourceCoordinator();
        }
        return instance;
    }

    public BackendGeometryBinding get(VertexBufferKey key, @Nullable BackendGeometryBinding sourceProvider) {
        if (key == null) {
            return null;
        }
        BackendGeometryBinding existing = installedBindings.get(key);
        if (existing != null) {
            return existing;
        }
        planMaterialization(key, sourceProvider);
        return null;
    }

    public BackendGeometryBinding get(VertexBufferKey key) {
        return get(key, null);
    }

    public BackendGeometryBinding get(RasterizationParameter parameter) {
        return get(VertexBufferKey.fromParameter(parameter));
    }

    public BackendGeometryBinding getIfPresent(VertexBufferKey key) {
        return key == null ? null : installedBindings.get(key);
    }

    public void planMaterialization(VertexBufferKey key, @Nullable BackendGeometryBinding sourceProvider) {
        if (key == null || installedBindings.containsKey(key)) {
            return;
        }
        pendingMaterialization.offer(new PendingGeometryBindingRequest(key, sourceProvider));
    }

    public List<PendingGeometryBindingRequest> drainPendingMaterializationRequests() {
        Map<VertexBufferKey, PendingGeometryBindingRequest> deduplicated = new LinkedHashMap<>();
        PendingGeometryBindingRequest request;
        while ((request = pendingMaterialization.poll()) != null) {
            PendingGeometryBindingRequest previous = deduplicated.get(request.key());
            if (previous == null || (previous.sourceProvider() == null && request.sourceProvider() != null)) {
                deduplicated.put(request.key(), request);
            }
        }
        return List.copyOf(deduplicated.values());
    }

    public void registerInstalledBinding(VertexBufferKey key, BackendGeometryBinding geometryBinding) {
        if (key == null || geometryBinding == null) {
            return;
        }
        BackendGeometryBinding previous = installedBindings.put(key, geometryBinding);
        if (previous != null && previous != geometryBinding) {
            previous.dispose();
        }
    }

    public void remove(VertexBufferKey key) {
        BackendGeometryBinding resource = installedBindings.remove(key);
        if (resource != null) {
            resource.dispose();
        }
    }

    public void clearAll() {
        installedBindings.values().forEach(BackendGeometryBinding::dispose);
        installedBindings.clear();
        pendingMaterialization.clear();
    }

    public VertexRecordWriter createBuilder(StructLayout format, PrimitiveType primitiveType, boolean instanced) {
        BuilderFactory factory = builderCache.computeIfAbsent(
                new RasterizationParameter.BuilderKey(format, primitiveType, instanced),
                key -> new BuilderFactory(key.format(), key.primitiveType(), key.instanced()));
        return factory.create();
    }

    public VertexRecordWriter createBuilder(ComponentSpec spec, PrimitiveType primitiveType) {
        return createBuilder(spec.getFormat(), primitiveType, spec.isInstanced());
    }

    public BuilderPair[] createBuilder(VertexLayoutSpec spec, PrimitiveType primitiveType) {
        BuilderPair[] builders = new BuilderPair[spec.getDynamicSpecs().length];
        for (int i = 0; i < spec.getDynamicSpecs().length; ++i) {
            ComponentSpec component = spec.getDynamicSpecs()[i];
            builders[i] = new BuilderPair(component.getId(), createBuilder(component, primitiveType), component.isTickUpdate());
        }
        return builders;
    }

    public BuilderPair[] createBuilder(RasterizationParameter parameter) {
        BuilderBlueprint[] blueprints = builderBatchCache.computeIfAbsent(
                parameter.builderBatchKey(),
                key -> createBuilderBlueprints(parameter.getLayout(), parameter.primitiveType()));
        BuilderPair[] builders = new BuilderPair[blueprints.length];
        for (int i = 0; i < blueprints.length; i++) {
            builders[i] = blueprints[i].create();
        }
        return builders;
    }

    private BuilderBlueprint[] createBuilderBlueprints(VertexLayoutSpec spec, PrimitiveType primitiveType) {
        BuilderBlueprint[] builders = new BuilderBlueprint[spec.getDynamicSpecs().length];
        for (int i = 0; i < spec.getDynamicSpecs().length; ++i) {
            ComponentSpec component = spec.getDynamicSpecs()[i];
            BuilderFactory factory = new BuilderFactory(component.getFormat(), primitiveType, component.isInstanced());
            builders[i] = new BuilderBlueprint(component.getId(), factory, component.isTickUpdate());
        }
        return builders;
    }

    public static BuilderPair[] snapshotBuilders(BuilderPair[] builders) {
        if (builders == null || builders.length == 0) {
            return new BuilderPair[0];
        }
        BuilderPair[] snapshot = new BuilderPair[builders.length];
        for (int i = 0; i < builders.length; i++) {
            BuilderPair builder = builders[i];
            if (builder == null) {
                continue;
            }
            snapshot[i] = new BuilderPair(
                    builder.key(),
                    builder.builder() != null ? builder.builder().snapshotCopy() : null,
                    builder.tickUpdate());
        }
        return snapshot;
    }

    public String getCacheStats() {
        return String.format(
                "GeometryResourceCoordinator: %d installed bindings, %d pending requests",
                installedBindings.size(),
                pendingMaterialization.size());
    }

    public record BuilderPair(KeyId key, VertexRecordWriter builder, boolean tickUpdate) {
    }

    public record PendingGeometryBindingRequest(VertexBufferKey key, @Nullable BackendGeometryBinding sourceProvider) {
    }

    private record BuilderFactory(StructLayout format, PrimitiveType primitiveType, boolean instanced) {
        private VertexRecordWriter create() {
            return new VertexRecordWriter(format, primitiveType);
        }
    }

    private record BuilderBlueprint(KeyId key, BuilderFactory factory, boolean tickUpdate) {
        private BuilderPair create() {
            return new BuilderPair(key, factory.create(), tickUpdate);
        }
    }
}
