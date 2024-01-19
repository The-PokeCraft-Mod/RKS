package com.thepokecraftmod.rks.util;

import com.thepokecraftmod.renderer.Window;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWVulkan.glfwVulkanSupported;

public class DebugWindow implements Window {

    private final long windowHandle;
    private int height;
    private boolean resized;
    private int width;

    public DebugWindow(String title, int width, int height) {
        if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");
        if (!glfwVulkanSupported()) throw new IllegalStateException("Cannot find a compatible Vulkan Driver");

        this.width = width;
        this.height = height;

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
        glfwWindowHint(GLFW_MAXIMIZED, GLFW_FALSE);

        this.windowHandle = glfwCreateWindow(this.width, this.height, title, MemoryUtil.NULL, MemoryUtil.NULL);
        if (this.windowHandle == MemoryUtil.NULL) throw new RuntimeException("Failed to create the GLFW window");

        glfwSetFramebufferSizeCallback(this.windowHandle, (window, w, h) -> resize(w, h));

        glfwSetKeyCallback(this.windowHandle, (window, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) glfwSetWindowShouldClose(window, true);
        });
    }

    public void close() {
        glfwFreeCallbacks(this.windowHandle);
        glfwDestroyWindow(this.windowHandle);
        glfwTerminate();
    }

    public int getHeight() {
        return this.height;
    }

    public int getWidth() {
        return this.width;
    }

    public long handle() {
        return this.windowHandle;
    }

    public boolean isKeyPressed(int keyCode) {
        return glfwGetKey(this.windowHandle, keyCode) == GLFW_PRESS;
    }

    public boolean isResized() {
        return this.resized;
    }

    public void setResized(boolean resized) {
        this.resized = resized;
    }

    public void pollEvents() {
        glfwPollEvents();
    }

    public void resetResized() {
        this.resized = false;
    }

    public void resize(int width, int height) {
        this.resized = true;
        this.width = width;
        this.height = height;
    }

    public boolean shouldClose() {
        return glfwWindowShouldClose(this.windowHandle);
    }
}
