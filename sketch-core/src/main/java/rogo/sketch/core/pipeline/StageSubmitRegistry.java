package rogo.sketch.core.pipeline;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.pipeline.submit.StageSubmitNode;
import rogo.sketch.core.pipeline.submit.StageWindow;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class StageSubmitRegistry {
    private final Map<KeyId, EnumMap<StageWindow, List<StageSubmitNode>>> stageSubmitNodes = new LinkedHashMap<>();
    private final Map<String, List<StageSubmitNode>> ownedStageSubmitNodes = new LinkedHashMap<>();

    void onStageRegistered(KeyId stageId) {
        if (stageId != null) {
            stageSubmitNodes.computeIfAbsent(stageId, ignored -> new EnumMap<>(StageWindow.class));
        }
    }

    void clear() {
        stageSubmitNodes.clear();
        ownedStageSubmitNodes.clear();
    }

    void register(@Nullable String ownerId, StageSubmitNode node) {
        if (node == null || node.stageId() == null || node.window() == null) {
            return;
        }
        EnumMap<StageWindow, List<StageSubmitNode>> windows = stageSubmitNodes.computeIfAbsent(
                node.stageId(),
                ignored -> new EnumMap<>(StageWindow.class));
        List<StageSubmitNode> nodes = new ArrayList<>(windows.getOrDefault(node.window(), List.of()));
        nodes.add(node);
        nodes.sort(Comparator.comparingInt(StageSubmitNode::sortHint).thenComparing(n -> n.nodeId().toString()));
        windows.put(node.window(), List.copyOf(nodes));
        if (ownerId != null) {
            ownedStageSubmitNodes.computeIfAbsent(ownerId, ignored -> new ArrayList<>()).add(node);
        }
    }

    void unregisterOwned(@Nullable String ownerId) {
        if (ownerId == null) {
            return;
        }
        List<StageSubmitNode> ownedNodes = ownedStageSubmitNodes.remove(ownerId);
        if (ownedNodes == null || ownedNodes.isEmpty()) {
            return;
        }
        for (StageSubmitNode node : ownedNodes) {
            EnumMap<StageWindow, List<StageSubmitNode>> windows = stageSubmitNodes.get(node.stageId());
            if (windows == null) {
                continue;
            }
            List<StageSubmitNode> existing = windows.get(node.window());
            if (existing == null || existing.isEmpty()) {
                continue;
            }
            List<StageSubmitNode> updated = new ArrayList<>(existing);
            updated.removeIf(candidate -> candidate.nodeId().equals(node.nodeId()) && ownerId.equals(candidate.ownerId()));
            if (updated.isEmpty()) {
                windows.remove(node.window());
            } else {
                windows.put(node.window(), List.copyOf(updated));
            }
        }
    }

    List<StageSubmitNode> nodes(KeyId stageId, StageWindow window) {
        EnumMap<StageWindow, List<StageSubmitNode>> windows = stageSubmitNodes.get(stageId);
        if (windows == null) {
            return List.of();
        }
        return windows.getOrDefault(window, List.of());
    }
}
