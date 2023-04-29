package com.thepokecraftmod.renderer.vk.descriptor;

import com.thepokecraftmod.renderer.vk.Device;
import com.thepokecraftmod.renderer.vk.VkUtils;
import com.thepokecraftmod.renderer.vk.VkWrapper;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.lwjgl.vulkan.VK11.*;

public abstract class DescriptorSetLayout implements VkWrapper<Long> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DescriptorSetLayout.class);
    private final Device device;
    protected long layout;

    protected DescriptorSetLayout(Device device) {
        this.device = device;
    }

    @Override
    public void close() {
        LOGGER.info("Closing descriptor set layout");
        vkDestroyDescriptorSetLayout(this.device.vk(), this.layout, null);
    }

    @Override
    public Long vk() {
        return layout;
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
                VkUtils.ok(vkCreateDescriptorSetLayout(device.vk(), layoutInfo, null, pSetLayout),
                        "Failed to create descriptor set layout");
                super.layout = pSetLayout.get(0);
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
