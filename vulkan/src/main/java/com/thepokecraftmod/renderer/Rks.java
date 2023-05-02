package com.thepokecraftmod.renderer;

import com.thepokecraftmod.renderer.impl.Renderer;
import com.thepokecraftmod.renderer.scene.Scene;
import com.thepokecraftmod.renderer.wrapper.init.ExtensionProvider;

import java.io.Closeable;

public class Rks implements Closeable {

    public final Renderer renderer;
    public final Scene scene;
    public final Window window;

    public Rks(Window window, ExtensionProvider instanceFactory) {
        this.window = window;
        this.scene = new Scene(window);
        this.renderer = new Renderer(window, instanceFactory, scene);
    }

    @Override
    public void close() {
        renderer.close();
        window.close();
    }
}
