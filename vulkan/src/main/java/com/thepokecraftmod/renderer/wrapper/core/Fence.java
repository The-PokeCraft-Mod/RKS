package com.thepokecraftmod.renderer.wrapper.core;

import com.thepokecraftmod.renderer.wrapper.init.Device;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkFenceCreateInfo;

import static org.lwjgl.vulkan.VK11.*;

public class Fence {

    private final Device device;
    private final long vkFence;

    public Fence(Device device, boolean signaled) {
        this.device = device;
        try (var stack = MemoryStack.stackPush()) {
            var fenceCreateInfo = VkFenceCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(signaled ? VK_FENCE_CREATE_SIGNALED_BIT : 0);

            var lp = stack.mallocLong(1);
            VkUtils.ok(vkCreateFence(device.vk(), fenceCreateInfo, null, lp),
                    "Failed to create semaphore");
            this.vkFence = lp.get(0);
        }
    }

    public void close() {
        vkDestroyFence(this.device.vk(), this.vkFence, null);
    }

    public void waitForFence() {
        vkWaitForFences(this.device.vk(), this.vkFence, true, Long.MAX_VALUE);
    }

    public long getVkFence() {
        return this.vkFence;
    }

    public void reset() {
        vkResetFences(this.device.vk(), this.vkFence);
    }

}
