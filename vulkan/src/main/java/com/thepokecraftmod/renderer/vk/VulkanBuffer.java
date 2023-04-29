package com.thepokecraftmod.renderer.vk;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.VkBufferCreateInfo;

import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK11.VK_SHARING_MODE_EXCLUSIVE;

public class VulkanBuffer {

    private final long allocation;
    private final long buffer;
    private final Device device;
    private final PointerBuffer pb;
    private final long requestedSize;

    private long mappedMemory;

    public VulkanBuffer(Device device, long size, int bufferUsage, int memoryUsage,
                        int requiredFlags) {
        this.device = device;
        this.requestedSize = size;
        try (var stack = MemoryStack.stackPush()) {
            var bufferCreateInfo = VkBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .size(size)
                    .usage(bufferUsage)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

            var allocInfo = VmaAllocationCreateInfo.calloc(stack)
                    .requiredFlags(requiredFlags)
                    .usage(memoryUsage);

            var pAllocation = stack.callocPointer(1);
            var lp = stack.mallocLong(1);
            VkUtils.ok(vmaCreateBuffer(device.getMemoryAllocator().getVmaAllocator(), bufferCreateInfo, allocInfo, lp,
                    pAllocation, null), "Failed to create buffer");
            this.buffer = lp.get(0);
            this.allocation = pAllocation.get(0);
            this.pb = MemoryUtil.memAllocPointer(1);
        }
    }

    public void close() {
        MemoryUtil.memFree(this.pb);
        unMap();
        vmaDestroyBuffer(this.device.getMemoryAllocator().getVmaAllocator(), this.buffer, this.allocation);
    }

    public void flush() {
        vmaFlushAllocation(this.device.getMemoryAllocator().getVmaAllocator(), this.allocation, 0, this.requestedSize);
    }

    public long getBuffer() {
        return this.buffer;
    }

    public long getRequestedSize() {
        return this.requestedSize;
    }

    public long map() {
        if (this.mappedMemory == NULL) {
            VkUtils.ok(vmaMapMemory(this.device.getMemoryAllocator().getVmaAllocator(), this.allocation, this.pb), "Failed to map allocation");
            this.mappedMemory = this.pb.get(0);
        }
        return this.mappedMemory;
    }

    public void unMap() {
        if (this.mappedMemory != NULL) {
            vmaUnmapMemory(this.device.getMemoryAllocator().getVmaAllocator(), this.allocation);
            this.mappedMemory = NULL;
        }
    }
}