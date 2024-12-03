package rogo.sketchrender.api;

public interface RenderSectionVisibility {
    boolean shouldCheckVisibility(int clientTick);

    void updateVisibleTick(int clientTick);

    int getPositionX();

    int getPositionY();

    int getPositionZ();
}
