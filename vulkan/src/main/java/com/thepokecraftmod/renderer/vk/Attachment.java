package com.thepokecraftmod.renderer.vk;

import com.thepokecraftmod.renderer.vk.init.Device;

import static org.lwjgl.vulkan.VK11.*;

public class Attachment {

    private final boolean depthAttachment;
    private final Image image;
    private final ImageView imageView;

    // TODO: builder
    public Attachment(Image image, ImageView imageView, boolean depthAttachment) {
        this.image = image;
        this.imageView = imageView;
        this.depthAttachment = depthAttachment;
    }

    public Attachment(Device device, Image image, int usage) {
        this.image = image;

        var aspectMask = calcAspectMask(usage);
        this.depthAttachment = aspectMask == VK_IMAGE_ASPECT_DEPTH_BIT;

        var imageViewData = new ImageView.ImageViewData()
                .format(this.image.getFormat())
                .aspectMask(aspectMask);
        this.imageView = new ImageView(device, this.image.vk(), imageViewData);
    }

    public Attachment(Device device, int width, int height, int format, int usage) {
        var imageData = new Image.ImageData().width(width).height(height).
                usage(usage | VK_IMAGE_USAGE_SAMPLED_BIT).
                format(format);
        this.image = new Image(device, imageData);

        var aspectMask = calcAspectMask(usage);
        this.depthAttachment = aspectMask == VK_IMAGE_ASPECT_DEPTH_BIT;

        var imageViewData = new ImageView.ImageViewData().format(this.image.getFormat()).aspectMask(aspectMask);
        this.imageView = new ImageView(device, this.image.vk(), imageViewData);
    }

    public static int calcAspectMask(int usage) {
        var aspectMask = 0;
        if ((usage & VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT) > 0) aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
        if ((usage & VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT) > 0) aspectMask = VK_IMAGE_ASPECT_DEPTH_BIT;
        return aspectMask;
    }

    public void close() {
        this.imageView.close();
        this.image.close();
    }

    public Image getImage() {
        return this.image;
    }

    public ImageView getImageView() {
        return this.imageView;
    }

    public boolean isDepthAttachment() {
        return this.depthAttachment;
    }
}
