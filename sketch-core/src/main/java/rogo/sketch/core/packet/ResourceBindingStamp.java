package rogo.sketch.core.packet;

import java.util.concurrent.atomic.AtomicLong;

public record ResourceBindingStamp(long stamp) {
    private static final AtomicLong SEQUENCE = new AtomicLong();
    public static final ResourceBindingStamp NONE = new ResourceBindingStamp(0L);

    public static ResourceBindingStamp next() {
        return new ResourceBindingStamp(SEQUENCE.incrementAndGet());
    }
}
