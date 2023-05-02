package com.thepokecraftmod.renderer.vk;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import static org.lwjgl.vulkan.VK11.*;

public class VkUtils {

    private VkUtils() {
        // Utility class
    }

    public static void copyMatrixToBuffer(VkBuffer vkBuffer, Matrix4f matrix) {
        copyMatrixToBuffer(vkBuffer, matrix, 0);
    }

    public static void copyMatrixToBuffer(VkBuffer vkBuffer, Matrix4f matrix, int offset) {
        var mappedMemory = vkBuffer.map();
        var matrixBuffer = MemoryUtil.memByteBuffer(mappedMemory, (int) vkBuffer.getRequestedSize());
        matrix.get(offset, matrixBuffer);
        vkBuffer.unMap();
    }

    /**
     * Translates a Vulkan {@code VkResult} value to a String describing the result.
     *
     * @param result the {@code VkResult} value
     */
    public static void ok(int result, String message) {
        if (result != VK_SUCCESS)
            throw new RuntimeException(message + " => " + switch (result) {
                // Success codes
                case VK_NOT_READY -> "A fence or query has not yet completed.";
                case VK_TIMEOUT -> "A wait operation has not completed in the specified time.";
                case VK_EVENT_SET -> "An event is signaled.";
                case VK_EVENT_RESET -> "An event is unsignaled.";
                case VK_INCOMPLETE -> "A return array was too small for the result.";
                case KHRSwapchain.VK_SUBOPTIMAL_KHR -> "A swapchain no longer matches the surface properties exactly, but can still be used to present to the surface successfully.";

                // Error codes
                case VK11.VK_ERROR_OUT_OF_POOL_MEMORY -> "Allocation failed due to no more space in the descriptor pool. (not because of system or device memory exhaustion)";
                case VK_ERROR_OUT_OF_HOST_MEMORY -> "A host memory allocation has failed.";
                case VK_ERROR_OUT_OF_DEVICE_MEMORY -> "A device memory allocation has failed.";
                case VK_ERROR_INITIALIZATION_FAILED -> "Initialization of an object could not be completed for implementation-specific reasons.";
                case VK_ERROR_DEVICE_LOST -> "The logical or physical device has been lost.";
                case VK_ERROR_MEMORY_MAP_FAILED -> "Mapping of a memory object has failed.";
                case VK_ERROR_LAYER_NOT_PRESENT -> "A requested layer is not present or could not be loaded.";
                case VK_ERROR_EXTENSION_NOT_PRESENT -> "A requested extension is not supported.";
                case VK_ERROR_FEATURE_NOT_PRESENT -> "A requested feature is not supported.";
                case VK_ERROR_INCOMPATIBLE_DRIVER -> "The requested version of Vulkan is not supported by the driver or is otherwise incompatible for implementation-specific reasons.";
                case VK_ERROR_TOO_MANY_OBJECTS -> "Too many objects of the type have already been created.";
                case VK_ERROR_FORMAT_NOT_SUPPORTED -> "A requested format is not supported on this device.";
                case KHRSurface.VK_ERROR_SURFACE_LOST_KHR -> "A surface is no longer available.";
                case KHRSurface.VK_ERROR_NATIVE_WINDOW_IN_USE_KHR -> "The requested window is already connected to a VkSurfaceKHR, or to some other non-Vulkan API.";
                case KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR -> "A surface has changed in such a way that it is no longer compatible with the swapchain, and further presentation requests using the "
                                + "swapchain will fail. Applications must query the new surface properties and recreate their swapchain if they wish to continue"
                                + "presenting to the surface.";
                case KHRDisplaySwapchain.VK_ERROR_INCOMPATIBLE_DISPLAY_KHR -> "The display used by a swapchain does not use the same presentable image layout, or is incompatible in a way that prevents sharing an image.";
                case EXTDebugReport.VK_ERROR_VALIDATION_FAILED_EXT -> "A validation layer found an error.";
                default -> String.format("%s [%d]", "Unknown", result);
            });
    }
}
