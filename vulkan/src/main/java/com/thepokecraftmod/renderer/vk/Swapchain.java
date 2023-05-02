package com.thepokecraftmod.renderer.vk;

import com.thepokecraftmod.renderer.Window;
import com.thepokecraftmod.renderer.vk.init.Device;
import com.thepokecraftmod.renderer.vk.init.PhysicalDevice;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import static org.lwjgl.vulkan.VK11.*;

public class Swapchain {
    private static final Logger LOGGER = LoggerFactory.getLogger(Swapchain.class);
    private final Device device;
    private final ImageView[] imageViews;
    private final SurfaceFormat surfaceFormat;
    private final VkExtent2D swapChainExtent;
    private final SyncSemaphores[] syncSemaphoresList;
    private final long vkSwapChain;

    private int currentFrame;

    public Swapchain(Device device, Surface surface, Window window, int requestedImages, boolean vsync) {
        LOGGER.info("Creating Swapchain");
        this.device = device;
        try (var stack = MemoryStack.stackPush()) {
            var physicalDevice = device.getPhysicalDevice();

            // Get surface capabilities
            var surfCapabilities = VkSurfaceCapabilitiesKHR.calloc(stack);
            VkUtils.ok(KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(device.getPhysicalDevice().vk(), surface.getVkSurface(), surfCapabilities), "Failed to get surface capabilities");

            var numImages = calcNumImages(surfCapabilities, requestedImages);

            this.surfaceFormat = calcSurfaceFormat(physicalDevice, surface);
            this.swapChainExtent = calcSwapChainExtent(window, surfCapabilities);

            var vkSwapchainCreateInfo = VkSwapchainCreateInfoKHR.calloc(stack)
                    .sType(KHRSwapchain.VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
                    .surface(surface.getVkSurface())
                    .minImageCount(numImages)
                    .imageFormat(this.surfaceFormat.imageFormat())
                    .imageColorSpace(this.surfaceFormat.colorSpace())
                    .imageExtent(this.swapChainExtent)
                    .imageArrayLayers(1)
                    .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                    .imageSharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .preTransform(surfCapabilities.currentTransform())
                    .compositeAlpha(KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                    .clipped(true)
                    .presentMode(vsync ? KHRSurface.VK_PRESENT_MODE_FIFO_KHR : KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR);
            var lp = stack.mallocLong(1);
            VkUtils.ok(KHRSwapchain.vkCreateSwapchainKHR(device.vk(), vkSwapchainCreateInfo, null, lp), "Failed to create swap chain");
            this.vkSwapChain = lp.get(0);

            this.imageViews = createImageViews(stack, device, this.vkSwapChain, this.surfaceFormat.imageFormat);
            numImages = this.imageViews.length;
            this.syncSemaphoresList = new SyncSemaphores[numImages];
            Arrays.setAll(this.syncSemaphoresList, i -> new SyncSemaphores(device));
            this.currentFrame = 0;
        }
    }

    public boolean acquireNextImage() {
        var resize = false;
        try (var stack = MemoryStack.stackPush()) {
            var ip = stack.mallocInt(1);
            var err = KHRSwapchain.vkAcquireNextImageKHR(this.device.vk(), this.vkSwapChain, ~0L,
                    this.syncSemaphoresList[this.currentFrame].imgAcquisitionSemaphore().getVkSemaphore(), MemoryUtil.NULL, ip);
            if (err == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR) resize = true;
            else if (err == KHRSwapchain.VK_SUBOPTIMAL_KHR) {
                // Not optimal but swapchain can still be used
            } else if (err != VK_SUCCESS) throw new RuntimeException("Failed to acquire image: " + err);
            this.currentFrame = ip.get(0);
        }

        return resize;
    }

    private int calcNumImages(VkSurfaceCapabilitiesKHR surfCapabilities, int requestedImages) {
        var maxImages = surfCapabilities.maxImageCount();
        var minImages = surfCapabilities.minImageCount();
        var result = minImages;
        if (maxImages != 0) result = Math.min(requestedImages, maxImages);
        result = Math.max(result, minImages);
        LOGGER.info("Requested [{}] images, got [{}] images. Surface capabilities, maxImages: [{}], minImages [{}]", requestedImages, result, maxImages, minImages);
        return result;
    }

    private SurfaceFormat calcSurfaceFormat(PhysicalDevice physicalDevice, Surface surface) {
        int imageFormat;
        int colorSpace;
        try (var stack = MemoryStack.stackPush()) {

            var ip = stack.mallocInt(1);
            VkUtils.ok(KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice.vk(),
                    surface.getVkSurface(), ip, null), "Failed to get the number surface formats");
            var numFormats = ip.get(0);
            if (numFormats <= 0) throw new RuntimeException("No surface formats retrieved");

            var surfaceFormats = VkSurfaceFormatKHR.calloc(numFormats, stack);
            VkUtils.ok(KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice.vk(),
                    surface.getVkSurface(), ip, surfaceFormats), "Failed to get surface formats");

            imageFormat = VK_FORMAT_B8G8R8A8_SRGB;
            colorSpace = surfaceFormats.get(0).colorSpace();
            for (var i = 0; i < numFormats; i++) {
                var surfaceFormatKHR = surfaceFormats.get(i);
                if (surfaceFormatKHR.format() == VK_FORMAT_B8G8R8A8_SRGB &&
                        surfaceFormatKHR.colorSpace() == KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                    imageFormat = surfaceFormatKHR.format();
                    colorSpace = surfaceFormatKHR.colorSpace();
                    break;
                }
            }
        }
        return new SurfaceFormat(imageFormat, colorSpace);
    }

    public VkExtent2D calcSwapChainExtent(Window window, VkSurfaceCapabilitiesKHR surfCapabilities) {
        var result = VkExtent2D.calloc();
        // Surface already defined, just use that for the swap chain
        if (surfCapabilities.currentExtent().width() == 0xFFFFFFFF) {
            // Surface size undefined. Set to the window size if within bounds
            var width = Math.min(window.getWidth(), surfCapabilities.maxImageExtent().width());
            width = Math.max(width, surfCapabilities.minImageExtent().width());

            var height = Math.min(window.getHeight(), surfCapabilities.maxImageExtent().height());
            height = Math.max(height, surfCapabilities.minImageExtent().height());

            result.width(width);
            result.height(height);
        } else result.set(surfCapabilities.currentExtent());
        return result;
    }

    public void close() {
        LOGGER.info("Closing Vulkan SwapChain");
        this.swapChainExtent.free();

        var size = this.imageViews != null ? this.imageViews.length : 0;
        for (var i = 0; i < size; i++) {
            this.imageViews[i].close();
            this.syncSemaphoresList[i].close();
        }

        KHRSwapchain.vkDestroySwapchainKHR(this.device.vk(), this.vkSwapChain, null);
    }

    private ImageView[] createImageViews(MemoryStack stack, Device device, long swapChain, int format) {
        ImageView[] result;

        var ip = stack.mallocInt(1);
        VkUtils.ok(KHRSwapchain.vkGetSwapchainImagesKHR(device.vk(), swapChain, ip, null), "Failed to get number of surface images");
        var numImages = ip.get(0);

        var swapChainImages = stack.mallocLong(numImages);
        VkUtils.ok(KHRSwapchain.vkGetSwapchainImagesKHR(device.vk(), swapChain, ip, swapChainImages), "Failed to get surface images");

        result = new ImageView[numImages];
        var imageViewData = new ImageView.ImageViewData().format(format).aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
        for (var i = 0; i < numImages; i++) result[i] = new ImageView(device, swapChainImages.get(i), imageViewData);

        return result;
    }

    public int getCurrentFrame() {
        return this.currentFrame;
    }

    public Device getDevice() {
        return this.device;
    }

    public ImageView[] getImageViews() {
        return this.imageViews;
    }

    public int getImageCount() {
        return this.imageViews.length;
    }

    public SurfaceFormat getSurfaceFormat() {
        return this.surfaceFormat;
    }

    public VkExtent2D getSwapChainExtent() {
        return this.swapChainExtent;
    }

    public SyncSemaphores[] getSyncSemaphoresList() {
        return this.syncSemaphoresList;
    }

    public long getVkSwapChain() {
        return this.vkSwapChain;
    }

    public boolean presentImage(Queue queue) {
        var resize = false;
        try (var stack = MemoryStack.stackPush()) {
            var present = VkPresentInfoKHR.calloc(stack)
                    .sType(KHRSwapchain.VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
                    .pWaitSemaphores(stack.longs(this.syncSemaphoresList[this.currentFrame].renderCompleteSemaphore().getVkSemaphore()))
                    .swapchainCount(1)
                    .pSwapchains(stack.longs(this.vkSwapChain))
                    .pImageIndices(stack.ints(this.currentFrame));

            var err = KHRSwapchain.vkQueuePresentKHR(queue.getVkQueue(), present);
            if (err == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR) resize = true;
            else if (err == KHRSwapchain.VK_SUBOPTIMAL_KHR) {
                // Not optimal but swap chain can still be used
            } else if (err != VK_SUCCESS) throw new RuntimeException("Failed to present KHR: " + err);
        }
        this.currentFrame = (this.currentFrame + 1) % this.imageViews.length;
        return resize;
    }

    public record SurfaceFormat(int imageFormat, int colorSpace) {
    }

    public record SyncSemaphores(Semaphore imgAcquisitionSemaphore, Semaphore geometryCompleteSemaphore, Semaphore renderCompleteSemaphore) {

        public SyncSemaphores(Device device) {
            this(new Semaphore(device), new Semaphore(device), new Semaphore(device));
        }

        public void close() {
            this.imgAcquisitionSemaphore.close();
            this.geometryCompleteSemaphore.close();
            this.renderCompleteSemaphore.close();
        }
    }
}
