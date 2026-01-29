package rogo.sketch.core.pipeline;

public enum FPS {
    FPS_30(30),
    FPS_60(60),
    FPS_90(90),
    FPS_120(120),
    FPS_144(144),
    FPS_165(165),
    FPS_240(240),
    FPS_300(300),
    UNLIMITED(-1);

    private final int limit;

    FPS(int limit) {
        this.limit = limit;
    }

    public int limit() {
        return limit;
    }

    public boolean unlimited() {
        return limit < 0;
    }
}