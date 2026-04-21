package rogo.sketch.core.graphics.ecs;

import rogo.sketch.core.util.KeyId;

import java.util.*;

/**
 * Chunked storage bucket for a single component signature.
 */
public final class GraphicsArchetype {
    private static final int DEFAULT_CHUNK_CAPACITY = 256;

    private final Set<GraphicsComponentType<?>> signature;
    private final Set<KeyId> componentIds;
    private final GraphicsCapabilityView capabilityView;
    private final List<GraphicsCapabilityDescriptor> capabilities;
    private final List<GraphicsAuthoringDescriptor> authoringDescriptors;
    private final List<GraphicsChunk> chunks = new ArrayList<>();
    private final int chunkCapacity;

    public GraphicsArchetype(Set<GraphicsComponentType<?>> signature) {
        this(signature, DEFAULT_CHUNK_CAPACITY);
    }

    public GraphicsArchetype(Set<GraphicsComponentType<?>> signature, int chunkCapacity) {
        this.signature = Collections.unmodifiableSet(new TreeSet<>(signature));
        Set<KeyId> componentIds = new LinkedHashSet<>();
        for (GraphicsComponentType<?> componentType : this.signature) {
            componentIds.add(componentType.id());
        }
        this.componentIds = Set.copyOf(componentIds);
        this.capabilityView = GraphicsCapabilityResolver.resolve(this.signature);
        this.capabilities = this.capabilityView.capabilities();
        this.authoringDescriptors = this.capabilityView.authoringDescriptors();
        this.chunkCapacity = chunkCapacity;
    }

    public Set<GraphicsComponentType<?>> signature() {
        return signature;
    }

    public List<GraphicsChunk> chunks() {
        return Collections.unmodifiableList(chunks);
    }

    GraphicsEntitySchema schema(GraphicsEntityId entityId) {
        return new GraphicsEntitySchema(
                entityId,
                componentIds,
                capabilityView,
                capabilities,
                authoringDescriptors);
    }

    GraphicsChunk.GraphicsChunkPlacement add(GraphicsEntityId entityId, GraphicsEntityBlueprint blueprint) {
        GraphicsChunk chunk = chunks.isEmpty() ? null : chunks.get(chunks.size() - 1);
        if (chunk == null || !chunk.hasSpace()) {
            chunk = new GraphicsChunk(signature, chunkCapacity);
            chunks.add(chunk);
        }
        return chunk.append(entityId, blueprint);
    }
}
