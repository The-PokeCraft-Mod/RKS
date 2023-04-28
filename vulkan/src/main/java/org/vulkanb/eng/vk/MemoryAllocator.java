package org.vulkanb.eng.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.VmaAllocatorCreateInfo;
import org.lwjgl.util.vma.VmaVulkanFunctions;
import org.lwjgl.vulkan.VkDevice;

import static org.lwjgl.util.vma.Vma.vmaCreateAllocator;
import static org.lwjgl.util.vma.Vma.vmaDestroyAllocator;

public class MemoryAllocator {

    private final long vmaAllocator;

    public MemoryAllocator(Instance instance, PhysicalDevice physicalDevice, VkDevice vkDevice) {
        try (var stack = MemoryStack.stackPush()) {
            var pAllocator = stack.mallocPointer(1);

            var vmaVulkanFunctions = VmaVulkanFunctions.calloc(stack)
                    .set(instance.getVkInstance(), vkDevice);

            var createInfo = VmaAllocatorCreateInfo.calloc(stack)
                    .instance(instance.getVkInstance())
                    .device(vkDevice)
                    .physicalDevice(physicalDevice.getVkPhysicalDevice())
                    .pVulkanFunctions(vmaVulkanFunctions);
            VkUtils.ok(vmaCreateAllocator(createInfo, pAllocator),
                    "Failed to create VMA allocator");

            this.vmaAllocator = pAllocator.get(0);
        }
    }

    public void close() {
        vmaDestroyAllocator(this.vmaAllocator);
    }

    public long getVmaAllocator() {
        return this.vmaAllocator;
    }
}
