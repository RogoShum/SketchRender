package rogo.sketch.vanilla.backend;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import rogo.sketch.core.backend.WindowService;

public final class MinecraftWindowService implements WindowService {
    private volatile boolean vSyncEnabled = true;

    @Override
    public long windowHandle() {
        return minecraftWindow().getWindow();
    }

    @Override
    public int framebufferWidth() {
        return minecraftWindow().getWidth();
    }

    @Override
    public int framebufferHeight() {
        return minecraftWindow().getHeight();
    }

    @Override
    public void resizeFramebuffer(int width, int height) {
        GLFW.glfwSetWindowSize(windowHandle(), Math.max(1, width), Math.max(1, height));
    }

    @Override
    public void setVSync(boolean enabled) {
        vSyncEnabled = enabled;
        if (RenderSystem.isOnRenderThreadOrInit()) {
            GLFW.glfwSwapInterval(enabled ? 1 : 0);
        } else {
            RenderSystem.recordRenderCall(() -> GLFW.glfwSwapInterval(enabled ? 1 : 0));
        }
    }

    @Override
    public boolean vSyncEnabled() {
        return vSyncEnabled;
    }

    private static com.mojang.blaze3d.platform.Window minecraftWindow() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) {
            throw new IllegalStateException("Minecraft window is unavailable");
        }
        return minecraft.getWindow();
    }
}
