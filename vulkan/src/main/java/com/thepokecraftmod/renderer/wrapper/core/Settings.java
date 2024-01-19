package com.thepokecraftmod.renderer.wrapper.core;

public class Settings {
    private static Settings instance;
    private final float fov = 90;
    private final int maxAnimWeightsBuffer = 1000000;
    private final int maxIndicesBuffer = 5000000;
    private final int maxJointMatricesBuffer = 20000000;
    private final int maxMaterials = 500;
    private final int maxTextures = maxMaterials * 3;
    private final int maxVerticesBuffer = 20000000;
    private final String physDeviceName = "NVIDIA GeForce RTX 2070 SUPER";
    private final int requestedImages = 3;
    private final boolean shaderRecompilation = true;
    private final float shadowBias = 0.001f;
    private final boolean shadowDebug = false;
    private final int shadowMapSize = 2048;
    private final boolean shadowPcf = true;
    private final int ups = 60;
    private final boolean vSync = false;
    private final boolean validate = true;
    private final float zFar = 0.1f;
    private final float zNear = 1000f;

    public static synchronized Settings getInstance() {
        if (instance == null) instance = new Settings();
        return instance;
    }

    public float getFov() {
        return this.fov;
    }

    public int getMaxAnimWeightsBuffer() {
        return this.maxAnimWeightsBuffer;
    }

    public int getMaxIndicesBuffer() {
        return this.maxIndicesBuffer;
    }

    public int getMaxJointMatricesBuffer() {
        return this.maxJointMatricesBuffer;
    }

    public int getMaxMaterials() {
        return this.maxMaterials;
    }

    public int getMaxTextures() {
        return this.maxTextures;
    }

    public int getMaxVerticesBuffer() {
        return this.maxVerticesBuffer;
    }

    public String preferredDevice() {
        return this.physDeviceName;
    }

    public int getRequestedImages() {
        return this.requestedImages;
    }

    public float getShadowBias() {
        return this.shadowBias;
    }

    public int getShadowMapSize() {
        return this.shadowMapSize;
    }

    public int getUpdatesPerSecond() {
        return this.ups;
    }

    public float getZFar() {
        return this.zFar;
    }

    public float getZNear() {
        return this.zNear;
    }

    public boolean isShaderRecompilation() {
        return this.shaderRecompilation;
    }

    public boolean isShadowDebug() {
        return this.shadowDebug;
    }

    public boolean isShadowPcf() {
        return this.shadowPcf;
    }

    public boolean isValidate() {
        return this.validate;
    }

    public boolean isvSync() {
        return this.vSync;
    }
}
