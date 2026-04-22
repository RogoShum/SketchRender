package rogo.sketch.core.pipeline;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.pipeline.module.diagnostic.SketchDiagnostics;
import rogo.sketch.core.pipeline.submit.StageSubmitNode;
import rogo.sketch.core.pipeline.submit.StageWindow;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        if (hasDependencyCycle(nodes)) {
            SketchDiagnostics.get().error(
                    "stage-submit",
                    "Rejected stage submit node because reads/writes form a cycle: stage="
                            + node.stageId() + ", window=" + node.window() + ", node=" + node.nodeId(),
                    new IllegalStateException("Stage submit dependency cycle"));
            return;
        }
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

    private boolean hasDependencyCycle(List<StageSubmitNode> nodes) {
        if (nodes == null || nodes.size() < 2) {
            return false;
        }
        Map<StageSubmitNode, List<StageSubmitNode>> edges = new LinkedHashMap<>();
        for (StageSubmitNode source : nodes) {
            edges.put(source, new ArrayList<>());
        }
        for (StageSubmitNode source : nodes) {
            for (StageSubmitNode target : nodes) {
                if (source == target) {
                    continue;
                }
                if (intersects(source.writes(), target.reads())) {
                    edges.get(source).add(target);
                }
            }
        }
        Set<StageSubmitNode> visiting = new LinkedHashSet<>();
        Set<StageSubmitNode> visited = new LinkedHashSet<>();
        for (StageSubmitNode node : nodes) {
            if (hasDependencyCycle(node, edges, visiting, visited)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasDependencyCycle(
            StageSubmitNode node,
            Map<StageSubmitNode, List<StageSubmitNode>> edges,
            Set<StageSubmitNode> visiting,
            Set<StageSubmitNode> visited) {
        if (visited.contains(node)) {
            return false;
        }
        if (!visiting.add(node)) {
            return true;
        }
        for (StageSubmitNode next : edges.getOrDefault(node, List.of())) {
            if (hasDependencyCycle(next, edges, visiting, visited)) {
                return true;
            }
        }
        visiting.remove(node);
        visited.add(node);
        return false;
    }

    private boolean intersects(List<KeyId> first, List<KeyId> second) {
        if (first == null || second == null || first.isEmpty() || second.isEmpty()) {
            return false;
        }
        Set<KeyId> keys = new LinkedHashSet<>(first);
        for (KeyId key : second) {
            if (keys.contains(key)) {
                return true;
            }
        }
        return false;
    }
}
