package org.vulkanb.eng.scene;

import org.joml.Matrix4f;
import org.vulkanb.eng.Settings;

public class Projection {

    private final Matrix4f projectionMatrix;

    public Projection() {
        this.projectionMatrix = new Matrix4f();
    }

    public Matrix4f getProjectionMatrix() {
        return this.projectionMatrix;
    }

    public void resize(int width, int height) {
        var engProps = Settings.getInstance();
        this.projectionMatrix.identity();
        this.projectionMatrix.perspective(engProps.getFov(), (float) width / (float) height,
                engProps.getZNear(), engProps.getZFar(), true);
    }
}
