package rogo.sketch.render.driver;

public class GraphicsDriver {
    private static GraphicsAPI currentAPI = new OpenGlAPI();

    public static GraphicsAPI getCurrentAPI() {
        return currentAPI;
    }

    public static void setCurrentAPI(GraphicsAPI api) {
        currentAPI = api;
    }
}