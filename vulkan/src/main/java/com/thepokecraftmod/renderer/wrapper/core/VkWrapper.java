package com.thepokecraftmod.renderer.wrapper.core;

import java.io.Closeable;

public interface VkWrapper<T> extends Closeable {

    @Override
    void close();

    T vk();
}
