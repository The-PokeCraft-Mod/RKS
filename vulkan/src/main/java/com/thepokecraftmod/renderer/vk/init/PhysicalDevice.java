package com.thepokecraftmod.renderer.vk.init;

import com.thepokecraftmod.renderer.vk.VkUtils;
import com.thepokecraftmod.renderer.vk.VkWrapper;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.lwjgl.vulkan.VK11.*;

public class PhysicalDevice implements VkWrapper<VkPhysicalDevice> {
    private static final Logger LOGGER = LoggerFactory.getLogger(PhysicalDevice.class);
    private final VkExtensionProperties.Buffer vkDeviceExtensions;
    private final VkPhysicalDeviceMemoryProperties vkMemoryProperties;
    private final VkPhysicalDevice vkPhysicalDevice;
    private final VkPhysicalDeviceFeatures vkPhysicalDeviceFeatures;
    private final VkPhysicalDeviceProperties vkPhysicalDeviceProperties;
    private final VkQueueFamilyProperties.Buffer vkQueueFamilyProps;
    private final Set<Integer> suportedSampleCount;

    private PhysicalDevice(VkPhysicalDevice vkPhysicalDevice) {
        try (var stack = MemoryStack.stackPush()) {
            this.vkPhysicalDevice = vkPhysicalDevice;

            var intBuffer = stack.mallocInt(1);

            // Get device properties
            this.vkPhysicalDeviceProperties = VkPhysicalDeviceProperties.calloc();
            vkGetPhysicalDeviceProperties(vkPhysicalDevice, this.vkPhysicalDeviceProperties);

            // Get device extensions
            VkUtils.ok(vkEnumerateDeviceExtensionProperties(vkPhysicalDevice, (String) null, intBuffer, null),
                    "Failed to get number of device extension properties");
            this.vkDeviceExtensions = VkExtensionProperties.calloc(intBuffer.get(0));
            VkUtils.ok(vkEnumerateDeviceExtensionProperties(vkPhysicalDevice, (String) null, intBuffer, this.vkDeviceExtensions),
                    "Failed to get extension properties");

            this.suportedSampleCount = calSupportedSampleCount(this.vkPhysicalDeviceProperties);

            // Get Queue family properties
            vkGetPhysicalDeviceQueueFamilyProperties(vkPhysicalDevice, intBuffer, null);
            this.vkQueueFamilyProps = VkQueueFamilyProperties.calloc(intBuffer.get(0));
            vkGetPhysicalDeviceQueueFamilyProperties(vkPhysicalDevice, intBuffer, this.vkQueueFamilyProps);

            this.vkPhysicalDeviceFeatures = VkPhysicalDeviceFeatures.calloc();
            vkGetPhysicalDeviceFeatures(vkPhysicalDevice, this.vkPhysicalDeviceFeatures);

            // Get Memory information and properties
            this.vkMemoryProperties = VkPhysicalDeviceMemoryProperties.calloc();
            vkGetPhysicalDeviceMemoryProperties(vkPhysicalDevice, this.vkMemoryProperties);
        }
    }

    public static PhysicalDevice createPhysicalDevice(Instance instance, String prefferredDeviceName) {
        LOGGER.info("Selecting physical devices");
        PhysicalDevice selectedPhysicalDevice = null;
        try (var stack = MemoryStack.stackPush()) {
            // Get available devices
            var pPhysicalDevices = getPhysicalDevices(instance, stack);
            var numDevices = pPhysicalDevices.capacity();
            if (numDevices <= 0) throw new RuntimeException("No physical devices found");

            // Populate available devices
            List<PhysicalDevice> devices = new ArrayList<>();
            for (var i = 0; i < numDevices; i++) {
                var vkPhysicalDevice = new VkPhysicalDevice(pPhysicalDevices.get(i), instance.vk());
                var physicalDevice = new PhysicalDevice(vkPhysicalDevice);

                var deviceName = physicalDevice.getDeviceName();
                if (physicalDevice.hasGraphicsQueueFamily() && physicalDevice.hasKHRSwapChainExtension()) {
                    LOGGER.info("Device [{}] supports required extensions", deviceName);
                    if (prefferredDeviceName != null && prefferredDeviceName.equals(deviceName)) {
                        selectedPhysicalDevice = physicalDevice;
                        break;
                    }
                    devices.add(physicalDevice);
                } else {
                    LOGGER.info("Device [{}] does not support required extensions", deviceName);
                    physicalDevice.close();
                }
            }

            // No preferred device, or it does not meet requirements, just pick the first one
            selectedPhysicalDevice = selectedPhysicalDevice == null && !devices.isEmpty() ? devices.remove(0) : selectedPhysicalDevice;

            // Clean up non-selected devices
            for (var physicalDevice : devices) physicalDevice.close();

            if (selectedPhysicalDevice == null) throw new RuntimeException("No suitable physical devices found");
            LOGGER.info("Selected device: [{}]", selectedPhysicalDevice.getDeviceName());
        }

        return selectedPhysicalDevice;
    }

    protected static PointerBuffer getPhysicalDevices(Instance instance, MemoryStack stack) {
        PointerBuffer pPhysicalDevices;
        // Get number of physical devices
        var intBuffer = stack.mallocInt(1);
        VkUtils.ok(vkEnumeratePhysicalDevices(instance.vk(), intBuffer, null), "Failed to get number of physical devices");
        var numDevices = intBuffer.get(0);
        LOGGER.info("Detected {} physical device(s)", numDevices);

        // Populate physical devices list pointer
        pPhysicalDevices = stack.mallocPointer(numDevices);
        VkUtils.ok(vkEnumeratePhysicalDevices(instance.vk(), intBuffer, pPhysicalDevices),
                "Failed to get physical devices");
        return pPhysicalDevices;
    }

    private Set<Integer> calSupportedSampleCount(VkPhysicalDeviceProperties devProps) {
        Set<Integer> result = new HashSet<>();
        var colorCounts = Integer.toUnsignedLong(this.vkPhysicalDeviceProperties.limits().framebufferColorSampleCounts());
        LOGGER.info("Color max samples: {}", colorCounts);
        var depthCounts = Integer.toUnsignedLong(devProps.limits().framebufferDepthSampleCounts());
        LOGGER.info("Depth max samples: {}", depthCounts);
        var counts = (int) (Math.min(colorCounts, depthCounts));
        LOGGER.info("Max samples: {}", depthCounts);

        result.add(VK_SAMPLE_COUNT_1_BIT);
        if ((counts & VK_SAMPLE_COUNT_64_BIT) > 0) result.add(VK_SAMPLE_COUNT_64_BIT);
        if ((counts & VK_SAMPLE_COUNT_32_BIT) > 0) result.add(VK_SAMPLE_COUNT_32_BIT);
        if ((counts & VK_SAMPLE_COUNT_16_BIT) > 0) result.add(VK_SAMPLE_COUNT_16_BIT);
        if ((counts & VK_SAMPLE_COUNT_8_BIT) > 0) result.add(VK_SAMPLE_COUNT_8_BIT);
        if ((counts & VK_SAMPLE_COUNT_4_BIT) > 0) result.add(VK_SAMPLE_COUNT_4_BIT);
        if ((counts & VK_SAMPLE_COUNT_2_BIT) != 0) result.add(VK_SAMPLE_COUNT_2_BIT);

        return result;
    }
    
    @Override
    public void close() {
        LOGGER.info("Closing {}", this.vkPhysicalDeviceProperties.deviceNameString());
        this.vkMemoryProperties.free();
        this.vkPhysicalDeviceFeatures.free();
        this.vkQueueFamilyProps.free();
        this.vkDeviceExtensions.free();
        this.vkPhysicalDeviceProperties.free();
    }

    public String getDeviceName() {
        return this.vkPhysicalDeviceProperties.deviceNameString();
    }

    public VkPhysicalDeviceMemoryProperties getVkMemoryProperties() {
        return this.vkMemoryProperties;
    }
    
    @Override
    public VkPhysicalDevice vk() {
        return this.vkPhysicalDevice;
    }

    public VkPhysicalDeviceFeatures getVkPhysicalDeviceFeatures() {
        return this.vkPhysicalDeviceFeatures;
    }

    public VkPhysicalDeviceProperties getVkPhysicalDeviceProperties() {
        return this.vkPhysicalDeviceProperties;
    }

    public VkQueueFamilyProperties.Buffer getVkQueueFamilyProps() {
        return this.vkQueueFamilyProps;
    }

    private boolean hasGraphicsQueueFamily() {
        var result = false;
        var numQueueFamilies = this.vkQueueFamilyProps != null ? this.vkQueueFamilyProps.capacity() : 0;
        for (var i = 0; i < numQueueFamilies; i++) {
            var familyProps = this.vkQueueFamilyProps.get(i);
            if ((familyProps.queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0) {
                result = true;
                break;
            }
        }
        return result;
    }

    private boolean hasKHRSwapChainExtension() {
        var result = false;
        var numExtensions = this.vkDeviceExtensions != null ? this.vkDeviceExtensions.capacity() : 0;
        for (var i = 0; i < numExtensions; i++) {
            var extensionName = this.vkDeviceExtensions.get(i).extensionNameString();
            if (KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME.equals(extensionName)) {
                result = true;
                break;
            }
        }
        return result;
    }

    public boolean supportsSampleCount(int numSamples) {
        return this.suportedSampleCount.contains(numSamples);
    }
}