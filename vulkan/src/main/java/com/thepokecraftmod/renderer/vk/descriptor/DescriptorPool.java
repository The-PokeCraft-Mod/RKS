package com.thepokecraftmod.renderer.vk.descriptor;

import com.thepokecraftmod.renderer.vk.init.Device;
import com.thepokecraftmod.renderer.vk.VkUtils;
import com.thepokecraftmod.renderer.vk.VkWrapper;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.lwjgl.vulkan.VK11.*;

public class DescriptorPool implements VkWrapper<Long> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DescriptorPool.class);
    private final Device device;
    private final long descriptorPool;

    public DescriptorPool(Device device, List<DescriptorTypeCount> descriptorTypeCounts, int objects) {
        try (var stack = MemoryStack.stackPush()) {
            LOGGER.info("Creating descriptor pool");
            this.device = device;
            var maxSets = 0;
            var typeCount = descriptorTypeCounts.size();
            var typeCounts = VkDescriptorPoolSize.calloc(typeCount, stack);
            for (var i = 0; i < typeCount; i++) {
                maxSets += descriptorTypeCounts.get(i).count();
                typeCounts.get(i)
                        .type(descriptorTypeCounts.get(i).descriptorType())
                        .descriptorCount(descriptorTypeCounts.get(i).count() * objects);
            }

            var descriptorPoolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT)
                    .pPoolSizes(typeCounts)
                    .maxSets(maxSets * objects);

            var pDescriptorPool = stack.mallocLong(1);
            VkUtils.ok(vkCreateDescriptorPool(device.vk(), descriptorPoolInfo, null, pDescriptorPool), "Failed to create descriptor pool");
            this.descriptorPool = pDescriptorPool.get(0);
        }
    }

    @Override
    public void close() {
        LOGGER.info("Closing descriptor pool");
        vkDestroyDescriptorPool(this.device.vk(), this.descriptorPool, null);
    }

    public void freeDescriptorSet(long vkDescriptorSet) {
        try (var stack = MemoryStack.stackPush()) {
            var longBuffer = stack.mallocLong(1);
            longBuffer.put(0, vkDescriptorSet);

            VkUtils.ok(vkFreeDescriptorSets(this.device.vk(), this.descriptorPool, longBuffer), "Failed to free descriptor set");
        }
    }

    public Device getDevice() {
        return this.device;
    }

    @Override
    public Long vk() {
        return this.descriptorPool;
    }

    public record DescriptorTypeCount(
            int count,
            int descriptorType
    ) {}
}
