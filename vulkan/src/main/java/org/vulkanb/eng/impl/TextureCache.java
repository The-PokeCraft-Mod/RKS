package org.vulkanb.eng.impl;

import org.vulkanb.eng.vk.Device;
import org.vulkanb.eng.vk.Texture;

import java.util.ArrayList;
import java.util.List;

public class TextureCache {

    private final IndexedLinkedHashMap<String, Texture> textureMap;

    public TextureCache() {
        this.textureMap = new IndexedLinkedHashMap<>();
    }

    public void close() {
        this.textureMap.forEach((k, v) -> v.close());
        this.textureMap.clear();
    }

    public Texture createTexture(Device device, String texturePath, int format) {
        if (texturePath == null || texturePath.trim().isEmpty()) return null;
        var texture = this.textureMap.get(texturePath);
        if (texture == null) {
            texture = new Texture(device, texturePath, format);
            this.textureMap.put(texturePath, texture);
        }
        return texture;
    }

    public List<Texture> getAsList() {
        return new ArrayList<>(this.textureMap.values());
    }

    public int getPosition(String texturePath) {
        var result = -1;
        if (texturePath != null) result = this.textureMap.getIndexOf(texturePath);
        return result;
    }

    public Texture getTexture(String texturePath) {
        return this.textureMap.get(texturePath.trim());
    }
}