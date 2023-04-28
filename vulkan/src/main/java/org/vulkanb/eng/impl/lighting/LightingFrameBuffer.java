package org.vulkanb.eng.impl.lighting;

import org.lwjgl.system.MemoryStack;
import org.tinylog.Logger;
import org.vulkanb.eng.vk.FrameBuffer;
import org.vulkanb.eng.vk.SwapChain;

import java.util.Arrays;

public class LightingFrameBuffer {

    private final LightingRenderPass lightingRenderPass;

    private FrameBuffer[] frameBuffers;

    public LightingFrameBuffer(SwapChain swapChain) {
        Logger.debug("Creating Lighting FrameBuffer");
        this.lightingRenderPass = new LightingRenderPass(swapChain);
        createFrameBuffers(swapChain);
    }

    public void close() {
        Logger.debug("Destroying Lighting FrameBuffer");
        Arrays.stream(this.frameBuffers).forEach(FrameBuffer::close);
        this.lightingRenderPass.close();
    }

    private void createFrameBuffers(SwapChain swapChain) {
        try (var stack = MemoryStack.stackPush()) {
            var extent2D = swapChain.getSwapChainExtent();
            var width = extent2D.width();
            var height = extent2D.height();

            var numImages = swapChain.getNumImages();
            this.frameBuffers = new FrameBuffer[numImages];
            var attachmentsBuff = stack.mallocLong(1);
            for (var i = 0; i < numImages; i++) {
                attachmentsBuff.put(0, swapChain.getImageViews()[i].getVkImageView());
                this.frameBuffers[i] = new FrameBuffer(swapChain.getDevice(), width, height,
                        attachmentsBuff, this.lightingRenderPass.getVkRenderPass(), 1);
            }
        }
    }

    public FrameBuffer[] getFrameBuffers() {
        return this.frameBuffers;
    }

    public LightingRenderPass getLightingRenderPass() {
        return this.lightingRenderPass;
    }

    public void resize(SwapChain swapChain) {
        Arrays.stream(this.frameBuffers).forEach(FrameBuffer::close);
        createFrameBuffers(swapChain);
    }
}