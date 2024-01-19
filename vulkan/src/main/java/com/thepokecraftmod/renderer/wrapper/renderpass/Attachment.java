package com.thepokecraftmod.renderer.wrapper.renderpass;

import com.thepokecraftmod.renderer.wrapper.image.Image;
import com.thepokecraftmod.renderer.wrapper.image.ImageView;
import com.thepokecraftmod.renderer.wrapper.init.Device;

import java.io.Closeable;

import static org.lwjgl.vulkan.VK11.*;

public class Attachment implements Closeable {

    private final boolean depthAttachment;
    private final Image image;
    private final ImageView imageView;
    public final int loadOp;
    public final int storeOp;
    public final int stencilLoadOp;
    public final int stencilStoreOp;
    public final int samples;
    public final int initialLayout;
    public final int finalLayout;

    public Attachment(boolean depthAttachment, Image image, ImageView imageView, int loadOp, int storeOp, int stencilLoadOp, int stencilStoreOp, int samples, int initialLayout, int finalLayout) {
        this.depthAttachment = depthAttachment;
        this.image = image;
        this.imageView = imageView;
        this.loadOp = loadOp;
        this.storeOp = storeOp;
        this.stencilLoadOp = stencilLoadOp;
        this.stencilStoreOp = stencilStoreOp;
        this.samples = samples;
        this.initialLayout = initialLayout;
        this.finalLayout = finalLayout;
    }

    public static class Builder {

        private boolean depthAttachment;
        private boolean depthAttachmentExplicit = false;
        private Image image;
        private ImageView imageView;
        private int loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
        private int storeOp = VK_ATTACHMENT_STORE_OP_STORE;
        private int stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
        private int stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
        private int samples = 1;
        private int initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
        private int finalLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;

        public Builder depthAttachment(boolean depthAttachment) {
            this.depthAttachment = depthAttachment;
            this.depthAttachmentExplicit = true;
            return this;
        }

        public Builder image(Image img) {
            this.image = img;
            return this;
        }

        public Builder imageView(Device device, ImageView.Builder viewBuilder) {
            if (image == null) throw new RuntimeException("Image not set");
            viewBuilder.format(this.image.format);
            this.imageView = viewBuilder.build(device, image);
            if (!depthAttachmentExplicit) this.depthAttachment = imageView.aspectMask == VK_IMAGE_ASPECT_DEPTH_BIT;
            if (depthAttachment) this.finalLayout = VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL;
            return this;
        }

        public Builder loadOp(int loadOp) {
            this.loadOp = loadOp;
            return this;
        }

        public Builder storeOp(int storeOp) {
            this.storeOp = storeOp;
            return this;
        }

        public Builder stencilLoadOp(int stencilLoadOp) {
            this.stencilLoadOp = stencilLoadOp;
            return this;
        }

        public Builder stencilStoreOp(int stencilStoreOp) {
            this.stencilStoreOp = stencilStoreOp;
            return this;
        }

        public Builder samples(int samples) {
            this.samples = samples;
            return this;
        }

        public Builder initialLayout(int initialLayout) {
            this.initialLayout = initialLayout;
            return this;
        }

        public Builder finalLayout(int finalLayout) {
            this.finalLayout = finalLayout;
            return this;
        }

        public Attachment build() {
            return new Attachment(
                    depthAttachment,
                    image,
                    imageView,
                    loadOp,
                    storeOp,
                    stencilLoadOp,
                    stencilStoreOp,
                    samples,
                    initialLayout,
                    finalLayout
            );
        }
    }

    @Override
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
