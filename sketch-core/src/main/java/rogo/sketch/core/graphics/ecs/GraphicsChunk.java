package rogo.sketch.core.graphics.ecs;

import java.util.*;

final class GraphicsChunk {
    private final Set<GraphicsComponentType<?>> signature;
    private final int capacity;
    private final List<GraphicsEntityId> entities = new ArrayList<>();
    private final Map<GraphicsComponentType<?>, List<Object>> columns = new LinkedHashMap<>();

    GraphicsChunk(Set<GraphicsComponentType<?>> signature, int capacity) {
        this.signature = signature;
        this.capacity = capacity;
        for (GraphicsComponentType<?> componentType : signature) {
            columns.put(componentType, new ArrayList<>(capacity));
        }
    }

    boolean hasSpace() {
        return entities.size() < capacity;
    }

    GraphicsChunkPlacement append(GraphicsEntityId entityId, GraphicsEntityBlueprint blueprint) {
        int row = entities.size();
        entities.add(entityId);
        for (GraphicsComponentType<?> componentType : signature) {
            columns.get(componentType).add(blueprint.components().get(componentType));
        }
        return new GraphicsChunkPlacement(this, row);
    }

    GraphicsChunkRemoval removeSwap(int row) {
        int lastIndex = entities.size() - 1;
        GraphicsEntityId removedEntity = entities.get(row);
        GraphicsEntityId movedEntity = null;
        if (row != lastIndex) {
            movedEntity = entities.get(lastIndex);
            entities.set(row, movedEntity);
        }
        entities.remove(lastIndex);

        for (List<Object> column : columns.values()) {
            if (row != lastIndex) {
                column.set(row, column.get(lastIndex));
            }
            column.remove(lastIndex);
        }
        return new GraphicsChunkRemoval(removedEntity, movedEntity, row);
    }

    <T> T component(int row, GraphicsComponentType<T> componentType) {
        List<Object> column = columns.get(componentType);
        if (column == null) {
            return null;
        }
        return componentType.cast(column.get(row));
    }

    <T> void replace(int row, GraphicsComponentType<T> componentType, T value) {
        List<Object> column = columns.get(componentType);
        if (column == null) {
            throw new IllegalArgumentException("Chunk does not contain component " + componentType.id());
        }
        column.set(row, value);
    }

    List<GraphicsEntityId> entities() {
        return List.copyOf(entities);
    }

    record GraphicsChunkPlacement(GraphicsChunk chunk, int row) {
    }

    record GraphicsChunkRemoval(GraphicsEntityId removedEntity, GraphicsEntityId movedEntity, int movedRow) {
    }
}
