package org.vulkanb.eng.scene;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class Entity {

    private final String id;
    private final String modelId;
    private final Matrix4f modelMatrix;
    private final Vector3f position;
    private final Quaternionf rotation;
    private EntityAnimation entityAnimation;
    private float scale;

    public Entity(String id, String modelId, Vector3f position) {
        this.id = id;
        this.modelId = modelId;
        this.position = position;
        this.scale = 1;
        this.rotation = new Quaternionf();
        this.modelMatrix = new Matrix4f();
        updateModelMatrix();
    }

    public EntityAnimation getEntityAnimation() {
        return this.entityAnimation;
    }

    public void setEntityAnimation(EntityAnimation entityAnimation) {
        this.entityAnimation = entityAnimation;
    }

    public String getId() {
        return this.id;
    }

    public String getModelId() {
        return this.modelId;
    }

    public Matrix4f getModelMatrix() {
        return this.modelMatrix;
    }

    public Vector3f getPosition() {
        return this.position;
    }

    public Quaternionf getRotation() {
        return this.rotation;
    }

    public float getScale() {
        return this.scale;
    }

    public void setScale(float scale) {
        this.scale = scale;
        updateModelMatrix();
    }

    public boolean hasAnimation() {
        return this.entityAnimation != null;
    }

    public final void setPosition(float x, float y, float z) {
        this.position.x = x;
        this.position.y = y;
        this.position.z = z;
        updateModelMatrix();
    }

    public void updateModelMatrix() {
        this.modelMatrix.translationRotateScale(this.position, this.rotation, this.scale);
    }

    public static class EntityAnimation {
        private int animationIdx;
        private int currentFrame;
        private boolean started;

        public EntityAnimation(boolean started, int animationIdx, int currentFrame) {
            this.started = started;
            this.animationIdx = animationIdx;
            this.currentFrame = currentFrame;
        }

        public int getAnimationIdx() {
            return this.animationIdx;
        }

        public void setAnimationIdx(int animationIdx) {
            this.animationIdx = animationIdx;
        }

        public int getCurrentFrame() {
            return this.currentFrame;
        }

        public void setCurrentFrame(int currentFrame) {
            this.currentFrame = currentFrame;
        }

        public boolean isStarted() {
            return this.started;
        }

        public void setStarted(boolean started) {
            this.started = started;
        }
    }
}