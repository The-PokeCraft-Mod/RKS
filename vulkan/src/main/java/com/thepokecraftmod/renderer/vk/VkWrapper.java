package com.thepokecraftmod.renderer.vk;

import java.io.Closeable;

public interface VkWrapper<T> extends Closeable {

    @Override
    void close();

    T vk();
}
