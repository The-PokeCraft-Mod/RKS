package com.thepokecraftmod.renderer;

import java.io.Closeable;

public interface Window extends Closeable {

    @Override
    void close();

    boolean shouldClose();

    void pollEvents();

    boolean isKeyPressed(int glfwKeyW);

    int getWidth();

    int getHeight();

    long handle();

    void setResized(boolean resized);

    boolean isResized();

    void resetResized();
}
