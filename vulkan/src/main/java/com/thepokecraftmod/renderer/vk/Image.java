package com.thepokecraftmod.renderer.vk;

import com.thepokecraftmod.renderer.vk.init.Device;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.VkImageCreateInfo;

import java.util.function.Function;

import static com.thepokecraftmod.renderer.vk.VkUtils.ok;
import static org.lwjgl.util.vma.Vma.VMA_MEMORY_USAGE_AUTO;
import static org.lwjgl.vulkan.VK11.*;

public class Image {

    private final Device device;
    private final int format;
    private final int mipLevels;
    private final long image;
    public final long allocation;

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
                    .usage(imageData.usage)
                    .pNext(imageData.pNext.apply(stack));

            var pImage = stack.mallocLong(1);
            var pAlloc = stack.mallocPointer(1);

            var createInfo = VmaAllocationCreateInfo.calloc(stack)
                    .requiredFlags(imageData.properties)
                    .usage(VMA_MEMORY_USAGE_AUTO);

            Vma.vmaCreateImage(device.memoryAllocator.vma(), imageCreateInfo, createInfo, pImage, pAlloc, null);

            ok(vkCreateImage(device.vk(), imageCreateInfo, null, pImage), "Failed to create image");
            this.image = pImage.get(0);
            this.allocation = pAlloc.get(0);

            Vma.vmaBindImageMemory(device.memoryAllocator.vma(), allocation, image);
        }
    }

    public void close() {
        vkDestroyImage(this.device.vk(), this.image, null);
        vkFreeMemory(this.device.vk(), this.allocation, null);
    }

    public int getFormat() {
        return this.format;
    }

    public int getMipLevels() {
        return this.mipLevels;
    }

    public long vk() {
        return this.image;
    }

    public long getAllocation() {
        return this.allocation;
    }

    public static class ImageData {
        private int arrayLayers;
        private int format;
        private int height;
        private int mipLevels;
        private int sampleCount;
        private int usage;
        private int width;
        private int properties;
        private Function<MemoryStack, Long> pNext = stack -> 0L;

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

        public ImageData properties(int properties) {
            this.properties = properties;
            return this;
        }

        public ImageData pNext(Function<MemoryStack, Long> pNext) {
            this.pNext = pNext;
            return this;
        }
    }
}