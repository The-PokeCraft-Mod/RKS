package com.thepokecraftmod.renderer.vk;

import com.thepokecraftmod.renderer.vk.init.ExtensionProvider;
import com.thepokecraftmod.renderer.vk.init.Instance;
import com.thepokecraftmod.renderer.vk.init.PhysicalDevice;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.VmaAllocatorCreateInfo;
import org.lwjgl.util.vma.VmaVulkanFunctions;
import org.lwjgl.vulkan.VkDevice;

import static org.lwjgl.util.vma.Vma.vmaCreateAllocator;
import static org.lwjgl.util.vma.Vma.vmaDestroyAllocator;

public class MemoryAllocator {

    private final long allocator;

    public MemoryAllocator(Instance instance, PhysicalDevice physicalDevice, VkDevice vkDevice, ExtensionProvider provider) {
        try (var stack = MemoryStack.stackPush()) {
            var pAllocator = stack.mallocPointer(1);
            var vmaVulkanFunctions = VmaVulkanFunctions.calloc(stack).set(instance.vk(), vkDevice);
            var createInfo = VmaAllocatorCreateInfo.calloc(stack)
                    .instance(instance.vk())
                    .device(vkDevice)
                    .physicalDevice(physicalDevice.vk())
                    .flags(provider.vmaFlags)
                    .pVulkanFunctions(vmaVulkanFunctions);

            VkUtils.ok(vmaCreateAllocator(createInfo, pAllocator), "Failed to create VMA allocator");
            this.allocator = pAllocator.get(0);
        }
    }

    public void close() {
        vmaDestroyAllocator(this.allocator);
    }

    public long vma() {
        return this.allocator;
    }
}
