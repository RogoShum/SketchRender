package rogo.sketchrender.render.sketch.component;

import rogo.sketchrender.render.sketch.resource.Texture;

import java.util.ArrayList;
import java.util.List;

public class RenderTarget {
    private final int handle;
    private final String identifier;
    private final List<Texture> colorAttachments = new ArrayList<>();
    private Texture depthAttachment;
    private Texture stencilAttachment;
    private int width, height;
    private int clearColor; // 例如ARGB

    public RenderTarget(int handle, String identifier, int width, int height, int clearColor) {
        this.handle = handle;
        this.identifier = identifier;
        this.width = width;
        this.height = height;
        this.clearColor = clearColor;
    }

    public void addColorAttachment(Texture texture) {
        if (texture != null) {
            colorAttachments.add(texture);
        }
    }

    public void setColorAttachment(int index, Texture texture) {
        while (colorAttachments.size() <= index) {
            colorAttachments.add(null);
        }
        colorAttachments.set(index, texture);
    }

    public Texture getColorAttachment(int index) {
        if (index < 0 || index >= colorAttachments.size()) return null;
        return colorAttachments.get(index);
    }

    public List<Texture> getColorAttachments() {
        return colorAttachments;
    }

    public Texture getDepthAttachment() {
        return depthAttachment;
    }

    public void setDepthAttachment(Texture texture) {
        this.depthAttachment = texture;
    }

    public Texture getStencilAttachment() {
        return stencilAttachment;
    }

    public void setStencilAttachment(Texture texture) {
        this.stencilAttachment = texture;
    }

    public int getHandle() {
        return handle;
    }

    public String getIdentifier() {
        return identifier;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getClearColor() {
        return clearColor;
    }

    public void setClearColor(int clearColor) {
        this.clearColor = clearColor;
    }
}