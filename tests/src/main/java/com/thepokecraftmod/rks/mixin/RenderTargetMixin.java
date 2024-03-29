package com.thepokecraftmod.rks.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.thepokecraftmod.rks.CreeperReplacementTest;
import com.thepokecraftmod.rks.util.InteropUtils;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.lwjgl.vulkan.VK10.*;

@Mixin(RenderTarget.class)
public class RenderTargetMixin {

    @Shadow
    protected int colorTextureId;
    @Shadow
    protected int depthBufferId;
    private InteropUtils.Texture2DVkGL interopDepthTexture;
    private InteropUtils.Texture2DVkGL interopColorTexture;

    @Inject(method = "createBuffers", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;checkStatus()V", ordinal = 0))
    public void createBuffers(int width, int height, boolean clearError, CallbackInfo ci) {
        GlStateManager._deleteTexture(colorTextureId);
        GlStateManager._deleteTexture(depthBufferId);

        this.interopColorTexture = new InteropUtils.Texture2DVkGL(
                CreeperReplacementTest.getVkDevice(),
                width,
                height,
                VK_FORMAT_R8G8B8A8_UNORM,
                VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                1
        );
        InteropUtils.createGlTexture(
                CreeperReplacementTest.getVkDevice(),
                interopColorTexture,
                GL30C.GL_RGBA8,
                GL11C.GL_NEAREST,
                GL11C.GL_NEAREST,
                GL15C.GL_CLAMP_TO_EDGE,
                width,
                height
        );

        this.interopDepthTexture = new InteropUtils.Texture2DVkGL(
                CreeperReplacementTest.getVkDevice(),
                width,
                height,
                VK_FORMAT_D24_UNORM_S8_UINT,
                VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                1
        );
        InteropUtils.createGlTexture(
                CreeperReplacementTest.getVkDevice(),
                interopDepthTexture,
                GL30C.GL_DEPTH24_STENCIL8,
                GL11C.GL_NEAREST,
                GL11C.GL_NEAREST,
                GL15C.GL_CLAMP_TO_EDGE,
                width,
                height
        );

        this.colorTextureId = interopColorTexture.glObjectId;
        this.depthBufferId = interopDepthTexture.glObjectId;
        CreeperReplacementTest.getSharedTextures().put(colorTextureId, interopColorTexture);
        CreeperReplacementTest.getSharedTextures().put(depthBufferId, interopColorTexture);

        fboTex(GL30C.GL_COLOR_ATTACHMENT0, colorTextureId);
        fboTex(GL30C.GL_DEPTH_ATTACHMENT, depthBufferId);

        try (var stack = MemoryStack.stackPush()) {
            var cmdBuffer = CreeperReplacementTest.getRenderer().cmdPool.newBuffer(true, true);
            cmdBuffer.record(CreeperReplacementTest.getRenderer().graphicsQueue, true, () -> {
                vkCmdPipelineBarrier(
                        cmdBuffer.vk(),
                        VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                        VK_PIPELINE_STAGE_ALL_GRAPHICS_BIT,
                        0,
                        null,
                        null,
                        VkImageMemoryBarrier.calloc(1, stack)
                                .sType$Default()
                                .srcAccessMask(0)
                                .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                                .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                                .newLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                                .image(interopColorTexture.image.vk())
                                .subresourceRange(r1 -> r1.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                        .layerCount(1)
                                        .levelCount(1)
                                )
                );

                vkCmdPipelineBarrier(
                        cmdBuffer.vk(),
                        VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                        VK_PIPELINE_STAGE_ALL_GRAPHICS_BIT,
                        0,
                        null,
                        null,
                        VkImageMemoryBarrier.calloc(1, stack)
                                .sType$Default()
                                .srcAccessMask(0)
                                .dstAccessMask(VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT)
                                .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                                .newLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
                                .image(interopDepthTexture.image.vk())
                                .subresourceRange(r1 ->
                                        r1.aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT | VK_IMAGE_ASPECT_STENCIL_BIT)
                                                .layerCount(1)
                                                .levelCount(1))
                );

                return null;
            });
        }
    }

    public void fboTex(int attachment, int glTexId) {
        GlStateManager._bindTexture(glTexId);
        GlStateManager._glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, attachment, GL11C.GL_TEXTURE_2D, glTexId, 0);
    }
}
