package com.thepokecraftmod.renderer.impl.geometry;

import com.thepokecraftmod.renderer.wrapper.image.Image;
import com.thepokecraftmod.renderer.wrapper.image.ImageView;
import com.thepokecraftmod.renderer.wrapper.renderpass.Attachment;
import com.thepokecraftmod.renderer.wrapper.init.Device;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.vulkan.VK11.*;

public class GeometryAttachments {
    private static final int NUMBER_ATTACHMENTS = 4;
    public static final int NUMBER_COLOR_ATTACHMENTS = NUMBER_ATTACHMENTS - 1;

    private final List<Attachment> attachments;
    private final Attachment depthAttachment;
    private final int height;
    private final int width;

    public GeometryAttachments(Device device, int width, int height) {
        this.width = width;
        this.height = height;
        this.attachments = new ArrayList<>();

        // Albedo attachment
        this.attachments.add(new Attachment.Builder()
                .image(new Image.Builder()
                        .format(VK_FORMAT_R16G16B16A16_SFLOAT)
                        .usage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)
                        .width(width)
                        .height(height)
                        .build(device))
                .imageView(device, new ImageView.Builder().generateAspectMask(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT))
                .build());

        // Normals attachment
        this.attachments.add(new Attachment.Builder()
                .image(new Image.Builder()
                        .format(VK_FORMAT_A2B10G10R10_UNORM_PACK32)
                        .usage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)
                        .width(width)
                        .height(height)
                        .build(device))
                .imageView(device, new ImageView.Builder().generateAspectMask(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT))
                .build());

        // PBR attachment
        this.attachments.add(new Attachment.Builder()
                .image(new Image.Builder()
                        .format(VK_FORMAT_R16G16B16A16_SFLOAT)
                        .usage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)
                        .width(width)
                        .height(height)
                        .build(device))
                .imageView(device, new ImageView.Builder().generateAspectMask(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT))
                .build());

        // Depth attachment
        this.depthAttachment = new Attachment.Builder()
                .image(new Image.Builder()
                        .format(VK_FORMAT_D32_SFLOAT)
                        .usage(VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)
                        .width(width)
                        .height(height)
                        .build(device))
                .imageView(device, new ImageView.Builder().generateAspectMask(VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT))
                .build();
        this.attachments.add(this.depthAttachment);
    }

    public void close() {
        this.attachments.forEach(Attachment::close);
    }

    public List<Attachment> getAttachments() {
        return this.attachments;
    }

    public Attachment getDepthAttachment() {
        return this.depthAttachment;
    }

    public int getHeight() {
        return this.height;
    }

    public int getWidth() {
        return this.width;
    }
}
