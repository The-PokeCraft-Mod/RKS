package com.thepokecraftmod.renderer.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkSamplerCreateInfo;

import static org.lwjgl.vulkan.VK11.*;

public class TextureSampler {

    private static final int MAX_ANISOTROPY = 16;

    private final Device device;
    private final long vkSampler;

    public TextureSampler(Device device, int mipLevels) {
        this.device = device;
        try (var stack = MemoryStack.stackPush()) {
            var samplerInfo = VkSamplerCreateInfo.calloc(stack)
                    .sType$Default()
                    .magFilter(VK_FILTER_LINEAR)
                    .minFilter(VK_FILTER_LINEAR)
                    .addressModeU(VK_SAMPLER_ADDRESS_MODE_REPEAT)
                    .addressModeV(VK_SAMPLER_ADDRESS_MODE_REPEAT)
                    .addressModeW(VK_SAMPLER_ADDRESS_MODE_REPEAT)
                    .borderColor(VK_BORDER_COLOR_INT_OPAQUE_BLACK)
                    .unnormalizedCoordinates(false)
                    .compareEnable(false)
                    .compareOp(VK_COMPARE_OP_ALWAYS)
                    .mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR)
                    .minLod(0.0f)
                    .maxLod(mipLevels)
                    .mipLodBias(0.0f);
            if (device.isSamplerAnisotropy()) samplerInfo
                    .anisotropyEnable(true)
                    .maxAnisotropy(MAX_ANISOTROPY);

            var lp = stack.mallocLong(1);
            VkUtils.ok(vkCreateSampler(device.getVkDevice(), samplerInfo, null, lp), "Failed to create sampler");
            this.vkSampler = lp.get(0);
        }
    }

    public void close() {
        vkDestroySampler(this.device.getVkDevice(), this.vkSampler, null);
    }

    public long getVkSampler() {
        return this.vkSampler;
    }
}
