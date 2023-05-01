package com.thepokecraftmod.renderer.vk.init;

import java.util.ArrayList;
import java.util.List;

public class ExtensionProvider {

    public final List<String> instanceExtensions = new ArrayList<>();
    public final List<String> deviceExtensions = new ArrayList<>();

    public ExtensionProvider instanceExtension(String extensionName) {
        this.instanceExtensions.add(extensionName);
        return this;
    }

    public ExtensionProvider deviceExtension(String extensionName) {
        deviceExtensions.add(extensionName);
        return this;
    }
}
