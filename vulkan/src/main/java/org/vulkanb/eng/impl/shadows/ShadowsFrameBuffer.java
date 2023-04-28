package org.vulkanb.eng.impl.shadows;

import org.lwjgl.system.MemoryStack;
import org.tinylog.Logger;
import org.vulkanb.eng.Settings;
import org.vulkanb.eng.vk.*;

import static org.lwjgl.vulkan.VK11.*;

public class ShadowsFrameBuffer {

    private final Attachment depthAttachment;
    private final FrameBuffer frameBuffer;
    private final ShadowsRenderPass shadowsRenderPass;

    public ShadowsFrameBuffer(Device device) {
        Logger.debug("Creating ShadowsFrameBuffer");
        try (var stack = MemoryStack.stackPush()) {
            var usage = VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT;
            var settings = Settings.getInstance();
            var shadowMapSize = settings.getShadowMapSize();
            var imageData = new Image.ImageData().width(shadowMapSize).height(shadowMapSize).
                    usage(usage | VK_IMAGE_USAGE_SAMPLED_BIT).
                    format(VK_FORMAT_D32_SFLOAT).arrayLayers(VkConstants.SHADOW_MAP_CASCADE_COUNT);
            var depthImage = new Image(device, imageData);

            var imageViewData = new ImageView.ImageViewData().format(depthImage.getFormat()).
                    aspectMask(Attachment.calcAspectMask(usage)).viewType(VK_IMAGE_VIEW_TYPE_2D_ARRAY).
                    baseArrayLayer(0).layerCount(VkConstants.SHADOW_MAP_CASCADE_COUNT);
            var depthImageView = new ImageView(device, depthImage.getVkImage(), imageViewData);
            this.depthAttachment = new Attachment(depthImage, depthImageView, true);

            this.shadowsRenderPass = new ShadowsRenderPass(device, this.depthAttachment);

            var attachmentsBuff = stack.mallocLong(1);
            attachmentsBuff.put(0, this.depthAttachment.getImageView().getVkImageView());
            this.frameBuffer = new FrameBuffer(device, shadowMapSize, shadowMapSize, attachmentsBuff,
                    this.shadowsRenderPass.getVkRenderPass(), VkConstants.SHADOW_MAP_CASCADE_COUNT);
        }
    }

    public void close() {
        Logger.debug("Destroying ShadowsFrameBuffer");
        this.shadowsRenderPass.close();
        this.depthAttachment.close();
        this.frameBuffer.close();
    }

    public Attachment getDepthAttachment() {
        return this.depthAttachment;
    }

    public FrameBuffer getFrameBuffer() {
        return this.frameBuffer;
    }

    public ShadowsRenderPass getRenderPass() {
        return this.shadowsRenderPass;
    }
}