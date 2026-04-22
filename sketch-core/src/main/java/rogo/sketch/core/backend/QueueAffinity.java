package rogo.sketch.core.backend;

import rogo.sketch.core.packet.ExecutionDomain;

public record QueueAffinity(ExecutionDomain domain, boolean preferDedicated) {
    public QueueAffinity {
        domain = domain != null ? domain : ExecutionDomain.RASTER;
    }
}
