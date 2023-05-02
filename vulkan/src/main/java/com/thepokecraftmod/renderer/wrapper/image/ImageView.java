package com.thepokecraftmod.renderer.wrapper.image;

import com.thepokecraftmod.renderer.wrapper.core.VkUtils;
import com.thepokecraftmod.renderer.wrapper.core.VkWrapper;
import com.thepokecraftmod.renderer.wrapper.init.Device;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkImageViewCreateInfo;

import static org.lwjgl.vulkan.VK11.*;

public class ImageView implements VkWrapper<Long> {

    public final int aspectMask;
    public final Device device;
    public final int mipLevels;
    private final long imageView;

    private ImageView(Device device, long vkImage, Builder builder) {
        try (var stack = MemoryStack.stackPush()) {
            this.device = device;
            this.aspectMask = builder.aspectMask;
            this.mipLevels = builder.mipLevels;
            var pImageView = stack.mallocLong(1);
            var createInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType$Default()
                    .image(vkImage)
                    .viewType(builder.viewType)
                    .format(builder.format)
                    .subresourceRange(it -> it
                            .aspectMask(this.aspectMask)
                            .baseMipLevel(0)
                            .levelCount(this.mipLevels)
                            .baseArrayLayer(builder.baseArrayLayer)
                            .layerCount(builder.layerCount));

            VkUtils.ok(vkCreateImageView(device.vk(), createInfo, null, pImageView), "Failed to create image view");
            this.imageView = pImageView.get(0);
        }
    }

    @Override
    public void close() {
        vkDestroyImageView(device.vk(), imageView, null);
    }

    @Override
    public Long vk() {
        return imageView;
    }

    public static class Builder {
        public int aspectMask;
        public int baseArrayLayer;
        public int format;
        public int layerCount;
        public int mipLevels;
        public int viewType;

        public Builder() {
            this.baseArrayLayer = 0;
            this.layerCount = 1;
            this.mipLevels = 1;
            this.viewType = VK_IMAGE_VIEW_TYPE_2D;
        }

        public Builder aspectMask(int aspectMask) {
            this.aspectMask = aspectMask;
            return this;
        }

        public Builder generateAspectMask(int usage) {
            var aspectMask = 0;
            if ((usage & VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT) > 0) aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
            if ((usage & VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT) > 0) aspectMask = VK_IMAGE_ASPECT_DEPTH_BIT;
            this.aspectMask = aspectMask;
            return this;
        }

        public Builder baseArrayLayer(int baseArrayLayer) {
            this.baseArrayLayer = baseArrayLayer;
            return this;
        }

        public Builder format(int format) {
            this.format = format;
            return this;
        }

        public Builder layerCount(int layerCount) {
            this.layerCount = layerCount;
            return this;
        }

        public Builder mipLevels(int mipLevels) {
            this.mipLevels = mipLevels;
            return this;
        }

        public Builder viewType(int viewType) {
            this.viewType = viewType;
            return this;
        }

        public ImageView build(Device device, Image image) {
            return new ImageView(device, image.vk(), this);
        }

        public ImageView build(Device device, long image) {
            return new ImageView(device, image, this);
        }
    }
}
