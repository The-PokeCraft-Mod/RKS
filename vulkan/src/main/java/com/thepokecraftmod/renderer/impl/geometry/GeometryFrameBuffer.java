package com.thepokecraftmod.renderer.impl.geometry;

import com.thepokecraftmod.renderer.wrapper.core.Swapchain;
import com.thepokecraftmod.renderer.wrapper.image.Image;
import com.thepokecraftmod.renderer.wrapper.image.ImageView;
import com.thepokecraftmod.renderer.wrapper.renderpass.Attachment;
import com.thepokecraftmod.renderer.wrapper.renderpass.FrameBuffer;
import com.thepokecraftmod.renderer.wrapper.renderpass.RenderPass;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;

import static org.lwjgl.vulkan.VK10.*;

public class GeometryFrameBuffer implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(GeometryFrameBuffer.class);
    private RenderPass geometryRenderPass;

    private FrameBuffer frameBuffer;

    public GeometryFrameBuffer(Swapchain swapChain) {
        LOGGER.info("Creating GeometryFrameBuffer");
        createAttachments(swapChain);
        createFrameBuffer(swapChain);
    }

    @Override
    public void close() {
        LOGGER.info("Closing");
        geometryRenderPass.close();
        frameBuffer.close();
    }

    private void createAttachments(Swapchain swapchain) {
        var extent2D = swapchain.getSwapChainExtent();
        var width = extent2D.width();
        var height = extent2D.height();
        var device = swapchain.getDevice();

        this.geometryRenderPass = new RenderPass.Builder()
                .colorAttachment(new Attachment.Builder()
                        .image(new Image.Builder()
                                .format(VK_FORMAT_R16G16B16A16_SFLOAT)
                                .usage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)
                                .width(width)
                                .height(height)
                                .build(device))
                        .imageView(device, new ImageView.Builder().generateAspectMask(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT))
                        .build())
                .colorAttachment(new Attachment.Builder()
                        .image(new Image.Builder()
                                .format(VK_FORMAT_A2B10G10R10_UNORM_PACK32)
                                .usage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)
                                .width(width)
                                .height(height)
                                .build(device))
                        .imageView(device, new ImageView.Builder().generateAspectMask(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT))
                        .build())
                .colorAttachment(new Attachment.Builder()
                        .image(new Image.Builder()
                                .format(VK_FORMAT_R16G16B16A16_SFLOAT)
                                .usage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)
                                .width(width)
                                .height(height)
                                .build(device))
                        .imageView(device, new ImageView.Builder().generateAspectMask(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT))
                        .build())
                .depthAttachment(new Attachment.Builder()
                        .image(new Image.Builder()
                                .format(VK_FORMAT_D32_SFLOAT)
                                .usage(VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)
                                .width(width)
                                .height(height)
                                .build(device))
                        .imageView(device, new ImageView.Builder().generateAspectMask(VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT))
                        .build())
                .build(device);
    }

    private void createFrameBuffer(Swapchain swapchain) {
        try (var stack = MemoryStack.stackPush()) {
            var attachments = geometryRenderPass.attachments;
            var attachmentsBuff = stack.mallocLong(attachments.size());
            for (var attachment : attachments) attachmentsBuff.put(attachment.getImageView().vk());
            attachmentsBuff.flip();

            this.frameBuffer = new FrameBuffer(swapchain.getDevice(), swapchain.getSwapChainExtent().width(), swapchain.getSwapChainExtent().height(), attachmentsBuff, this.geometryRenderPass.vk(), 1);
        }
    }

    public FrameBuffer getFrameBuffer() {
        return this.frameBuffer;
    }

    public RenderPass getRenderPass() {
        return this.geometryRenderPass;
    }

    public void resize(Swapchain swapChain) {
        this.frameBuffer.close();
        this.geometryRenderPass.close();
        createAttachments(swapChain);
        createFrameBuffer(swapChain);
    }
}
