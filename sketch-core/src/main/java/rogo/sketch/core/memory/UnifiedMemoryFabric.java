package rogo.sketch.core.memory;

import rogo.sketch.core.pipeline.module.diagnostic.SketchDiagnostics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;

public final class UnifiedMemoryFabric {
    private static final UnifiedMemoryFabric INSTANCE = new UnifiedMemoryFabric();
    private static final long SNAPSHOT_INTERVAL_MS = 100L;
    private static final long TIMELINE_WINDOW_MS = 10_000L;
    private static final String DIAG_MODULE = "memory-fabric";

    private final AtomicLong nextLeaseId = new AtomicLong(1L);
    private final Map<Long, LeaseState> leases = new LinkedHashMap<>();
    private final EnumMap<MemoryDomain, MemoryBudget> budgets = new EnumMap<>(MemoryDomain.class);
    private final EnumMap<MemoryDomain, Long> domainPeakBytes = new EnumMap<>(MemoryDomain.class);
    private final Deque<MemoryTimelineRecord> timeline = new ArrayDeque<>();

    private long totalPeakBytes;
    private long pendingAllocatedBytes;
    private long pendingFreedBytes;
    private long lastSnapshotMillis;
    private MemoryDebugSnapshot cachedSnapshot = MemoryDebugSnapshot.empty();

    private UnifiedMemoryFabric() {
        for (MemoryDomain domain : MemoryDomain.values()) {
            budgets.put(domain, domain.defaultBudget());
            domainPeakBytes.put(domain, 0L);
        }
    }

    public static UnifiedMemoryFabric get() {
        return INSTANCE;
    }

    public synchronized MemoryLease openLease(MemoryDomain domain, String ownerId) {
        long leaseId = nextLeaseId.getAndIncrement();
        leases.put(leaseId, new LeaseState(domain, ownerId));
        return new MemoryLease(this, leaseId);
    }

    public synchronized void configureBudget(MemoryDomain domain, MemoryBudget budget) {
        if (domain == null) {
            return;
        }
        budgets.put(domain, budget == null ? MemoryBudget.unbounded() : budget);
        cachedSnapshot = MemoryDebugSnapshot.empty();
    }

    synchronized void updateLease(long leaseId, long reservedBytes, long liveBytes, double fragmentationRatio) {
        LeaseState leaseState = leases.get(leaseId);
        if (leaseState == null) {
            return;
        }
        applyStateUpdate(leaseState, reservedBytes, liveBytes, fragmentationRatio);
        cachedSnapshot = MemoryDebugSnapshot.empty();
    }

    synchronized void bindLeaseSuppliers(
            long leaseId,
            LongSupplier reservedSupplier,
            LongSupplier liveSupplier,
            DoubleSupplier fragmentationSupplier) {
        LeaseState leaseState = leases.get(leaseId);
        if (leaseState == null) {
            return;
        }
        leaseState.reservedSupplier = reservedSupplier;
        leaseState.liveSupplier = liveSupplier;
        leaseState.fragmentationSupplier = fragmentationSupplier;
        cachedSnapshot = MemoryDebugSnapshot.empty();
    }

    synchronized void closeLease(long leaseId) {
        LeaseState leaseState = leases.remove(leaseId);
        if (leaseState == null) {
            return;
        }
        if (leaseState.reservedBytes > 0L) {
            pendingFreedBytes += leaseState.reservedBytes;
        }
        cachedSnapshot = MemoryDebugSnapshot.empty();
    }

    public synchronized MemoryDebugSnapshot snapshot() {
        long now = System.currentTimeMillis();
        if (cachedSnapshot != MemoryDebugSnapshot.empty() && now - lastSnapshotMillis < SNAPSHOT_INTERVAL_MS) {
            return cachedSnapshot;
        }

        for (LeaseState leaseState : leases.values()) {
            refreshLease(leaseState);
        }

        EnumMap<MemoryDomain, Aggregate> aggregates = new EnumMap<>(MemoryDomain.class);
        for (MemoryDomain domain : MemoryDomain.values()) {
            aggregates.put(domain, new Aggregate());
        }

        long totalLiveBytes = 0L;
        long totalReservedBytes = 0L;
        for (LeaseState leaseState : leases.values()) {
            Aggregate aggregate = aggregates.get(leaseState.domain);
            aggregate.liveBytes += leaseState.liveBytes;
            aggregate.reservedBytes += leaseState.reservedBytes;
            if (leaseState.reservedBytes > 0L && leaseState.fragmentationRatio > 0.0D) {
                aggregate.fragmentationWeighted += leaseState.fragmentationRatio * leaseState.reservedBytes;
                aggregate.fragmentationWeight += leaseState.reservedBytes;
            }
            totalLiveBytes += leaseState.liveBytes;
            totalReservedBytes += leaseState.reservedBytes;
        }

        totalPeakBytes = Math.max(totalPeakBytes, totalLiveBytes);
        long totalBudgetBytes = 0L;
        List<MemoryDomainSnapshot> domainSnapshots = new ArrayList<>();
        for (MemoryDomain domain : MemoryDomain.values()) {
            Aggregate aggregate = aggregates.get(domain);
            long previousPeak = domainPeakBytes.getOrDefault(domain, 0L);
            long peak = Math.max(previousPeak, aggregate.liveBytes);
            domainPeakBytes.put(domain, peak);
            MemoryBudget budget = budgets.getOrDefault(domain, MemoryBudget.unbounded());
            if (budget.bounded()) {
                totalBudgetBytes += budget.limitBytes();
            }
            double fragmentation = aggregate.fragmentationWeight <= 0L
                    ? 0.0D
                    : aggregate.fragmentationWeighted / aggregate.fragmentationWeight;
            domainSnapshots.add(new MemoryDomainSnapshot(
                    domain,
                    aggregate.liveBytes,
                    aggregate.reservedBytes,
                    peak,
                    budget,
                    budget.usageRatio(aggregate.reservedBytes),
                    fragmentation));
        }
        domainSnapshots.sort(Comparator.comparingLong(MemoryDomainSnapshot::reservedBytes).reversed()
                .thenComparing(snapshot -> snapshot.domain().ordinal()));

        double allocRate = 0.0D;
        double freeRate = 0.0D;
        if (lastSnapshotMillis > 0L) {
            long elapsedMillis = Math.max(1L, now - lastSnapshotMillis);
            double seconds = elapsedMillis / 1000.0D;
            allocRate = pendingAllocatedBytes / seconds;
            freeRate = pendingFreedBytes / seconds;
        }

        timeline.addLast(new MemoryTimelineRecord(now, totalLiveBytes));
        long earliest = now - TIMELINE_WINDOW_MS;
        while (!timeline.isEmpty() && timeline.peekFirst().epochMillis() < earliest) {
            timeline.removeFirst();
        }

        MemoryBudget totalBudget = totalBudgetBytes > 0L ? MemoryBudget.ofBytes(totalBudgetBytes) : MemoryBudget.unbounded();
        double totalBudgetUsage = totalBudget.usageRatio(totalReservedBytes);
        cachedSnapshot = new MemoryDebugSnapshot(
                totalLiveBytes,
                totalReservedBytes,
                totalPeakBytes,
                allocRate,
                freeRate,
                totalBudget,
                totalBudgetUsage,
                domainSnapshots,
                List.copyOf(timeline));
        pendingAllocatedBytes = 0L;
        pendingFreedBytes = 0L;
        lastSnapshotMillis = now;
        return cachedSnapshot;
    }

    private void refreshLease(LeaseState leaseState) {
        if (leaseState.reservedSupplier == null || leaseState.liveSupplier == null) {
            return;
        }
        try {
            long reservedBytes = Math.max(0L, leaseState.reservedSupplier.getAsLong());
            long liveBytes = Math.max(0L, leaseState.liveSupplier.getAsLong());
            double fragmentation = leaseState.fragmentationSupplier != null
                    ? clampRatio(leaseState.fragmentationSupplier.getAsDouble())
                    : 0.0D;
            applyStateUpdate(leaseState, reservedBytes, liveBytes, fragmentation);
        } catch (Exception e) {
            SketchDiagnostics.get().warn(DIAG_MODULE, "Failed to sample memory lease for " + leaseState.ownerId, e);
        }
    }

    private void applyStateUpdate(LeaseState leaseState, long reservedBytes, long liveBytes, double fragmentationRatio) {
        long sanitizedReserved = Math.max(0L, reservedBytes);
        long sanitizedLive = Math.max(0L, Math.min(liveBytes, sanitizedReserved));
        if (sanitizedReserved > leaseState.reservedBytes) {
            pendingAllocatedBytes += sanitizedReserved - leaseState.reservedBytes;
        } else if (sanitizedReserved < leaseState.reservedBytes) {
            pendingFreedBytes += leaseState.reservedBytes - sanitizedReserved;
        }
        leaseState.reservedBytes = sanitizedReserved;
        leaseState.liveBytes = sanitizedLive;
        leaseState.fragmentationRatio = clampRatio(fragmentationRatio);
    }

    private double clampRatio(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static final class LeaseState {
        private final MemoryDomain domain;
        private final String ownerId;
        private long reservedBytes;
        private long liveBytes;
        private double fragmentationRatio;
        private LongSupplier reservedSupplier;
        private LongSupplier liveSupplier;
        private DoubleSupplier fragmentationSupplier;

        private LeaseState(MemoryDomain domain, String ownerId) {
            this.domain = domain;
            this.ownerId = ownerId != null ? ownerId : domain.name().toLowerCase();
        }
    }

    private static final class Aggregate {
        private long liveBytes;
        private long reservedBytes;
        private double fragmentationWeighted;
        private long fragmentationWeight;
    }
}
