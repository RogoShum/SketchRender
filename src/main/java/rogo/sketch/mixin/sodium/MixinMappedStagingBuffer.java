package rogo.sketch.mixin.sodium;

import me.jellysquid.mods.sodium.client.gl.arena.staging.MappedStagingBuffer;
import me.jellysquid.mods.sodium.client.gl.buffer.GlBufferMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import rogo.sketch.compat.sodium.api.ResourceChecker;

import java.lang.reflect.Field;

@Mixin(value = MappedStagingBuffer.class, remap = false)
public abstract class MixinMappedStagingBuffer implements ResourceChecker {
    @Unique
    private ResourceChecker sketchlib$resourceChecker;

    @Override
    public boolean disposed() {
        if (sketchlib$resourceChecker == null) {
            try {
                Field field = MappedStagingBuffer.class.getDeclaredField("mappedBuffer");
                field.setAccessible(true);
                sketchlib$resourceChecker = (ResourceChecker) field.get(this);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        }

        return sketchlib$resourceChecker.disposed();
    }

    @Mixin(targets = "me.jellysquid.mods.sodium.client.gl.arena.staging.MappedStagingBuffer$MappedBuffer", remap = false)
    public static abstract class MixinMappedBuffer implements ResourceChecker {
        @Shadow(remap = false)
        public abstract GlBufferMapping map();

        @Override
        public boolean disposed() {
            return this.map().isDisposed();
        }
    }
}