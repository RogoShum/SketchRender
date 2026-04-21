package rogo.sketch.core.vertex;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.backend.BackendGeometryBinding;
import rogo.sketch.core.data.format.VertexBufferKey;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Owns backend geometry binding residency and pending materialization requests
 * for one pipeline residency domain.
 */
public final class MeshResidencyPool {
    private final String debugName;
    private final Map<MeshResidencyKey, BackendGeometryBinding> installedBindings = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<PendingResidencyRequest> pendingMaterialization = new ConcurrentLinkedQueue<>();

    public MeshResidencyPool(String debugName) {
        this.debugName = debugName != null ? debugName : "mesh-residency";
    }

    @Nullable
    public BackendGeometryBinding get(VertexBufferKey key, @Nullable BackendGeometryBinding sourceProvider) {
        if (key == null) {
            return null;
        }
        MeshResidencyKey residencyKey = MeshResidencyKey.from(key);
        BackendGeometryBinding existing = installedBindings.get(residencyKey);
        if (existing != null) {
            return existing;
        }
        planMaterialization(residencyKey, sourceProvider);
        return null;
    }

    @Nullable
    public BackendGeometryBinding get(VertexBufferKey key) {
        return get(key, null);
    }

    @Nullable
    public BackendGeometryBinding getIfPresent(VertexBufferKey key) {
        return key == null ? null : installedBindings.get(MeshResidencyKey.from(key));
    }

    public void planMaterialization(VertexBufferKey key, @Nullable BackendGeometryBinding sourceProvider) {
        if (key == null) {
            return;
        }
        planMaterialization(MeshResidencyKey.from(key), sourceProvider);
    }

    public void planMaterialization(MeshResidencyKey key, @Nullable BackendGeometryBinding sourceProvider) {
        if (key == null || installedBindings.containsKey(key)) {
            return;
        }
        pendingMaterialization.offer(new PendingResidencyRequest(key, sourceProvider));
    }

    public List<PendingResidencyRequest> drainPendingMaterializationRequests() {
        Map<MeshResidencyKey, PendingResidencyRequest> deduplicated = new LinkedHashMap<>();
        PendingResidencyRequest request;
        while ((request = pendingMaterialization.poll()) != null) {
            PendingResidencyRequest previous = deduplicated.get(request.key());
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
        MeshResidencyKey residencyKey = MeshResidencyKey.from(key);
        BackendGeometryBinding previous = installedBindings.put(residencyKey, geometryBinding);
        if (previous != null && previous != geometryBinding) {
            previous.dispose();
        }
    }

    public void remove(VertexBufferKey key) {
        if (key == null) {
            return;
        }
        BackendGeometryBinding binding = installedBindings.remove(MeshResidencyKey.from(key));
        if (binding != null) {
            binding.dispose();
        }
    }

    public void clearAll() {
        installedBindings.values().forEach(BackendGeometryBinding::dispose);
        installedBindings.clear();
        pendingMaterialization.clear();
    }

    public int installedBindingCount() {
        return installedBindings.size();
    }

    public int pendingRequestCount() {
        return pendingMaterialization.size();
    }

    public String debugName() {
        return debugName;
    }

    public String statsLine() {
        return String.format(
                "%s: %d installed bindings, %d pending requests",
                debugName,
                installedBindings.size(),
                pendingMaterialization.size());
    }

    public record PendingResidencyRequest(
            MeshResidencyKey key,
            @Nullable BackendGeometryBinding sourceProvider
    ) {
        public PendingResidencyRequest {
            Objects.requireNonNull(key, "key");
        }

        public VertexBufferKey vertexBufferKey() {
            return key.vertexBufferKey();
        }
    }
}
