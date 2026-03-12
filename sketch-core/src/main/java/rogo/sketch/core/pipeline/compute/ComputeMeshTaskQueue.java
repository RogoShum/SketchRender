package rogo.sketch.core.pipeline.compute;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Priority queue for future async chunk mesh compute compile tasks.
 */
public final class ComputeMeshTaskQueue {
    private final PriorityBlockingQueue<ChunkComputeCompileRequest> queue = new PriorityBlockingQueue<>();

    public void submit(ChunkComputeCompileRequest request) {
        if (request != null) {
            queue.offer(request);
        }
    }

    public List<ChunkComputeCompileRequest> pollBudget(ComputeMeshTaskBudget budget) {
        int max = budget != null ? budget.maxTasksPerFrame() : ComputeMeshTaskBudget.DEFAULT.maxTasksPerFrame();
        List<ChunkComputeCompileRequest> out = new ArrayList<>(Math.max(1, max));
        for (int i = 0; i < max; i++) {
            ChunkComputeCompileRequest req = queue.poll();
            if (req == null) {
                break;
            }
            out.add(req);
        }
        return out;
    }

    public int size() {
        return queue.size();
    }

    public void clear() {
        queue.clear();
    }
}