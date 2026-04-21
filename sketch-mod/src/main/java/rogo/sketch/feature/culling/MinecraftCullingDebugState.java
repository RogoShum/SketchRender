package rogo.sketch.feature.culling;

/**
 * Minecraft-specific culling debug state and counters.
 */
public final class MinecraftCullingDebugState {
    private static final MinecraftCullingDebugState INSTANCE = new MinecraftCullingDebugState();

    private volatile int debugMode;
    private volatile int fps;
    private volatile int entityHiddenCount;
    private volatile int entityTotalCount;
    private volatile int blockEntityHiddenCount;
    private volatile int blockEntityTotalCount;
    private volatile boolean checkingCull;
    private volatile boolean checkingTexture;

    private MinecraftCullingDebugState() {
    }

    public static MinecraftCullingDebugState getInstance() {
        return INSTANCE;
    }

    public void resetFrameCounters() {
        entityHiddenCount = 0;
        entityTotalCount = 0;
        blockEntityHiddenCount = 0;
        blockEntityTotalCount = 0;
    }

    public void incrementEntityTotal() {
        entityTotalCount++;
    }

    public void incrementEntityHidden() {
        entityHiddenCount++;
    }

    public void incrementBlockEntityTotal() {
        blockEntityTotalCount++;
    }

    public void incrementBlockEntityHidden() {
        blockEntityHiddenCount++;
    }

    public void updateFps(int fps) {
        this.fps = fps;
    }

    public void setCheckingFlags(boolean checkingCull, boolean checkingTexture) {
        this.checkingCull = checkingCull;
        this.checkingTexture = checkingTexture;
    }

    public void clearCheckingFlags() {
        setCheckingFlags(false, false);
    }

    public int nextDebugMode() {
        debugMode++;
        if (debugMode >= 3) {
            debugMode = 0;
        }
        return debugMode;
    }

    public int debugMode() {
        return debugMode;
    }

    public int fps() {
        return fps;
    }

    public int entityHiddenCount() {
        return entityHiddenCount;
    }

    public int entityTotalCount() {
        return entityTotalCount;
    }

    public int blockEntityHiddenCount() {
        return blockEntityHiddenCount;
    }

    public int blockEntityTotalCount() {
        return blockEntityTotalCount;
    }

    public boolean checkingCull() {
        return checkingCull;
    }

    public boolean checkingTexture() {
        return checkingTexture;
    }
}
