package com.thepokecraftmod.renderer.vk;

import com.thepokecraftmod.renderer.vk.init.Device;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.lwjgl.vulkan.VK11.*;

public class CmdPool implements VkWrapper<Long> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CmdPool.class);
    private final Device device;
    private final long vkCommandPool;

    public CmdPool(Device device, int queueFamilyIndex) {
        try (var stack = MemoryStack.stackPush()) {
            LOGGER.info("Creating Vulkan CommandPool");
            this.device = device;
            var cmdPoolInfo = VkCommandPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
                    .queueFamilyIndex(queueFamilyIndex);

            var lp = stack.mallocLong(1);
            VkUtils.ok(vkCreateCommandPool(device.vk(), cmdPoolInfo, null, lp),
                    "Failed to create command pool");

            this.vkCommandPool = lp.get(0);
        }
    }

    @Override
    public void close() {
        vkDestroyCommandPool(this.device.vk(), this.vkCommandPool, null);
    }

    public Device getDevice() {
        return this.device;
    }

    @Override
    public Long vk() {
        return this.vkCommandPool;
    }
}
