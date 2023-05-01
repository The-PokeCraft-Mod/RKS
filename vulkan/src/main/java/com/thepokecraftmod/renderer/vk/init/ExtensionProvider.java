package com.thepokecraftmod.renderer.vk.init;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.util.vma.Vma.VMA_ALLOCATOR_CREATE_BUFFER_DEVICE_ADDRESS_BIT;

public class ExtensionProvider {

    public final List<String> instanceExtensions = new ArrayList<>();
    public final List<String> deviceExtensions = new ArrayList<>();
    public final List<String> enabledFeatures = new ArrayList<>();
    public int vmaFlags;

    public ExtensionProvider instanceExtension(String extensionName) {
        this.instanceExtensions.add(extensionName);
        return this;
    }

    public ExtensionProvider deviceExtension(String extensionName) {
        deviceExtensions.add(extensionName);
        return this;
    }

    public ExtensionProvider enableFeature(String feature) {
        enabledFeatures.add(feature);
        return this;
    }

    public ExtensionProvider vmaFlags(int flags) {
        this.vmaFlags = flags;
        return this;
    }
}
