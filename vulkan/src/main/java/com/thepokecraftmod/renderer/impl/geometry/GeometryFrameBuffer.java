package com.thepokecraftmod.renderer.impl.geometry;

import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.thepokecraftmod.renderer.wrapper.renderpass.FrameBuffer;
import com.thepokecraftmod.renderer.wrapper.core.Swapchain;

public class GeometryFrameBuffer {
    private static final Logger LOGGER = LoggerFactory.getLogger(GeometryFrameBuffer.class);
    private final GeometryRenderPass geometryRenderPass;

    private FrameBuffer frameBuffer;
    private GeometryAttachments geometryAttachments;

    public GeometryFrameBuffer(Swapchain swapChain) {
        LOGGER.info("Creating GeometryFrameBuffer");
        createAttachments(swapChain);
        this.geometryRenderPass = new GeometryRenderPass(swapChain.getDevice(), this.geometryAttachments.getAttachments());
        createFrameBuffer(swapChain);
    }

    public void close() {
        LOGGER.info("Closing Geometry FrameBuffer");
        this.geometryRenderPass.close();
        this.geometryAttachments.close();
        this.frameBuffer.close();
    }

    private void createAttachments(Swapchain swapChain) {
        var extent2D = swapChain.getSwapChainExtent();
        var width = extent2D.width();
        var height = extent2D.height();
        this.geometryAttachments = new GeometryAttachments(swapChain.getDevice(), width, height);
    }

    private void createFrameBuffer(Swapchain swapChain) {
        try (var stack = MemoryStack.stackPush()) {
            var attachments = this.geometryAttachments.getAttachments();
            var attachmentsBuff = stack.mallocLong(attachments.size());
            for (var attachment : attachments) attachmentsBuff.put(attachment.getImageView().getVkImageView());
            attachmentsBuff.flip();

            this.frameBuffer = new FrameBuffer(swapChain.getDevice(), this.geometryAttachments.getWidth(), this.geometryAttachments.getHeight(),
                    attachmentsBuff, this.geometryRenderPass.getVkRenderPass(), 1);
        }
    }

    public GeometryAttachments geometryAttachments() {
        return this.geometryAttachments;
    }

    public FrameBuffer getFrameBuffer() {
        return this.frameBuffer;
    }

    public GeometryRenderPass getRenderPass() {
        return this.geometryRenderPass;
    }

    public void resize(Swapchain swapChain) {
        this.frameBuffer.close();
        this.geometryAttachments.close();
        createAttachments(swapChain);
        createFrameBuffer(swapChain);
    }
}
