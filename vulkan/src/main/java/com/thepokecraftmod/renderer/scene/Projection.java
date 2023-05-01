package com.thepokecraftmod.renderer.scene;

import org.joml.Matrix4f;
import com.thepokecraftmod.renderer.Settings;

public class Projection {

    public final Matrix4f projectionMatrix;

    public Projection() {
        this.projectionMatrix = new Matrix4f();
    }

    public Matrix4f getProjectionMatrix() {
        return this.projectionMatrix;
    }

    public void resize(int width, int height) {
        // var engProps = Settings.getInstance();
        // this.projectionMatrix.identity();
        // this.projectionMatrix.perspective(engProps.getFov(), (float) width / (float) height, engProps.getZNear(), engProps.getZFar(), true);
    }
}
