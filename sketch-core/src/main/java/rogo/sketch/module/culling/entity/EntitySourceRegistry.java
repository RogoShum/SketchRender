package rogo.sketch.module.culling.entity;

import org.jetbrains.annotations.Nullable;
import org.joml.primitives.AABBf;
import rogo.sketch.core.graphics.ecs.GraphicsEntityId;
import rogo.sketch.core.scene.SceneDatabase;
import rogo.sketch.core.util.IndexPool;
import rogo.sketch.module.culling.VisibilitySystem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Host-agnostic registry for entity/block-entity culling subjects.
 */
public final class EntitySourceRegistry {
    private final IndexPool<Object> subjectIndex = new IndexPool<>();
    private final Map<Object, SubjectRecord> subjectRecords = new HashMap<>();
    private final SceneDatabase sceneDatabase;

    public EntitySourceRegistry(SceneDatabase sceneDatabase) {
        this.sceneDatabase = sceneDatabase;
    }

    public synchronized void upsert(
            Object subjectKey,
            EntityFeatureSchema.SubjectKind subjectKind,
            AABBf bounds,
            int flags,
            @Nullable GraphicsEntityId transformEntityId,
            int logicTick) {
        if (subjectKey == null || bounds == null) {
            return;
        }
        if (!subjectIndex.contains(subjectKey)) {
            subjectIndex.add(subjectKey);
        }
        SubjectRecord record = subjectRecords.computeIfAbsent(subjectKey, ignored -> new SubjectRecord());
        if (sameData(record.data, subjectKind, bounds, flags, transformEntityId)) {
            return;
        }
        record.data = EntityFeatureSchema.fromBounds(subjectKey, subjectKind, bounds, flags, transformEntityId);
    }

    public synchronized void touch(
            Object subjectKey,
            EntityFeatureSchema.SubjectKind subjectKind,
            AABBf bounds,
            int flags,
            @Nullable GraphicsEntityId transformEntityId,
            int logicTick) {
        upsert(subjectKey, subjectKind, bounds, flags, transformEntityId, logicTick);
    }

    public synchronized int subjectCount() {
        return subjectIndex.getMaxIndex();
    }

    public synchronized int liveSubjectCount() {
        return subjectIndex.size();
    }

    public synchronized int indexOf(Object subjectKey) {
        if (subjectKey == null || !subjectIndex.contains(subjectKey)) {
            return -1;
        }
        return subjectIndex.indexOf(subjectKey);
    }

    public synchronized boolean hasVisibilitySample(Object subjectKey) {
        SubjectRecord record = subjectRecords.get(subjectKey);
        return record != null && record.visibilitySampled;
    }

    public synchronized List<IndexedSubject> snapshot() {
        List<IndexedSubject> subjects = new ArrayList<>(subjectIndex.size());
        subjectIndex.forEach((subject, slot) -> {
            SubjectRecord record = subjectRecords.get(subject);
            if (record != null && record.data != null) {
                subjects.add(new IndexedSubject(slot, record.data, record.visibilitySampled));
            }
        });
        subjects.sort(Comparator.comparingInt(IndexedSubject::slot));
        return subjects;
    }

    public synchronized void forEachIndexedOrdered(IndexedSubjectConsumer consumer) {
        if (consumer == null) {
            return;
        }
        for (int slot = 0; slot < subjectIndex.getMaxIndex(); ++slot) {
            Object subject = subjectIndex.get(slot);
            if (subject == null) {
                continue;
            }
            SubjectRecord record = subjectRecords.get(subject);
            if (record != null && record.data != null) {
                consumer.accept(slot, record.data, record.visibilitySampled);
            }
        }
    }

    public synchronized List<IndexedSubject> snapshotVisible(VisibilitySystem visibilitySystem, @Nullable rogo.sketch.core.pipeline.RenderContext renderContext) {
        List<IndexedSubject> subjects = snapshot();
        if (visibilitySystem == null) {
            return subjects;
        }
        return visibilitySystem.collectVisibleSubjects(subjects, renderContext);
    }

    public synchronized void markVisibilitySampled() {
        for (SubjectRecord record : subjectRecords.values()) {
            record.visibilitySampled = true;
        }
    }

    public synchronized void markUploadSubmitted() {
        for (SubjectRecord record : subjectRecords.values()) {
            record.pendingSample = true;
        }
    }

    public synchronized void promotePendingSamples() {
        for (SubjectRecord record : subjectRecords.values()) {
            if (record.pendingSample) {
                record.visibilitySampled = true;
                record.pendingSample = false;
            }
        }
    }

    public synchronized void remove(Object subject) {
        if (subject == null) {
            return;
        }
        subjectIndex.remove(subject);
        subjectRecords.remove(subject);
    }

    public synchronized void clear() {
        subjectIndex.clear();
        subjectRecords.clear();
    }

    public record IndexedSubject(
            int slot,
            EntityFeatureSchema.SubjectData data,
            boolean visibilitySampled) {
    }

    private static final class SubjectRecord {
        private EntityFeatureSchema.SubjectData data;
        private boolean visibilitySampled;
        private boolean pendingSample;
    }

    @FunctionalInterface
    public interface IndexedSubjectConsumer {
        void accept(int slot, EntityFeatureSchema.SubjectData data, boolean visibilitySampled);
    }

    private static boolean sameData(
            @Nullable EntityFeatureSchema.SubjectData existing,
            EntityFeatureSchema.SubjectKind subjectKind,
            AABBf bounds,
            int flags,
            @Nullable GraphicsEntityId transformEntityId) {
        if (existing == null || bounds == null) {
            return false;
        }
        float centerX = (bounds.minX + bounds.maxX) * 0.5f;
        float centerY = (bounds.minY + bounds.maxY) * 0.5f;
        float centerZ = (bounds.minZ + bounds.maxZ) * 0.5f;
        float extentX = bounds.maxX - bounds.minX;
        float extentY = bounds.maxY - bounds.minY;
        float extentZ = bounds.maxZ - bounds.minZ;
        return existing.subjectKind() == subjectKind
                && existing.centerX() == centerX
                && existing.centerY() == centerY
                && existing.centerZ() == centerZ
                && existing.extentX() == extentX
                && existing.extentY() == extentY
                && existing.extentZ() == extentZ
                && existing.flags() == flags
                && Objects.equals(existing.transformEntityId(), transformEntityId);
    }
}
