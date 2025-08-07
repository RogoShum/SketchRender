package rogo.sketchrender.culling;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;
import rogo.sketchrender.SketchRender;
import rogo.sketchrender.render.sketch.resource.PersistentReadSSBO;
import rogo.sketchrender.render.sketch.resource.ShaderStorageBuffer;
import rogo.sketchrender.util.IndexPool;
import rogo.sketchrender.util.LifeTimer;

import java.util.Set;

import static net.minecraftforge.common.extensions.IForgeBlockEntity.INFINITE_EXTENT_AABB;

public class EntityCullingMask {
    private final EntityMap entityMap = new EntityMap();
    private ShaderStorageBuffer entityDataShaderStorageBuffer;
    private PersistentReadSSBO cullingResultSSBO;
    private PersistentReadSSBO prevCullingResultSSBO;

    public EntityCullingMask(int initialCapacity) {
        int adjustedCapacity = calculateNewCapacity(initialCapacity);
        initializeSSBOs(adjustedCapacity);
    }

    private void initializeSSBOs(int initialCapacity) {
        this.entityDataShaderStorageBuffer = new ShaderStorageBuffer(initialCapacity, 6 * Float.BYTES, GL43.GL_DYNAMIC_DRAW);
        this.cullingResultSSBO = new PersistentReadSSBO(initialCapacity, Byte.BYTES);
        this.prevCullingResultSSBO = new PersistentReadSSBO(initialCapacity, Byte.BYTES);
    }

    public void bindSSBO() {
        this.entityDataShaderStorageBuffer.bindShaderSlot(0);
        this.cullingResultSSBO.bindShaderSlot(1);
    }

    public boolean isObjectVisible(Object o) {
        AABB aabb = SketchRender.getObjectAABB(o);

        if (aabb == INFINITE_EXTENT_AABB) {
            return true;
        }

        int idx = getEntityMap().getIndex(o);

        if (getEntityMap().objectTimer.contains(o)) {
            getEntityMap().add(o, CullingStateManager.clientTickCount);
        }

        if (idx > -1 && idx < cullingResultSSBO.getDataCount()) {
           byte result = cullingResultSSBO.getMappedBuffer().get(idx);
            if (result > 0) {
                return true;
            } else if (idx < prevCullingResultSSBO.getDataCount()) {
                result = prevCullingResultSSBO.getMappedBuffer().get(idx);

                if (result > 0) {
                    return true;
                }
            }

            return false;
        } else {
            getEntityMap().add(o, CullingStateManager.clientTickCount);
        }

        return true;
    }

    public void cleanup() {
        if (entityDataShaderStorageBuffer != null) {
            entityDataShaderStorageBuffer.dispose();
        }
        if (cullingResultSSBO != null) {
            cullingResultSSBO.dispose();
        }
        if (prevCullingResultSSBO != null) {
            prevCullingResultSSBO.dispose();
        }
        getEntityMap().clear();
    }

    private void checkAndAdjustCapacity() {
        int currentEntityCount = getEntityMap().size();
        int currentCapacity = (int) entityDataShaderStorageBuffer.getDataCount();

        if (currentEntityCount > currentCapacity * 0.75) {
            int newCapacity = calculateNewCapacity(currentEntityCount);
            entityDataShaderStorageBuffer.ensureCapacity(newCapacity, false);
            cullingResultSSBO.ensureCapacity(newCapacity);
            prevCullingResultSSBO.ensureCapacity(newCapacity);
        } else if (currentEntityCount < currentCapacity * 0.25 && currentCapacity > 64) {
            int newCapacity = Math.max(64, currentEntityCount);
            entityDataShaderStorageBuffer.ensureCapacity(newCapacity, false, true);
            cullingResultSSBO.ensureCapacity(newCapacity, true);
            prevCullingResultSSBO.ensureCapacity(newCapacity, true);
        }
    }

    private int calculateNewCapacity(int requiredCapacity) {
        int capacity = ((requiredCapacity / 64) + 1) * 64;
        capacity = ((capacity + 3) / 4) * 4;
        return capacity;
    }

    public void updateEntityData() {
        checkAndAdjustCapacity();

        long bufferPointer = entityDataShaderStorageBuffer.getMemoryAddress();
        getEntityMap().indexPool.forEach((obj, index) -> {
            AABB aabb = SketchRender.getObjectAABB(obj);
            Vec3 center = aabb.getCenter();

            int offset = index * 6 * Float.BYTES;
            MemoryUtil.memPutFloat(bufferPointer + offset, (float) center.x);
            MemoryUtil.memPutFloat(bufferPointer + offset + 4, (float) center.y);
            MemoryUtil.memPutFloat(bufferPointer + offset + 8, (float) center.z);
            MemoryUtil.memPutFloat(bufferPointer + offset + 12, (float) aabb.getXsize());
            MemoryUtil.memPutFloat(bufferPointer + offset + 16, (float) aabb.getYsize());
            MemoryUtil.memPutFloat(bufferPointer + offset + 20, (float) aabb.getZsize());
            entityDataShaderStorageBuffer.position = Math.max(entityDataShaderStorageBuffer.position, offset + 24);
        });

        entityDataShaderStorageBuffer.upload();
        entityDataShaderStorageBuffer.position = 0;
    }

    public void swapBuffer(int tickCount) {
        PersistentReadSSBO buffer = this.prevCullingResultSSBO;
        this.prevCullingResultSSBO = this.cullingResultSSBO;
        this.cullingResultSSBO = buffer;
        MemoryUtil.memSet(buffer.getMemoryAddress(), 0, buffer.getCapacity());
        getEntityMap().tickTemp(tickCount);
    }

    public EntityMap getEntityMap() {
        return entityMap;
    }

    public static class EntityMap {
        private final IndexPool<Object> indexPool = new IndexPool<>();
        private final LifeTimer<Object> objectTimer = new LifeTimer<>();

        public EntityMap() {
        }

        public void addObject(Object obj) {
            if (obj instanceof Entity && ((Entity) obj).isAlive())
                indexPool.add(obj);
            else if (obj instanceof BlockEntity && !((BlockEntity) obj).isRemoved())
                indexPool.add(obj);
            else
                indexPool.add(obj);
        }

        public void add(Object obj, int tickCount) {
            objectTimer.updateUsageTick(obj, tickCount);
            addObject(obj);
        }

        public Integer getIndex(Object obj) {
            if (!indexPool.contains(obj))
                return -1;

            return indexPool.indexOf(obj);
        }

        public void tickTemp(int tickCount) {
            Set<Object> removed = objectTimer.tick(tickCount, 20);
            for (Object o : removed) {
                indexPool.remove(o);
            }
        }

        public void clear() {
            indexPool.clear();
            objectTimer.clear();
        }

        public int size() {
            return indexPool.getMaxIndex();
        }
    }
}