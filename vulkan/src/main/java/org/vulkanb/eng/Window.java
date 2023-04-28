package org.vulkanb.eng;

import java.io.Closeable;

public interface Window extends Closeable {

    @Override
    void close();

    MouseInput getMouseInput();

    boolean shouldClose();

    void pollEvents();

    boolean isKeyPressed(int glfwKeyW);

    int getWidth();

    int getHeight();

    long getWindowHandle();

    void setResized(boolean resized);

    boolean isResized();

    void resetResized();
}
