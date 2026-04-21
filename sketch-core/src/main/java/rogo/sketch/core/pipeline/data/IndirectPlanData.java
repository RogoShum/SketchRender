package rogo.sketch.core.pipeline.data;

import rogo.sketch.core.graphics.ecs.GraphicsUniformSubject;
import rogo.sketch.core.pipeline.indirect.IndirectPlanRequest;
import rogo.sketch.core.pipeline.indirect.IndirectRewriteResult;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IndirectPlanData implements RenderPipelineData {
    public static final KeyId KEY = KeyId.of("indirect_plan_data");

    private final Map<KeyId, Map<KeyId, IndirectPlanRequest>> requestsByStage = new ConcurrentHashMap<>();
    private final Map<KeyId, Map<KeyId, IndirectRewriteResult>> resultsByStage = new ConcurrentHashMap<>();

    public void request(IndirectPlanRequest request) {
        if (request == null) {
            return;
        }
        requestsByStage
                .computeIfAbsent(request.stageId(), ignored -> new ConcurrentHashMap<>())
                .put(request.graphicsId(), request);
    }

    public void request(KeyId stageId, KeyId graphicsId, IndirectPlanRequest.RequestMode requestMode) {
        request(new IndirectPlanRequest(stageId, graphicsId, requestMode));
    }

    public IndirectPlanRequest requestFor(KeyId stageId, KeyId graphicsId) {
        if (stageId == null || graphicsId == null) {
            return null;
        }
        Map<KeyId, IndirectPlanRequest> stageRequests = requestsByStage.get(stageId);
        return stageRequests != null ? stageRequests.get(graphicsId) : null;
    }

    public IndirectPlanRequest firstRequest(KeyId stageId, Collection<GraphicsUniformSubject> subjects) {
        if (stageId == null || subjects == null || subjects.isEmpty()) {
            return null;
        }
        for (GraphicsUniformSubject subject : subjects) {
            if (subject == null || subject.identifier() == null) {
                continue;
            }
            IndirectPlanRequest request = requestFor(stageId, subject.identifier());
            if (request != null) {
                return request;
            }
        }
        return null;
    }

    public boolean hasAnyRequest(KeyId stageId, Collection<GraphicsUniformSubject> subjects) {
        return firstRequest(stageId, subjects) != null;
    }

    public void recordResult(IndirectRewriteResult result) {
        if (result == null) {
            return;
        }
        resultsByStage
                .computeIfAbsent(result.stageId(), ignored -> new ConcurrentHashMap<>())
                .put(result.graphicsId(), result);
    }

    public List<IndirectRewriteResult> results(KeyId stageId) {
        Map<KeyId, IndirectRewriteResult> stageResults = resultsByStage.get(stageId);
        if (stageResults == null || stageResults.isEmpty()) {
            return List.of();
        }
        return List.copyOf(stageResults.values());
    }

    public List<IndirectRewriteResult> allResults() {
        List<IndirectRewriteResult> results = new ArrayList<>();
        for (Map<KeyId, IndirectRewriteResult> stageResults : resultsByStage.values()) {
            results.addAll(stageResults.values());
        }
        return List.copyOf(results);
    }

    @Override
    public void reset() {
        requestsByStage.clear();
        resultsByStage.clear();
    }
}

