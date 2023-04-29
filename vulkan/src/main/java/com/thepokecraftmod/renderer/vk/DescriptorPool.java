package com.thepokecraftmod.renderer.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.tinylog.Logger;

import java.util.List;

import static org.lwjgl.vulkan.VK11.*;

// TODO: better layout for having multiple objects
public class DescriptorPool {
    private final Device device;
    private final long descriptorPool;

    public DescriptorPool(Device device, List<DescriptorTypeCount> descriptorTypeCounts) {
        try (var stack = MemoryStack.stackPush()) {
            Logger.debug("Creating descriptor pool");
            this.device = device;
            var maxSets = 0;
            var typeCount = descriptorTypeCounts.size();
            var typeCounts = VkDescriptorPoolSize.calloc(typeCount, stack);
            for (var i = 0; i < typeCount; i++) {
                maxSets += descriptorTypeCounts.get(i).count();
                typeCounts.get(i)
                        .type(descriptorTypeCounts.get(i).descriptorType())
                        .descriptorCount(descriptorTypeCounts.get(i).count());
            }

            var descriptorPoolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT)
                    .pPoolSizes(typeCounts)
                    .maxSets(maxSets);

            var pDescriptorPool = stack.mallocLong(1);
            VkUtils.ok(vkCreateDescriptorPool(device.getVkDevice(), descriptorPoolInfo, null, pDescriptorPool), "Failed to create descriptor pool");
            this.descriptorPool = pDescriptorPool.get(0);
        }
    }

    public void close() {
        Logger.debug("Destroying descriptor pool");
        vkDestroyDescriptorPool(this.device.getVkDevice(), this.descriptorPool, null);
    }

    public void freeDescriptorSet(long vkDescriptorSet) {
        try (var stack = MemoryStack.stackPush()) {
            var longBuffer = stack.mallocLong(1);
            longBuffer.put(0, vkDescriptorSet);

            VkUtils.ok(vkFreeDescriptorSets(this.device.getVkDevice(), this.descriptorPool, longBuffer), "Failed to free descriptor set");
        }
    }

    public Device getDevice() {
        return this.device;
    }

    public long vk() {
        return this.descriptorPool;
    }

    public record DescriptorTypeCount(
            int count,
            int descriptorType
    ) {}
}
