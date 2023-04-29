package com.thepokecraftmod.renderer.impl.geometry;

import org.lwjgl.system.MemoryStack;
import org.tinylog.Logger;
import com.thepokecraftmod.renderer.vk.FrameBuffer;
import com.thepokecraftmod.renderer.vk.SwapChain;

public class GeometryFrameBuffer {

    private final GeometryRenderPass geometryRenderPass;

    private FrameBuffer frameBuffer;
    private GeometryAttachments geometryAttachments;

    public GeometryFrameBuffer(SwapChain swapChain) {
        Logger.debug("Creating GeometryFrameBuffer");
        createAttachments(swapChain);
        this.geometryRenderPass = new GeometryRenderPass(swapChain.getDevice(), this.geometryAttachments.getAttachments());
        createFrameBuffer(swapChain);
    }

    public void close() {
        Logger.debug("Destroying Geometry FrameBuffer");
        this.geometryRenderPass.close();
        this.geometryAttachments.close();
        this.frameBuffer.close();
    }

    private void createAttachments(SwapChain swapChain) {
        var extent2D = swapChain.getSwapChainExtent();
        var width = extent2D.width();
        var height = extent2D.height();
        this.geometryAttachments = new GeometryAttachments(swapChain.getDevice(), width, height);
    }

    private void createFrameBuffer(SwapChain swapChain) {
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

    public void resize(SwapChain swapChain) {
        this.frameBuffer.close();
        this.geometryAttachments.close();
        createAttachments(swapChain);
        createFrameBuffer(swapChain);
    }
}
