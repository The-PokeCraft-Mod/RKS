package com.thepokecraftmod.renderer.vk;

import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSurface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Surface {
    private static final Logger LOGGER = LoggerFactory.getLogger(Surface.class);
    private final PhysicalDevice physicalDevice;
    private final long vkSurface;

    public Surface(PhysicalDevice physicalDevice, long windowHandle) {
        LOGGER.info("Creating Vulkan surface");
        this.physicalDevice = physicalDevice;
        try (var stack = MemoryStack.stackPush()) {
            var pSurface = stack.mallocLong(1);
            GLFWVulkan.glfwCreateWindowSurface(this.physicalDevice.vk().getInstance(), windowHandle,
                    null, pSurface);
            this.vkSurface = pSurface.get(0);
        }
    }

    public void close() {
        LOGGER.info("Closing Vulkan surface");
        KHRSurface.vkDestroySurfaceKHR(this.physicalDevice.vk().getInstance(), this.vkSurface, null);
    }

    public long getVkSurface() {
        return this.vkSurface;
    }
}
