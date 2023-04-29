package com.thepokecraftmod.renderer.vk;

import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSurface;
import org.tinylog.Logger;

public class Surface {

    private final PhysicalDevice physicalDevice;
    private final long vkSurface;

    public Surface(PhysicalDevice physicalDevice, long windowHandle) {
        Logger.debug("Creating Vulkan surface");
        this.physicalDevice = physicalDevice;
        try (var stack = MemoryStack.stackPush()) {
            var pSurface = stack.mallocLong(1);
            GLFWVulkan.glfwCreateWindowSurface(this.physicalDevice.getVkPhysicalDevice().getInstance(), windowHandle,
                    null, pSurface);
            this.vkSurface = pSurface.get(0);
        }
    }

    public void close() {
        Logger.debug("Destroying Vulkan surface");
        KHRSurface.vkDestroySurfaceKHR(this.physicalDevice.getVkPhysicalDevice().getInstance(), this.vkSurface, null);
    }

    public long getVkSurface() {
        return this.vkSurface;
    }
}
