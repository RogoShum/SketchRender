package rogo.sketchrender.render.driver;

public class GraphicsDriver {
    private static GraphicsAPI currentAPI = GraphicsAPI.OPENGL; // 默认

    public static GraphicsAPI getCurrentAPI() {
        return currentAPI;
    }

    public static void setCurrentAPI(GraphicsAPI api) {
        currentAPI = api;
    }
}