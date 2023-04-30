package com.thepokecraftmod.renderer;

import com.thepokecraftmod.renderer.impl.Renderer;
import com.thepokecraftmod.renderer.scene.Scene;

import java.io.Closeable;

public class Rks implements Closeable {

    public final Renderer renderer;
    public final Scene scene;
    public final Window window;

    public Rks(Window window) {
        this.window = window;
        this.scene = new Scene(this.window);
        this.renderer = new Renderer(this.window, this.scene);
    }

    @Override
    public void close() {
        this.renderer.close();
        this.window.close();
    }
}
