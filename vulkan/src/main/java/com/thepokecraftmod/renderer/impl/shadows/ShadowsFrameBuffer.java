package com.thepokecraftmod.renderer.impl.shadows;


import com.thepokecraftmod.renderer.wrapper.core.Settings;
import com.thepokecraftmod.renderer.wrapper.core.VkConstants;
import com.thepokecraftmod.renderer.wrapper.image.Image;
import com.thepokecraftmod.renderer.wrapper.image.ImageView;
import com.thepokecraftmod.renderer.wrapper.init.Device;
import com.thepokecraftmod.renderer.wrapper.renderpass.Attachment;
import com.thepokecraftmod.renderer.wrapper.renderpass.FrameBuffer;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.lwjgl.vulkan.VK11.*;

public class ShadowsFrameBuffer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShadowsFrameBuffer.class);
    private final Attachment depthAttachment;
    private final FrameBuffer frameBuffer;
    private final ShadowsRenderPass shadowsRenderPass;

    public ShadowsFrameBuffer(Device device) {
        LOGGER.info("Creating ShadowsFrameBuffer");
        try (var stack = MemoryStack.stackPush()) {
            var usage = VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT;
            var settings = Settings.getInstance();
            var shadowMapSize = settings.getShadowMapSize();
            var depthImage = new Image.Builder()
                    .width(shadowMapSize)
                    .height(shadowMapSize)
                    .usage(usage | VK_IMAGE_USAGE_SAMPLED_BIT)
                    .format(VK_FORMAT_D32_SFLOAT)
                    .arrayLayers(VkConstants.SHADOW_MAP_CASCADE_COUNT)
                    .build(device);

            var viewBuilder = new ImageView.Builder()
                    .format(depthImage.format)
                    .generateAspectMask(usage)
                    .viewType(VK_IMAGE_VIEW_TYPE_2D_ARRAY)
                    .baseArrayLayer(0)
                    .layerCount(VkConstants.SHADOW_MAP_CASCADE_COUNT);

            this.depthAttachment = new Attachment.Builder()
                    .depthAttachment(true)
                    .image(depthImage)
                    .imageView(device, new ImageView.Builder()
                            .format(depthImage.format)
                            .generateAspectMask(usage)
                            .viewType(VK_IMAGE_VIEW_TYPE_2D_ARRAY)
                            .baseArrayLayer(0)
                            .layerCount(VkConstants.SHADOW_MAP_CASCADE_COUNT))
                    .build();

            this.shadowsRenderPass = new ShadowsRenderPass(device, this.depthAttachment);

            var attachmentsBuff = stack.mallocLong(1);
            attachmentsBuff.put(0, this.depthAttachment.getImageView().vk());
            this.frameBuffer = new FrameBuffer(device, shadowMapSize, shadowMapSize, attachmentsBuff, this.shadowsRenderPass.getVkRenderPass(), VkConstants.SHADOW_MAP_CASCADE_COUNT);
        }
    }

    public void close() {
        LOGGER.info("Closing");
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
