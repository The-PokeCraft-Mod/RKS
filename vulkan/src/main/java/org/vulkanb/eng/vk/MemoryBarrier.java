package org.vulkanb.eng.vk;

import org.lwjgl.vulkan.VkMemoryBarrier;

public class MemoryBarrier {

    private final VkMemoryBarrier.Buffer vkMemoryBarrier;

    public MemoryBarrier(int srcAccessMask, int dstAccessMask) {
        this.vkMemoryBarrier = VkMemoryBarrier.calloc(1)
                .sType$Default()
                .srcAccessMask(srcAccessMask)
                .dstAccessMask(dstAccessMask);
    }

    public VkMemoryBarrier.Buffer getVkMemoryBarrier() {
        return this.vkMemoryBarrier;
    }
}