package rogo.sketch.core.backend;

import rogo.sketch.core.packet.ExecutionDomain;

public interface QueueRouter {
    QueueRouter NO_OP = new QueueRouter() {
        @Override
        public GpuHandle resolveQueue(QueueAffinity affinity) {
            return GpuHandle.NONE;
        }

        @Override
        public boolean isDedicatedQueue(ExecutionDomain domain) {
            return false;
        }
    };

    GpuHandle resolveQueue(QueueAffinity affinity);

    boolean isDedicatedQueue(ExecutionDomain domain);
}
