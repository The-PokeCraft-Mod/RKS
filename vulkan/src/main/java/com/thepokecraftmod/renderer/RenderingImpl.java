package com.thepokecraftmod.renderer;

import org.jetbrains.annotations.ApiStatus;
import com.thepokecraftmod.renderer.impl.Render;
import com.thepokecraftmod.renderer.scene.Scene;

import java.io.Closeable;

// TODO: remove
@ApiStatus.ScheduledForRemoval
public interface RenderingImpl extends Closeable {

    @Override
    void close();

    void handleInput(Window window, Scene scene, long diffTimeMillis, boolean inputConsumed);

    void init(Scene scene, Render render);
}
