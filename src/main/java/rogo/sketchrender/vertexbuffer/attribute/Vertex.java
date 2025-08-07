package rogo.sketchrender.vertexbuffer.attribute;

public abstract class Vertex {
    private final int count;
    private final String name;
    private final int index;

    public Vertex(int index, String name, int count) {
        this.count = count;
        this.name = name;
        this.index = index;
    }

    public String name() {
        return name;
    }

    public int index() {
        return index;
    }

    public int count() {
        return count;
    }

    public abstract int size();

    public abstract int glType();
}