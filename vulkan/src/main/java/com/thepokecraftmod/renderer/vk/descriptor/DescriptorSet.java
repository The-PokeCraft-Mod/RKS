package com.thepokecraftmod.renderer.vk.descriptor;

import com.thepokecraftmod.renderer.vk.VkUtils;
import com.thepokecraftmod.renderer.vk.VulkanBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import static org.lwjgl.vulkan.VK11.*;

public abstract class DescriptorSet {

    protected long vkDescriptorSet;

    public long vk() {
        return this.vkDescriptorSet;
    }

    public static class DynUniformDescriptorSet extends SimpleDescriptorSet {
        public DynUniformDescriptorSet(DescriptorPool descriptorPool, DescriptorSetLayout descriptorSetLayout, VulkanBuffer buffer, int binding, long size) {
            super(descriptorPool, descriptorSetLayout, buffer, binding, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC, size);
        }
    }

    public static class SimpleDescriptorSet extends DescriptorSet {

        public SimpleDescriptorSet(DescriptorPool descriptorPool, DescriptorSetLayout descriptorSetLayout, VulkanBuffer buffer, int binding, int type, long size) {
            try (var stack = MemoryStack.stackPush()) {
                var device = descriptorPool.getDevice();
                var pDescriptorSetLayout = stack.mallocLong(1).put(0, descriptorSetLayout.vk());
                var allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                        .sType$Default()
                        .descriptorPool(descriptorPool.vk())
                        .pSetLayouts(pDescriptorSetLayout);

                var pDescriptorSet = stack.mallocLong(1);
                VkUtils.ok(vkAllocateDescriptorSets(device.getVkDevice(), allocInfo, pDescriptorSet), "Failed to create descriptor set");

                this.vkDescriptorSet = pDescriptorSet.get(0);

                var bufferInfo = VkDescriptorBufferInfo.calloc(1, stack)
                        .buffer(buffer.getBuffer())
                        .offset(0)
                        .range(size);

                var descrBuffer = VkWriteDescriptorSet.calloc(1, stack);

                descrBuffer.get(0)
                        .sType$Default()
                        .dstSet(this.vkDescriptorSet)
                        .dstBinding(binding)
                        .descriptorType(type)
                        .descriptorCount(1)
                        .pBufferInfo(bufferInfo);

                vkUpdateDescriptorSets(device.getVkDevice(), descrBuffer, null);
            }
        }
    }

    public static class StorageDescriptorSet extends SimpleDescriptorSet {

        public StorageDescriptorSet(DescriptorPool descriptorPool, DescriptorSetLayout descriptorSetLayout, VulkanBuffer buffer, int binding) {
            super(descriptorPool, descriptorSetLayout, buffer, binding, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, buffer.getRequestedSize());
        }
    }

    public static class UniformDescriptorSet extends SimpleDescriptorSet {

        public UniformDescriptorSet(DescriptorPool descriptorPool, DescriptorSetLayout descriptorSetLayout,
                                    VulkanBuffer buffer, int binding) {
            super(descriptorPool, descriptorSetLayout, buffer, binding, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
                    buffer.getRequestedSize());
        }
    }
}
