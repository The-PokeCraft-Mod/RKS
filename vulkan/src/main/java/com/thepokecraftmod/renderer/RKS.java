package com.thepokecraftmod.renderer;

import com.thepokecraftmod.renderer.impl.Render;
import com.thepokecraftmod.renderer.scene.Scene;

import java.io.Closeable;

public class RKS implements Closeable {

    public final Render renderer;
    public final Scene scene;
    public final Window window;

    public RKS(Window window) {
        this.window = window;
        this.scene = new Scene(this.window);
        this.renderer = new Render(this.window, this.scene);
    }

    @Override
    public void close() {
        this.renderer.close();
        this.window.close();
    }
}
