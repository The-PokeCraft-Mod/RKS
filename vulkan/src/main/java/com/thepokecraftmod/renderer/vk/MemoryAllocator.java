package com.thepokecraftmod.renderer.vk;

import com.thepokecraftmod.renderer.vk.init.Instance;
import com.thepokecraftmod.renderer.vk.init.PhysicalDevice;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vma.VmaAllocatorCreateInfo;
import org.lwjgl.util.vma.VmaVulkanFunctions;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;

import static org.lwjgl.util.vma.Vma.vmaCreateAllocator;
import static org.lwjgl.util.vma.Vma.vmaDestroyAllocator;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceMemoryProperties;
import static org.lwjgl.vulkan.VK11.VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT;

public class MemoryAllocator {

    private final long allocator;

    public MemoryAllocator(Instance instance, PhysicalDevice physicalDevice, VkDevice vkDevice, int flags, boolean sharedMemory) {
        try (var stack = MemoryStack.stackPush()) {
            var pAllocator = stack.mallocPointer(1);
            var vmaVulkanFunctions = VmaVulkanFunctions.calloc(stack).set(instance.vk(), vkDevice);
            var createInfo = VmaAllocatorCreateInfo.calloc(stack)
                    .instance(instance.vk())
                    .device(vkDevice)
                    .physicalDevice(physicalDevice.vk())
                    .flags(flags)
                    .pVulkanFunctions(vmaVulkanFunctions);

            if(sharedMemory) {
                var memoryProperties = VkPhysicalDeviceMemoryProperties.calloc(stack);
                vkGetPhysicalDeviceMemoryProperties(physicalDevice.vk(), memoryProperties);

                var handleTypes = MemoryUtil.memAllocInt(memoryProperties.memoryTypeCount());
                for (var i = 0; i < handleTypes.capacity(); i++)
                    handleTypes.put(i, VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT);
                createInfo.pTypeExternalMemoryHandleTypes(handleTypes);
            }

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
