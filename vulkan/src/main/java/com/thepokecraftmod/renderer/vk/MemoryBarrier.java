package com.thepokecraftmod.renderer.vk;

import org.lwjgl.vulkan.VkMemoryBarrier;

public class MemoryBarrier implements VkWrapper<VkMemoryBarrier.Buffer> {

    private final VkMemoryBarrier.Buffer memoryBarrier;

    public MemoryBarrier(int srcAccessMask, int dstAccessMask) {
        this.memoryBarrier = VkMemoryBarrier.calloc(1)
                .sType$Default()
                .srcAccessMask(srcAccessMask)
                .dstAccessMask(dstAccessMask);
    }

    @Override
    public void close() {}

    public VkMemoryBarrier.Buffer vk() {
        return this.memoryBarrier;
    }
}