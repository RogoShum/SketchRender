package rogo.sketchrender.util;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import rogo.sketchrender.api.RenderSectionVisibility;

public class DummySection implements RenderSectionVisibility {
    private final int x;
    private final int y;
    private final int z;

    public DummySection(AABB aabb) {
        Vec3 center = aabb.getCenter();
        x = (int) center.x;
        y = (int) center.y;
        z = (int) center.z;
    }

    @Override
    public boolean shouldCheckVisibility(int clientTick) {
        return true;
    }

    @Override
    public void updateVisibleTick(int clientTick) {
    }

    @Override
    public int getPositionX() {
        return x;
    }

    @Override
    public int getPositionY() {
        return y;
    }

    @Override
    public int getPositionZ() {
        return z;
    }
}
