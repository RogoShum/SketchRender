package rogo.sketchrender.culling;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;
import rogo.sketchrender.SketchRender;
import rogo.sketchrender.shader.uniform.PersistentReadSSBO;
import rogo.sketchrender.shader.uniform.SSBO;
import rogo.sketchrender.util.IndexedSet;
import rogo.sketchrender.util.LifeTimer;

import java.util.Set;

import static net.minecraftforge.common.extensions.IForgeBlockEntity.INFINITE_EXTENT_AABB;

public class EntityCullingMask {
    private final EntityMap entityMap = new EntityMap();

    private SSBO entityDataSSBO;
    private PersistentReadSSBO cullingResultSSBO;

    public EntityCullingMask(int initialCapacity) {
        int adjustedCapacity = Math.max(64, ((initialCapacity / 64) + 1) * 64);
        initializeSSBOs(adjustedCapacity);
    }

    private void initializeSSBOs(int initialCapacity) {
        entityDataSSBO = new SSBO(initialCapacity, 8 * Float.BYTES, GL43.GL_DYNAMIC_DRAW);
        cullingResultSSBO = new PersistentReadSSBO(initialCapacity, Integer.BYTES);
    }

    public void bindSSBO() {
        entityDataSSBO.bindShaderSlot(0);
        cullingResultSSBO.bindShaderSlot(1);
    }

    public boolean isObjectVisible(Object o) {
        AABB aabb = SketchRender.getObjectAABB(o);

        if (aabb == INFINITE_EXTENT_AABB) {
            return true;
        }

        int idx = getEntityTable().getIndex(o);

        if (getEntityTable().objectTimer.contains(o)) {
            getEntityTable().add(o, CullingStateManager.clientTickCount);
        }

        if (idx > -1 && idx < cullingResultSSBO.getDataNum()) {
            return cullingResultSSBO.getInt(idx) < 1;
        } else {
            getEntityTable().add(o, CullingStateManager.clientTickCount);
        }

        return true;
    }

    public void cleanup() {
        if (entityDataSSBO != null) {
            entityDataSSBO.discard();
        }
        if (cullingResultSSBO != null) {
            cullingResultSSBO.discard();
        }
    }

    private void checkAndAdjustCapacity() {
        int currentEntityCount = getEntityTable().size();
        int currentCapacity = (int) entityDataSSBO.getDataNum();

        if (currentEntityCount > currentCapacity * 0.75) {
            int newCapacity = calculateNewCapacity(currentEntityCount);
            entityDataSSBO.ensureCapacity(newCapacity, false);
            cullingResultSSBO.ensureCapacity(newCapacity);
        } else if (currentEntityCount < currentCapacity * 0.25 && currentCapacity > 64) {
            int newCapacity = Math.max(64, currentEntityCount);
            entityDataSSBO.ensureCapacity(newCapacity, false, true);
            cullingResultSSBO.ensureCapacity(newCapacity, true);
        }
    }

    private int calculateNewCapacity(int requiredCapacity) {
        return ((requiredCapacity / 64) + 1) * 64;
    }

    public void updateEntityData() {
        checkAndAdjustCapacity();

        long bufferPointer = entityDataSSBO.getMemoryAddress();
        for (int index = 0; index < getEntityTable().indexMap.size(); ++index) {
            Object obj = getEntityTable().indexMap.get(index);
            AABB aabb = SketchRender.getObjectAABB(obj);
            Vec3 center = aabb.getCenter();

            int offset = index * 8 * Float.BYTES;
            MemoryUtil.memPutFloat(bufferPointer + offset, (float) center.x);
            MemoryUtil.memPutFloat(bufferPointer + offset + 4, (float) center.y);
            MemoryUtil.memPutFloat(bufferPointer + offset + 8, (float) center.z);
            MemoryUtil.memPutFloat(bufferPointer + offset + 12, (float) aabb.getXsize());
            MemoryUtil.memPutFloat(bufferPointer + offset + 16, (float) aabb.getYsize());
            MemoryUtil.memPutFloat(bufferPointer + offset + 20, (float) aabb.getZsize());
            MemoryUtil.memPutFloat(bufferPointer + offset + 24, index);
            MemoryUtil.memPutFloat(bufferPointer + offset + 28, 0);
            entityDataSSBO.position = offset + 32;
        }

        entityDataSSBO.upload();
        entityDataSSBO.position = 0;
    }

    public EntityMap getEntityTable() {
        return entityMap;
    }

    public static class EntityMap {
        private IndexedSet<Object> indexMap = new IndexedSet<>();
        private final LifeTimer<Object> objectTimer = new LifeTimer<>();

        public EntityMap() {
        }

        public void addObject(Object obj) {
            if (obj instanceof Entity && ((Entity) obj).isAlive())
                indexMap.add(obj);
            else if (obj instanceof BlockEntity && !((BlockEntity) obj).isRemoved())
                indexMap.add(obj);
            else
                indexMap.add(obj);
        }

        public void add(Object obj, int tickCount) {
            objectTimer.updateUsageTick(obj, tickCount);
            addObject(obj);
        }

        public Integer getIndex(Object obj) {
            if (!indexMap.contains(obj))
                return -1;

            return indexMap.indexOf(obj);
        }

        public void tickTemp(int tickCount) {
            Set<Object> removed = objectTimer.tick(tickCount, 20);
            //stupid but works
            indexMap = objectTimer.toIndexedSet();
        }

        public void clear() {
            indexMap.clear();
            objectTimer.clear();
        }

        public int size() {
            return indexMap.size();
        }
    }
}