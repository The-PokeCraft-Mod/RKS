package org.vulkanb.eng.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import java.util.Collections;
import java.util.List;

import static org.lwjgl.vulkan.VK11.*;

public class TextureDescriptorSet extends DescriptorSet {

    public TextureDescriptorSet(DescriptorPool descriptorPool, DescriptorSetLayout descriptorSetLayout,
                                Texture texture, TextureSampler textureSampler, int binding) {
        this(descriptorPool, descriptorSetLayout, Collections.singletonList(texture), textureSampler, binding);
    }

    public TextureDescriptorSet(DescriptorPool descriptorPool, DescriptorSetLayout descriptorSetLayout,
                                List<Texture> textureList, TextureSampler textureSampler, int binding) {
        try (var stack = MemoryStack.stackPush()) {
            var device = descriptorPool.getDevice();
            var pDescriptorSetLayout = stack.mallocLong(1);
            pDescriptorSetLayout.put(0, descriptorSetLayout.vk());
            var allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType$Default()
                    .descriptorPool(descriptorPool.vk())
                    .pSetLayouts(pDescriptorSetLayout);

            var pDescriptorSet = stack.mallocLong(1);
            VkUtils.ok(vkAllocateDescriptorSets(device.getVkDevice(), allocInfo, pDescriptorSet),
                    "Failed to create descriptor set");
            this.vkDescriptorSet = pDescriptorSet.get(0);

            var numImages = textureList.size();
            var imageInfo = VkDescriptorImageInfo.calloc(numImages, stack);
            for (var i = 0; i < numImages; i++) {
                var texture = textureList.get(i);
                imageInfo.get(i)
                        .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                        .imageView(texture.getImageView().getVkImageView())
                        .sampler(textureSampler.getVkSampler());
            }

            var descrBuffer = VkWriteDescriptorSet.calloc(1, stack);
            descrBuffer.get(0)
                    .sType$Default()
                    .dstSet(this.vkDescriptorSet)
                    .dstBinding(binding)
                    .dstArrayElement(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(numImages)
                    .pImageInfo(imageInfo);

            vkUpdateDescriptorSets(device.getVkDevice(), descrBuffer, null);
        }
    }
}
