package com.thepokecraftmod.renderer.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.tinylog.Logger;

import static org.lwjgl.vulkan.VK11.*;

public class CommandPool {

    private final Device device;
    private final long vkCommandPool;

    public CommandPool(Device device, int queueFamilyIndex) {
        Logger.debug("Creating Vulkan CommandPool");

        this.device = device;
        try (var stack = MemoryStack.stackPush()) {
            var cmdPoolInfo = VkCommandPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
                    .queueFamilyIndex(queueFamilyIndex);

            var lp = stack.mallocLong(1);
            VkUtils.ok(vkCreateCommandPool(device.getVkDevice(), cmdPoolInfo, null, lp),
                    "Failed to create command pool");

            this.vkCommandPool = lp.get(0);
        }
    }

    public void close() {
        vkDestroyCommandPool(this.device.getVkDevice(), this.vkCommandPool, null);
    }

    public Device getDevice() {
        return this.device;
    }

    public long getVkCommandPool() {
        return this.vkCommandPool;
    }
}
