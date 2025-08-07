package rogo.sketchrender.render.sketch.driver;

public class GraphicsDriver {
    private static GraphicsAPI currentAPI = GraphicsAPI.OPENGL;

    public static GraphicsAPI getCurrentAPI() {
        return currentAPI;
    }

    public static void setCurrentAPI(GraphicsAPI api) {
        currentAPI = api;
    }
}