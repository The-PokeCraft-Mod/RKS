package com.thepokecraftmod.renderer.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkImageViewCreateInfo;

import static org.lwjgl.vulkan.VK11.*;
import static com.thepokecraftmod.renderer.vk.VkUtils.ok;

public class ImageView {

    private final int aspectMask;
    private final Device device;
    private final int mipLevels;
    private final long vkImageView;

    public ImageView(Device device, long vkImage, ImageViewData imageViewData) {
        this.device = device;
        this.aspectMask = imageViewData.aspectMask;
        this.mipLevels = imageViewData.mipLevels;
        try (var stack = MemoryStack.stackPush()) {
            var lp = stack.mallocLong(1);
            var viewCreateInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType$Default()
                    .image(vkImage)
                    .viewType(imageViewData.viewType)
                    .format(imageViewData.format)
                    .subresourceRange(it -> it
                            .aspectMask(this.aspectMask)
                            .baseMipLevel(0)
                            .levelCount(this.mipLevels)
                            .baseArrayLayer(imageViewData.baseArrayLayer)
                            .layerCount(imageViewData.layerCount));

            ok(vkCreateImageView(device.getVkDevice(), viewCreateInfo, null, lp),
                    "Failed to create image view");
            this.vkImageView = lp.get(0);
        }
    }

    public void close() {
        vkDestroyImageView(this.device.getVkDevice(), this.vkImageView, null);
    }

    public int getAspectMask() {
        return this.aspectMask;
    }

    public int getMipLevels() {
        return this.mipLevels;
    }

    public long getVkImageView() {
        return this.vkImageView;
    }

    public static class ImageViewData {
        private int aspectMask;
        private int baseArrayLayer;
        private int format;
        private int layerCount;
        private int mipLevels;
        private int viewType;

        public ImageViewData() {
            this.baseArrayLayer = 0;
            this.layerCount = 1;
            this.mipLevels = 1;
            this.viewType = VK_IMAGE_VIEW_TYPE_2D;
        }

        public ImageViewData aspectMask(int aspectMask) {
            this.aspectMask = aspectMask;
            return this;
        }

        public ImageViewData baseArrayLayer(int baseArrayLayer) {
            this.baseArrayLayer = baseArrayLayer;
            return this;
        }

        public ImageViewData format(int format) {
            this.format = format;
            return this;
        }

        public ImageViewData layerCount(int layerCount) {
            this.layerCount = layerCount;
            return this;
        }

        public ImageViewData mipLevels(int mipLevels) {
            this.mipLevels = mipLevels;
            return this;
        }

        public ImageViewData viewType(int viewType) {
            this.viewType = viewType;
            return this;
        }
    }
}
