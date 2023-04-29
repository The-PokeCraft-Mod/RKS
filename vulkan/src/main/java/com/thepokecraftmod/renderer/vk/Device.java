package com.thepokecraftmod.renderer.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.lwjgl.vulkan.VK11.*;

public class Device implements VkWrapper<VkDevice> {
    private static final Logger LOGGER = LoggerFactory.getLogger(Device.class);
    private final MemoryAllocator memoryAllocator;
    private final PhysicalDevice physicalDevice;
    private final boolean samplerAnisotropy;
    private final VkDevice vkDevice;

    public Device(Instance instance, PhysicalDevice physicalDevice) {
        LOGGER.info("Creating device");

        this.physicalDevice = physicalDevice;
        try (var stack = MemoryStack.stackPush()) {

            // Define required extensions
            var requiredExtensions = stack.mallocPointer(1);
            requiredExtensions.put(0, stack.ASCII(KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME));

            // Set up required features
            var features = VkPhysicalDeviceFeatures.calloc(stack);
            var supportedFeatures = this.physicalDevice.getVkPhysicalDeviceFeatures();
            this.samplerAnisotropy = supportedFeatures.samplerAnisotropy();
            if (this.samplerAnisotropy) features.samplerAnisotropy(true);
            features.depthClamp(supportedFeatures.depthClamp());
            features.geometryShader(true);
            if (!supportedFeatures.multiDrawIndirect()) throw new RuntimeException("Multi draw Indirect not supported");
            features.multiDrawIndirect(true);

            // Enable all the queue families
            var queuePropsBuff = physicalDevice.getVkQueueFamilyProps();
            var numQueuesFamilies = queuePropsBuff.capacity();
            var queueCreationInfoBuf = VkDeviceQueueCreateInfo.calloc(numQueuesFamilies, stack);
            for (var i = 0; i < numQueuesFamilies; i++) {
                var priorities = stack.callocFloat(queuePropsBuff.get(i).queueCount());
                queueCreationInfoBuf.get(i)
                        .sType$Default()
                        .queueFamilyIndex(i)
                        .pQueuePriorities(priorities);
            }

            var deviceCreateInfo = VkDeviceCreateInfo.calloc(stack)
                    .sType$Default()
                    .ppEnabledExtensionNames(requiredExtensions)
                    .pEnabledFeatures(features)
                    .pQueueCreateInfos(queueCreationInfoBuf);

            var pp = stack.mallocPointer(1);
            VkUtils.ok(vkCreateDevice(physicalDevice.vk(), deviceCreateInfo, null, pp),
                    "Failed to create device");
            this.vkDevice = new VkDevice(pp.get(0), physicalDevice.vk(), deviceCreateInfo);

            this.memoryAllocator = new MemoryAllocator(instance, physicalDevice, this.vkDevice);
        }
    }
    
    @Override
    public void close() {
        LOGGER.info("Closing Vulkan device");
        this.memoryAllocator.close();
        vkDestroyDevice(this.vkDevice, null);
    }

    public MemoryAllocator getMemoryAllocator() {
        return this.memoryAllocator;
    }

    public PhysicalDevice getPhysicalDevice() {
        return this.physicalDevice;
    }
    
    @Override
    public VkDevice vk() {
        return this.vkDevice;
    }

    public boolean isSamplerAnisotropy() {
        return this.samplerAnisotropy;
    }

    public void waitIdle() {
        vkDeviceWaitIdle(this.vkDevice);
    }
}
