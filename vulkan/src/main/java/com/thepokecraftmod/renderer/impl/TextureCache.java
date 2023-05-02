package com.thepokecraftmod.renderer.impl;

import com.thepokecraftmod.renderer.wrapper.init.Device;
import com.thepokecraftmod.renderer.wrapper.image.Texture;

import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;

public class TextureCache implements Closeable {

    private final IndexedLinkedHashMap<String, Texture> textureMap;

    public TextureCache() {
        this.textureMap = new IndexedLinkedHashMap<>();
    }

    @Override
    public void close() {
        this.textureMap.forEach((k, v) -> v.close());
        this.textureMap.clear();
    }

    public Texture createTexture(Device device, String id, BufferedImage cpuTexture, boolean transparent, int format) {
        var texture = this.textureMap.get(id);
        if (texture == null) {
            texture = new Texture(device, id, cpuTexture, transparent, format);
            this.textureMap.put(id, texture);
        }
        return texture;
    }

    public List<Texture> getAll() {
        return new ArrayList<>(this.textureMap.values());
    }

    public int getPosition(String texturePath) {
        var result = -1;
        if (texturePath != null) result = this.textureMap.getIndexOf(texturePath);
        return result;
    }

    public Texture getTexture(String texturePath) {
        return this.textureMap.get(texturePath);
    }
}