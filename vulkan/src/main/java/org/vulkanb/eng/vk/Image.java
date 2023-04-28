package org.vulkanb.eng.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;

import static org.lwjgl.vulkan.VK11.*;
import static org.vulkanb.eng.vk.VkUtils.ok;

public class Image {

    private final Device device;
    private final int format;
    private final int mipLevels;
    private final long vkImage;
    private final long vkMemory;

    public Image(Device device, ImageData imageData) {
        this.device = device;
        try (var stack = MemoryStack.stackPush()) {
            this.format = imageData.format;
            this.mipLevels = imageData.mipLevels;

            var imageCreateInfo = VkImageCreateInfo.calloc(stack)
                    .sType$Default()
                    .imageType(VK_IMAGE_TYPE_2D)
                    .format(this.format)
                    .extent(it -> it
                            .width(imageData.width)
                            .height(imageData.height)
                            .depth(1)
                    )
                    .mipLevels(this.mipLevels)
                    .arrayLayers(imageData.arrayLayers)
                    .samples(imageData.sampleCount)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .usage(imageData.usage);

            var lp = stack.mallocLong(1);
            ok(vkCreateImage(device.getVkDevice(), imageCreateInfo, null, lp), "Failed to create image");
            this.vkImage = lp.get(0);

            // Get memory requirements for this object
            var memReqs = VkMemoryRequirements.calloc(stack);
            vkGetImageMemoryRequirements(device.getVkDevice(), this.vkImage, memReqs);

            // Select memory size and type
            var memAlloc = VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default()
                    .allocationSize(memReqs.size())
                    .memoryTypeIndex(VkUtils.memoryTypeFromProperties(device.getPhysicalDevice(),
                            memReqs.memoryTypeBits(), 0));

            // Allocate memory
            ok(vkAllocateMemory(device.getVkDevice(), memAlloc, null, lp), "Failed to allocate memory");
            this.vkMemory = lp.get(0);

            // Bind memory
            ok(vkBindImageMemory(device.getVkDevice(), this.vkImage, this.vkMemory, 0),
                    "Failed to bind image memory");
        }
    }

    public void close() {
        vkDestroyImage(this.device.getVkDevice(), this.vkImage, null);
        vkFreeMemory(this.device.getVkDevice(), this.vkMemory, null);
    }

    public int getFormat() {
        return this.format;
    }

    public int getMipLevels() {
        return this.mipLevels;
    }

    public long getVkImage() {
        return this.vkImage;
    }

    public long getVkMemory() {
        return this.vkMemory;
    }

    public static class ImageData {
        private int arrayLayers;
        private int format;
        private int height;
        private int mipLevels;
        private int sampleCount;
        private int usage;
        private int width;

        public ImageData() {
            this.format = VK_FORMAT_R8G8B8A8_SRGB;
            this.mipLevels = 1;
            this.sampleCount = 1;
            this.arrayLayers = 1;
        }

        public ImageData arrayLayers(int arrayLayers) {
            this.arrayLayers = arrayLayers;
            return this;
        }

        public ImageData format(int format) {
            this.format = format;
            return this;
        }

        public ImageData height(int height) {
            this.height = height;
            return this;
        }

        public ImageData mipLevels(int mipLevels) {
            this.mipLevels = mipLevels;
            return this;
        }

        public ImageData sampleCount(int sampleCount) {
            this.sampleCount = sampleCount;
            return this;
        }

        public ImageData usage(int usage) {
            this.usage = usage;
            return this;
        }

        public ImageData width(int width) {
            this.width = width;
            return this;
        }
    }
}