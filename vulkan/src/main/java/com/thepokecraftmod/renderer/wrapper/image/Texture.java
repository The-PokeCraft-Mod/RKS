package com.thepokecraftmod.renderer.wrapper.image;

import com.thepokecraftmod.renderer.wrapper.core.VkBuffer;
import com.thepokecraftmod.renderer.wrapper.cmd.CmdBuffer;
import com.thepokecraftmod.renderer.wrapper.init.Device;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferFloat;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK11.*;

public class Texture {
    private static final Logger LOGGER = LoggerFactory.getLogger(Texture.class);
    private final int height;
    private final int mipLevels;
    private final int width;
    private final String textureId;
    private final boolean transparent;
    private Image image;
    private ImageView imageView;
    private boolean recordedTransition;
    private VkBuffer stgBuffer;

    public Texture(Device device, String textureId, BufferedImage cpuTexture, boolean transparent, int imageFormat) {
        LOGGER.info("Creating texture [{}]", textureId);
        var imgBuffer = cpuTexture.getData().getDataBuffer();
        var rgbaBuffer = (ByteBuffer) null;
        this.width = cpuTexture.getWidth();
        this.height = cpuTexture.getHeight();
        this.mipLevels = (int) (log2(Math.min(width, height)) + 1);
        this.transparent = transparent;
        this.textureId = textureId;

        // Java API Issue
        //noinspection ChainOfInstanceofChecks
        if (imgBuffer instanceof DataBufferFloat intBuffer) {
            var rawData = intBuffer.getData();
            rgbaBuffer = MemoryUtil.memAlloc(rawData.length * 4);

            for (var hdrChannel : rawData) {
                var pixel = hdrToRgb(hdrChannel);
                rgbaBuffer.put((byte) ((pixel >> 16) & 0xFF));
                rgbaBuffer.put((byte) ((pixel >> 8) & 0xFF));
                rgbaBuffer.put((byte) (pixel & 0xFF));
                rgbaBuffer.put((byte) ((pixel >> 24) & 0xFF));
            }

            rgbaBuffer.flip();
        } else if (imgBuffer instanceof DataBufferInt floatBuffer) {
            var rawData = floatBuffer.getData();
            rgbaBuffer = MemoryUtil.memAlloc(rawData.length * 4);

            for (var pixel : rawData) {
                rgbaBuffer.put((byte) ((pixel >> 16) & 0xFF));
                rgbaBuffer.put((byte) ((pixel >> 8) & 0xFF));
                rgbaBuffer.put((byte) (pixel & 0xFF));
                rgbaBuffer.put((byte) ((pixel >> 24) & 0xFF));
            }

            rgbaBuffer.flip();
        } else throw new RuntimeException("Unknown Data Type: " + imgBuffer.getClass().getName());

        createTextureResources(device, rgbaBuffer, imageFormat);
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
        this.stgBuffer = new VkBuffer(device, size, VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
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
        this.imageView = new ImageView(device, this.image.vk(), imageViewData);
    }

    public String getTextureId() {
        return this.textureId;
    }

    public ImageView getImageView() {
        return this.imageView;
    }

    public boolean hasTransparencies() {
        return this.transparent;
    }

    private double log2(int n) {
        return Math.log(n) / Math.log(2);
    }

    private void recordCopyBuffer(MemoryStack stack, CmdBuffer cmd, VkBuffer bufferData) {
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

        vkCmdCopyBufferToImage(cmd.vk(), bufferData.getBuffer(), this.image.vk(), VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);
    }

    private void recordGenerateMipMaps(MemoryStack stack, CmdBuffer cmd) {
        var subResourceRange = VkImageSubresourceRange.calloc(stack)
                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                .baseArrayLayer(0)
                .levelCount(1)
                .layerCount(1);

        var barrier = VkImageMemoryBarrier.calloc(1, stack)
                .sType$Default()
                .image(this.image.vk())
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
                    this.image.vk(), VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    this.image.vk(), VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
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
                .image(this.image.vk())
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
            LOGGER.info("Recording transition for texture [{}]", this.textureId);
            this.recordedTransition = true;
            try (var stack = MemoryStack.stackPush()) {
                recordImageTransition(stack, cmd);
                recordCopyBuffer(stack, cmd, this.stgBuffer);
                recordGenerateMipMaps(stack, cmd);
            }
        } else LOGGER.info("Texture [{}] has already been transitioned", this.textureId);
    }

    private static int hdrToRgb(float hdr) {
        return (int) Math.min(Math.max(Math.pow(hdr, 1.0 / 2.2) * 255, 0), 255);
    }
}
