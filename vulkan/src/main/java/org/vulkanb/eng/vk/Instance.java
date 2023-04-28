package org.vulkanb.eng.vk;

import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import org.tinylog.Logger;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.VK11.*;

public class Instance {

    public static final int MESSAGE_SEVERITY_BITMASK = VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT |
            VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT;
    public static final int MESSAGE_TYPE_BITMASK = VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT |
            VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT |
            VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT;

    private final VkInstance vkInstance;

    private VkDebugUtilsMessengerCreateInfoEXT debugUtils;
    private long vkDebugHandle;

    public Instance(boolean validate) {
        Logger.debug("Creating Vulkan instance");
        try (var stack = MemoryStack.stackPush()) {
            // Create application information
            var appShortName = stack.UTF8("VulkanBook");
            var appInfo = VkApplicationInfo.calloc(stack)
                    .sType$Default()
                    .pApplicationName(appShortName)
                    .applicationVersion(1)
                    .pEngineName(appShortName)
                    .engineVersion(0)
                    .apiVersion(VK_API_VERSION_1_1);

            // Validation layers
            var validationLayers = getSupportedValidationLayers();
            var numValidationLayers = validationLayers.size();
            var supportsValidation = validate;
            if (validate && numValidationLayers == 0) {
                supportsValidation = false;
                Logger.warn("Request validation but no supported validation layers found. Falling back to no validation");
            }
            Logger.debug("Validation: {}", supportsValidation);

            // Set required  layers
            PointerBuffer requiredLayers = null;
            if (supportsValidation) {
                requiredLayers = stack.mallocPointer(numValidationLayers);
                for (var i = 0; i < numValidationLayers; i++) {
                    Logger.debug("Using validation layer [{}]", validationLayers.get(i));
                    requiredLayers.put(i, stack.ASCII(validationLayers.get(i)));
                }
            }

            // GLFW Extension
            var glfwExtensions = GLFWVulkan.glfwGetRequiredInstanceExtensions();
            if (glfwExtensions == null)
                throw new RuntimeException("Failed to find the GLFW platform surface extensions");

            PointerBuffer requiredExtensions;
            if (supportsValidation) {
                var vkDebugUtilsExtension = stack.UTF8(EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME);
                requiredExtensions = stack.mallocPointer(glfwExtensions.remaining() + 1);
                requiredExtensions.put(glfwExtensions).put(vkDebugUtilsExtension);
            } else {
                requiredExtensions = stack.mallocPointer(glfwExtensions.remaining());
                requiredExtensions.put(glfwExtensions);
            }
            requiredExtensions.flip();

            var extension = MemoryUtil.NULL;
            if (supportsValidation) {
                this.debugUtils = createDebugCallBack();
                extension = this.debugUtils.address();
            }

            // Create instance info
            var instanceInfo = VkInstanceCreateInfo.calloc(stack)
                    .sType$Default()
                    .pNext(extension)
                    .pApplicationInfo(appInfo)
                    .ppEnabledLayerNames(requiredLayers)
                    .ppEnabledExtensionNames(requiredExtensions);

            var pInstance = stack.mallocPointer(1);
            VkUtils.ok(vkCreateInstance(instanceInfo, null, pInstance), "Error creating instance");
            this.vkInstance = new VkInstance(pInstance.get(0), instanceInfo);

            this.vkDebugHandle = VK_NULL_HANDLE;
            if (supportsValidation) {
                var longBuff = stack.mallocLong(1);
                VkUtils.ok(vkCreateDebugUtilsMessengerEXT(this.vkInstance, this.debugUtils, null, longBuff), "Error creating debug utils");
                this.vkDebugHandle = longBuff.get(0);
            }
        }
    }

    private static VkDebugUtilsMessengerCreateInfoEXT createDebugCallBack() {
        return VkDebugUtilsMessengerCreateInfoEXT
                .calloc()
                .sType$Default()
                .messageSeverity(MESSAGE_SEVERITY_BITMASK)
                .messageType(MESSAGE_TYPE_BITMASK)
                .pfnUserCallback((messageSeverity, messageTypes, pCallbackData, pUserData) -> {
                    var callbackData = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData);
                    if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT) != 0)
                        Logger.info("VkDebugUtilsCallback, {}", callbackData.pMessageString());
                    else if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT) != 0)
                        Logger.warn("VkDebugUtilsCallback, {}", callbackData.pMessageString());
                    else if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) != 0)
                        Logger.error("VkDebugUtilsCallback, {}", callbackData.pMessageString());
                    else Logger.debug("VkDebugUtilsCallback, {}", callbackData.pMessageString());
                    return VK_FALSE;
                });
    }

    public void close() {
        Logger.debug("Destroying Vulkan instance");
        if (this.vkDebugHandle != VK_NULL_HANDLE)
            vkDestroyDebugUtilsMessengerEXT(this.vkInstance, this.vkDebugHandle, null);
        if (this.debugUtils != null) {
            this.debugUtils.pfnUserCallback().free();
            this.debugUtils.free();
        }
        vkDestroyInstance(this.vkInstance, null);
    }

    private List<String> getSupportedValidationLayers() {
        try (var stack = MemoryStack.stackPush()) {
            var numLayersArr = stack.callocInt(1);
            vkEnumerateInstanceLayerProperties(numLayersArr, null);
            var numLayers = numLayersArr.get(0);
            Logger.debug("Instance supports [{}] layers", numLayers);

            var propsBuf = VkLayerProperties.calloc(numLayers, stack);
            vkEnumerateInstanceLayerProperties(numLayersArr, propsBuf);
            List<String> supportedLayers = new ArrayList<>();
            for (var i = 0; i < numLayers; i++) {
                var props = propsBuf.get(i);
                var layerName = props.layerNameString();
                supportedLayers.add(layerName);
                Logger.debug("Supported layer [{}]", layerName);
            }

            List<String> layersToUse = new ArrayList<>();

            // Main validation layer
            if (supportedLayers.contains("VK_LAYER_KHRONOS_validation")) {
                layersToUse.add("VK_LAYER_KHRONOS_validation");
                return layersToUse;
            }

            // Fallback 1
            if (supportedLayers.contains("VK_LAYER_LUNARG_standard_validation")) {
                layersToUse.add("VK_LAYER_LUNARG_standard_validation");
                return layersToUse;
            }

            // Fallback 2 (set)
            List<String> requestedLayers = new ArrayList<>();
            requestedLayers.add("VK_LAYER_GOOGLE_threading");
            requestedLayers.add("VK_LAYER_LUNARG_parameter_validation");
            requestedLayers.add("VK_LAYER_LUNARG_object_tracker");
            requestedLayers.add("VK_LAYER_LUNARG_core_validation");
            requestedLayers.add("VK_LAYER_GOOGLE_unique_objects");

            return requestedLayers.stream().filter(supportedLayers::contains).toList();
        }
    }

    public VkInstance getVkInstance() {
        return this.vkInstance;
    }
}