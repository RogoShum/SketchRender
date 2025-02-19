package rogo.sketchrender.culling;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GL44;
import org.lwjgl.system.MemoryUtil;
import rogo.sketchrender.SketchRender;
import rogo.sketchrender.api.Config;
import rogo.sketchrender.shader.uniform.SSBO;
import rogo.sketchrender.util.IndexedSet;
import rogo.sketchrender.util.LifeTimer;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.function.Consumer;

import static net.minecraftforge.common.extensions.IForgeBlockEntity.INFINITE_EXTENT_AABB;

public class EntityCullingSSBOMask {
    private final EntityMap entityMap = new EntityMap();

    private SSBO entityDataSSBO; // 存储实体数据的SSBO
    private SSBO cullingResultSSBO; // 存储剔除结果的SSBO
    private ByteBuffer persistentBuffer; // Persistent Mapping的映射缓冲区

    public EntityCullingSSBOMask(int initialCapacity) {
        // 确保初始容量是64的倍数，并且至少为64
        int adjustedCapacity = Math.max(64, ((initialCapacity / 64) + 1) * 64);
        initializeSSBOs(adjustedCapacity);
    }

    private void initializeSSBOs(int initialCapacity) {
        entityDataSSBO = new SSBO(initialCapacity, 8 * Float.BYTES, GL43.GL_DYNAMIC_DRAW);
        cullingResultSSBO = new SSBO(initialCapacity, Integer.BYTES, GL44.GL_MAP_PERSISTENT_BIT | GL43.GL_MAP_READ_BIT);
        persistentBuffer = MemoryUtil.memByteBuffer(cullingResultSSBO.getMemoryAddress(), initialCapacity * Integer.BYTES);
    }

    public void bindSSBO() {
        entityDataSSBO.bindShaderSlot(0);
        cullingResultSSBO.bindShaderSlot(1);
    }

    protected boolean shouldUpdate() {
        return true;
    }

    int configDelayCount() {
        return Config.getDepthUpdateDelay();
    }

    public boolean isObjectVisible(Object o) {
        AABB aabb = SketchRender.getObjectAABB(o);

        if (aabb == INFINITE_EXTENT_AABB) {
            return true;
        }

        int idx = getEntityTable().getIndex(o);

        if (getEntityTable().tempObjectTimer.contains(o))
            getEntityTable().addTemp(o, CullingStateManager.clientTickCount);

        if (idx > -1 && idx < persistentBuffer.limit() / Integer.BYTES) {
            return persistentBuffer.getInt(idx * Integer.BYTES) > 0;
        } else {
            getEntityTable().addTemp(o, CullingStateManager.clientTickCount);
        }

        return true;
    }


    public void readData() {
        getEntityTable().readUpload();
    }

    public void cleanup() {
        if (entityDataSSBO != null) {
            entityDataSSBO.discard();
        }
        if (cullingResultSSBO != null) {
            cullingResultSSBO.discard();
        }
    }

    /**
     * 检查并调整SSBO的容量，确保其能够容纳当前实体数量
     */
    private void checkAndAdjustCapacity() {
        int currentEntityCount = getEntityTable().size();
        int currentCapacity = (int) entityDataSSBO.getDataNum();

        // 如果实体数量超过当前容量的75%，则扩容
        if (currentEntityCount > currentCapacity * 0.75) {
            int newCapacity = calculateNewCapacity(currentEntityCount);
            entityDataSSBO.ensureCapacity(newCapacity);
            cullingResultSSBO.ensureCapacity(newCapacity);
            persistentBuffer = MemoryUtil.memByteBuffer(cullingResultSSBO.getMemoryAddress(), newCapacity * Integer.BYTES);
        }
        // 如果实体数量小于当前容量的25%，则缩容
        else if (currentEntityCount < currentCapacity * 0.25 && currentCapacity > 64) {
            int newCapacity = Math.max(64, currentEntityCount);
            entityDataSSBO.ensureCapacity(newCapacity, true);
            cullingResultSSBO.ensureCapacity(newCapacity, true);
            persistentBuffer = MemoryUtil.memByteBuffer(cullingResultSSBO.getMemoryAddress(), newCapacity * Integer.BYTES);
        }
    }

    /**
     * 计算新的容量，确保是64的倍数
     */
    private int calculateNewCapacity(int requiredCapacity) {
        return ((requiredCapacity / 64) + 1) * 64;
    }

    /**
     * 更新实体数据，手动将数据存入entityDataSSBO并上传
     */
    public void updateEntityData() {
        checkAndAdjustCapacity();

        long bufferPointer = entityDataSSBO.getMemoryAddress();
        getEntityTable().clearUpload();
        getEntityTable().indexMap.forEach((obj, index) -> {
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

            getEntityTable().uploadTemp.add(obj);
            getEntityTable().uploadEntity.put(obj, index);
        });


        entityDataSSBO.upload();
        entityDataSSBO.position = 0;
    }

    public EntityMap getEntityTable() {
        return entityMap;
    }

    public static class EntityMap {
        private final IndexedSet<Object> indexMap = new IndexedSet<>();
        private final LifeTimer<Object> tempObjectTimer = new LifeTimer<>();
        private HashSet<Object> uploadTemp = new HashSet<>();
        private HashSet<Object> readTemp = new HashSet<>();
        private HashMap<Object, Integer> uploadEntity = new HashMap<>();
        private HashMap<Object, Integer> readEntity = new HashMap<>();

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

        public void addTemp(Object obj, int tickCount) {
            tempObjectTimer.updateUsageTick(obj, tickCount);
        }

        public void copyTemp(EntityMap entityMap, int tickCount) {
            entityMap.tempObjectTimer.foreach(o -> addTemp(o, tickCount));
            this.uploadTemp.addAll(entityMap.uploadTemp);
            this.uploadEntity.putAll(entityMap.uploadEntity);
            this.readTemp = uploadTemp;
            this.readEntity = uploadEntity;
        }

        public Integer getIndex(Object obj) {
            if (!readTemp.contains(obj))
                return -1;
            return readEntity.getOrDefault(obj, -1);
        }

        public void readUpload() {
            readTemp = uploadTemp;
            uploadTemp = new HashSet<>();
            readEntity = uploadEntity;
            uploadEntity = new HashMap<>();
        }

        public void clearUpload() {
            uploadTemp.clear();
            uploadEntity.clear();
        }

        public void clearIndexMap() {
            indexMap.clear();
        }

        public void tickTemp(int tickCount) {
            tempObjectTimer.tick(tickCount, 3);
        }

        public void addAllTemp() {
            tempObjectTimer.foreach(this::addObject);
        }

        public void clear() {
            indexMap.clear();
            tempObjectTimer.clear();
        }

        private void addAttribute(Consumer<Consumer<ByteBuffer>> consumer, AABB aabb, int index) {
            consumer.accept(buffer -> {
                buffer.putFloat(index);

                float size = (float) Math.max(aabb.getXsize(), aabb.getZsize());
                buffer.putFloat(size + 0.5F);
                buffer.putFloat((float) aabb.getYsize() + 0.5F);

                Vec3 pos = aabb.getCenter();
                buffer.putFloat((float) pos.x);
                buffer.putFloat((float) pos.y);
                buffer.putFloat((float) pos.z);
            });
        }

        public void addEntityAttribute(Consumer<Consumer<ByteBuffer>> consumer) {
            clearUpload();
            indexMap.forEach((o, index) -> {
                addAttribute(consumer, SketchRender.getObjectAABB(o), index);
                uploadTemp.add(o);
                uploadEntity.put(o, index);
            });
        }

        public int size() {
            return indexMap.size();
        }
    }
}
