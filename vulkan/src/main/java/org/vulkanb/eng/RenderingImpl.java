package org.vulkanb.eng;

import org.jetbrains.annotations.ApiStatus;
import org.vulkanb.eng.impl.Render;
import org.vulkanb.eng.scene.Scene;

import java.io.Closeable;

// TODO: remove
@ApiStatus.ScheduledForRemoval
public interface RenderingImpl extends Closeable {

    @Override
    void close();

    void handleInput(Window window, Scene scene, long diffTimeMillis, boolean inputConsumed);

    void init(Scene scene, Render render);
}
