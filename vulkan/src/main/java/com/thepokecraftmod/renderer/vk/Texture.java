package com.thepokecraftmod.renderer.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

import static org.lwjgl.stb.STBImage.*;
import static org.lwjgl.vulkan.VK11.*;

public class Texture {
    private static final Logger LOGGER = LoggerFactory.getLogger(Texture.class);
    private final int height;
    private final int mipLevels;
    private final int width;
    private String fileName;
    private boolean hasTransparencies;
    private Image image;
    private ImageView imageView;
    private boolean recordedTransition;
    private VulkanBuffer stgBuffer;

    public Texture(Device device, String fileName, int imageFormat) {
        LOGGER.info("Creating texture [{}]", fileName);
        this.recordedTransition = false;
        this.fileName = fileName;
        ByteBuffer buf = null;
        try (var stack = MemoryStack.stackPush()) {
            var w = stack.mallocInt(1);
            var h = stack.mallocInt(1);
            var channels = stack.mallocInt(1);

            buf = stbi_load(fileName, w, h, channels, 4);
            if (buf == null)
                throw new RuntimeException("Image file [" + fileName + "] not loaded: " + stbi_failure_reason());

            setHasTransparencies(buf);

            this.width = w.get();
            this.height = h.get();
            this.mipLevels = (int) Math.floor(log2(Math.min(this.width, this.height))) + 1;

            createTextureResources(device, buf, imageFormat);
        } finally {
            if (buf != null) stbi_image_free(buf);
        }
    }

    public Texture(Device device, ByteBuffer buf, int width, int height, int imageFormat) {
        this.width = width;
        this.height = height;
        this.mipLevels = 1;

        createTextureResources(device, buf, imageFormat);
    }

    public void close() {
        closeStaging();
        this.imageView.close();
        this.image.close();
    }

    public void closeStaging() {
        if (this.stgBuffer != null) {
            this.stgBuffer.close();
            this.stgBuffer = null;
        }
    }

    private void createStgBuffer(Device device, ByteBuffer data) {
        var size = data.remaining();
        this.stgBuffer = new VulkanBuffer(device, size, VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        var mappedMemory = this.stgBuffer.map();
        var buffer = MemoryUtil.memByteBuffer(mappedMemory, (int) this.stgBuffer.getRequestedSize());
        buffer.put(data);
        data.flip();

        this.stgBuffer.unMap();
    }

    private void createTextureResources(Device device, ByteBuffer buf, int imageFormat) {
        createStgBuffer(device, buf);
        var imageData = new Image.ImageData().width(this.width).height(this.height).
                usage(VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT).
                format(imageFormat).mipLevels(this.mipLevels);
        this.image = new Image(device, imageData);
        var imageViewData = new ImageView.ImageViewData().format(this.image.getFormat()).
                aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevels(this.mipLevels);
        this.imageView = new ImageView(device, this.image.getVkImage(), imageViewData);
    }

    public String getFileName() {
        return this.fileName;
    }

    public ImageView getImageView() {
        return this.imageView;
    }

    public boolean hasTransparencies() {
        return this.hasTransparencies;
    }

    private double log2(int n) {
        return Math.log(n) / Math.log(2);
    }

    private void recordCopyBuffer(MemoryStack stack, CmdBuffer cmd, VulkanBuffer bufferData) {

        var region = VkBufferImageCopy.calloc(1, stack)
                .bufferOffset(0)
                .bufferRowLength(0)
                .bufferImageHeight(0)
                .imageSubresource(it ->
                        it.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                .mipLevel(0)
                                .baseArrayLayer(0)
                                .layerCount(1)
                )
                .imageOffset(it -> it.x(0).y(0).z(0))
                .imageExtent(it -> it.width(this.width).height(this.height).depth(1));

        vkCmdCopyBufferToImage(cmd.vk(), bufferData.getBuffer(), this.image.getVkImage(),
                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);
    }

    private void recordGenerateMipMaps(MemoryStack stack, CmdBuffer cmd) {
        var subResourceRange = VkImageSubresourceRange.calloc(stack)
                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                .baseArrayLayer(0)
                .levelCount(1)
                .layerCount(1);

        var barrier = VkImageMemoryBarrier.calloc(1, stack)
                .sType$Default()
                .image(this.image.getVkImage())
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .subresourceRange(subResourceRange);

        var mipWidth = this.width;
        var mipHeight = this.height;

        for (var i = 1; i < this.mipLevels; i++) {
            subResourceRange.baseMipLevel(i - 1);
            barrier.subresourceRange(subResourceRange)
                    .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    .newLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                    .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT);

            vkCmdPipelineBarrier(cmd.vk(),
                    VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0,
                    null, null, barrier);

            var auxi = i;
            var srcOffset0 = VkOffset3D.calloc(stack).x(0).y(0).z(0);
            var srcOffset1 = VkOffset3D.calloc(stack).x(mipWidth).y(mipHeight).z(1);
            var dstOffset0 = VkOffset3D.calloc(stack).x(0).y(0).z(0);
            var dstOffset1 = VkOffset3D.calloc(stack)
                    .x(mipWidth > 1 ? mipWidth / 2 : 1).y(mipHeight > 1 ? mipHeight / 2 : 1).z(1);
            var blit = VkImageBlit.calloc(1, stack)
                    .srcOffsets(0, srcOffset0)
                    .srcOffsets(1, srcOffset1)
                    .srcSubresource(it -> it
                            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                            .mipLevel(auxi - 1)
                            .baseArrayLayer(0)
                            .layerCount(1))
                    .dstOffsets(0, dstOffset0)
                    .dstOffsets(1, dstOffset1)
                    .dstSubresource(it -> it
                            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                            .mipLevel(auxi)
                            .baseArrayLayer(0)
                            .layerCount(1));

            vkCmdBlitImage(cmd.vk(),
                    this.image.getVkImage(), VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    this.image.getVkImage(), VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    blit, VK_FILTER_LINEAR);

            barrier.oldLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                    .newLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .srcAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                    .dstAccessMask(VK_ACCESS_SHADER_READ_BIT);

            vkCmdPipelineBarrier(cmd.vk(),
                    VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, 0,
                    null, null, barrier);

            if (mipWidth > 1) mipWidth /= 2;
            if (mipHeight > 1) mipHeight /= 2;
        }

        barrier.subresourceRange(it -> it
                        .baseMipLevel(this.mipLevels - 1))
                .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                .newLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                .dstAccessMask(VK_ACCESS_SHADER_READ_BIT);

        vkCmdPipelineBarrier(cmd.vk(),
                VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, 0,
                null, null, barrier);
    }

    private void recordImageTransition(MemoryStack stack, CmdBuffer cmd) {

        var barrier = VkImageMemoryBarrier.calloc(1, stack)
                .sType$Default()
                .oldLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED)
                .newLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(this.image.getVkImage())
                .subresourceRange(it -> it
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(0)
                        .levelCount(this.mipLevels)
                        .baseArrayLayer(0)
                        .layerCount(1));

        int srcStage;
        int srcAccessMask;
        int dstAccessMask;
        int dstStage;

        if (VK10.VK_IMAGE_LAYOUT_UNDEFINED == VK_IMAGE_LAYOUT_UNDEFINED && VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
            srcStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
            srcAccessMask = 0;
            dstStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
            dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
        } else if (VK10.VK_IMAGE_LAYOUT_UNDEFINED == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL && VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
            srcStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
            srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
            dstStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
            dstAccessMask = VK_ACCESS_SHADER_READ_BIT;
        } else throw new RuntimeException("Unsupported layout transition");

        barrier.srcAccessMask(srcAccessMask);
        barrier.dstAccessMask(dstAccessMask);

        vkCmdPipelineBarrier(cmd.vk(), srcStage, dstStage, 0, null, null, barrier);
    }

    public void recordTextureTransition(CmdBuffer cmd) {
        if (this.stgBuffer != null && !this.recordedTransition) {
            LOGGER.info("Recording transition for texture [{}]", this.fileName);
            this.recordedTransition = true;
            try (var stack = MemoryStack.stackPush()) {
                recordImageTransition(stack, cmd);
                recordCopyBuffer(stack, cmd, this.stgBuffer);
                recordGenerateMipMaps(stack, cmd);
            }
        } else LOGGER.info("Texture [{}] has already been transitioned", this.fileName);
    }

    private void setHasTransparencies(ByteBuffer buf) {
        var numPixels = buf.capacity() / 4;
        var offset = 0;
        this.hasTransparencies = false;
        for (var i = 0; i < numPixels; i++) {
            var a = (0xFF & buf.get(offset + 3));
            if (a < 255) {
                this.hasTransparencies = true;
                break;
            }
            offset += 4;
        }
    }
}
