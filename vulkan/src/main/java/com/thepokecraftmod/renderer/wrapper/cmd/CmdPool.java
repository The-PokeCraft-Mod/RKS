package com.thepokecraftmod.renderer.wrapper.cmd;

import com.thepokecraftmod.renderer.wrapper.core.VkUtils;
import com.thepokecraftmod.renderer.wrapper.core.VkWrapper;
import com.thepokecraftmod.renderer.wrapper.init.Device;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.lwjgl.vulkan.VK11.*;

public class CmdPool implements VkWrapper<Long> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CmdPool.class);
    public final Device device;
    private final long cmdPool;

    public CmdPool(Device device, int queueFamilyIndex) {
        try (var stack = MemoryStack.stackPush()) {
            LOGGER.info("Creating Vulkan CommandPool");
            this.device = device;
            var cmdPoolInfo = VkCommandPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
                    .queueFamilyIndex(queueFamilyIndex);

            var pCmdPool = stack.mallocLong(1);
            VkUtils.ok(vkCreateCommandPool(device.vk(), cmdPoolInfo, null, pCmdPool), "Failed to create command pool");
            this.cmdPool = pCmdPool.get(0);
        }
    }

    public CmdBuffer newBuffer(boolean primary, boolean oneTimeSubmit) {
        return new CmdBuffer(this, primary, oneTimeSubmit);
    }

    @Override
    public void close() {
        vkDestroyCommandPool(this.device.vk(), this.cmdPool, null);
    }

    @Override
    public Long vk() {
        return this.cmdPool;
    }
}
