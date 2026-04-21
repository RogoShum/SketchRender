package rogo.sketch.core.memory;

import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;

public final class MemoryLease implements AutoCloseable {
    private final UnifiedMemoryFabric fabric;
    private final long leaseId;

    MemoryLease(UnifiedMemoryFabric fabric, long leaseId) {
        this.fabric = fabric;
        this.leaseId = leaseId;
    }

    public MemoryLease update(long reservedBytes, long liveBytes) {
        fabric.updateLease(leaseId, reservedBytes, liveBytes, 0.0D);
        return this;
    }

    public MemoryLease update(long reservedBytes, long liveBytes, double fragmentationRatio) {
        fabric.updateLease(leaseId, reservedBytes, liveBytes, fragmentationRatio);
        return this;
    }

    public MemoryLease bindSuppliers(LongSupplier reservedSupplier, LongSupplier liveSupplier) {
        fabric.bindLeaseSuppliers(leaseId, reservedSupplier, liveSupplier, null);
        return this;
    }

    public MemoryLease bindSuppliers(
            LongSupplier reservedSupplier,
            LongSupplier liveSupplier,
            DoubleSupplier fragmentationSupplier) {
        fabric.bindLeaseSuppliers(leaseId, reservedSupplier, liveSupplier, fragmentationSupplier);
        return this;
    }

    @Override
    public void close() {
        fabric.closeLease(leaseId);
    }
}
