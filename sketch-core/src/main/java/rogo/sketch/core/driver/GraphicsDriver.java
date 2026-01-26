package rogo.sketch.core.driver;

public class GraphicsDriver {
    private static GraphicsAPI currentAPI = new OpenGLAPI();

    public static GraphicsAPI getCurrentAPI() {
        return currentAPI;
    }

    public static void setCurrentAPI(GraphicsAPI api) {
        currentAPI = api;
    }
}