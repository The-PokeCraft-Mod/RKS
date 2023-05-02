package com.thepokecraftmod.renderer.wrapper.init;

import java.util.ArrayList;
import java.util.List;

public class ExtensionProvider {

    public final List<String> instanceExtensions = new ArrayList<>();
    public final List<String> deviceExtensions = new ArrayList<>();
    public final List<String> enabledFeatures = new ArrayList<>();
    public boolean enableSharedMemAlloc;

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

    //FIXME: tape solution
    public ExtensionProvider enableSharedAllocator(boolean sharedAllocator) {
        this.enableSharedMemAlloc = sharedAllocator;
        return this;
    }
}
