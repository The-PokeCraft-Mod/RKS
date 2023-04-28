package org.vulkanb.eng.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.tinylog.Logger;

import static org.lwjgl.vulkan.VK11.*;

public abstract class DescriptorSetLayout {
    private final Device device;

    protected long vkDescriptorLayout;

    protected DescriptorSetLayout(Device device) {
        this.device = device;
    }

    public void close() {
        Logger.debug("Destroying descriptor set layout");
        vkDestroyDescriptorSetLayout(this.device.getVkDevice(), this.vkDescriptorLayout, null);
    }

    public long vk() {
        return this.vkDescriptorLayout;
    }

    public static class DynUniformDescriptorSetLayout extends SimpleDescriptorSetLayout {
        public DynUniformDescriptorSetLayout(Device device, int binding, int stage) {
            super(device, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC, 1, binding, stage);
        }
    }

    public static class SamplerDescriptorSetLayout extends SimpleDescriptorSetLayout {
        public SamplerDescriptorSetLayout(Device device, int descriptorCount, int binding, int stage) {
            super(device, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, descriptorCount, binding, stage);
        }
    }

    public static class SimpleDescriptorSetLayout extends DescriptorSetLayout {

        public SimpleDescriptorSetLayout(Device device, int descriptorType, int descriptorCount, int binding, int stage) {
            super(device);
            try (var stack = MemoryStack.stackPush()) {
                var layoutBindings = VkDescriptorSetLayoutBinding.calloc(1, stack);
                layoutBindings.get(0)
                        .binding(binding)
                        .descriptorType(descriptorType)
                        .descriptorCount(descriptorCount)
                        .stageFlags(stage);

                var layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                        .sType$Default()
                        .pBindings(layoutBindings);

                var pSetLayout = stack.mallocLong(1);
                VkUtils.ok(vkCreateDescriptorSetLayout(device.getVkDevice(), layoutInfo, null, pSetLayout),
                        "Failed to create descriptor set layout");
                super.vkDescriptorLayout = pSetLayout.get(0);
            }
        }
    }

    public static class StorageDescriptorSetLayout extends SimpleDescriptorSetLayout {
        public StorageDescriptorSetLayout(Device device, int binding, int stage) {
            super(device, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 1, binding, stage);
        }
    }

    public static class UniformDescriptorSetLayout extends SimpleDescriptorSetLayout {
        public UniformDescriptorSetLayout(Device device, int binding, int stage) {
            super(device, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 1, binding, stage);
        }
    }
}
