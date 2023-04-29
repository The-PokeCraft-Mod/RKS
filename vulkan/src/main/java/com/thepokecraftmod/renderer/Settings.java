package com.thepokecraftmod.renderer;

import org.tinylog.Logger;

import java.io.IOException;
import java.util.Properties;

public class Settings {
    private static final float DEFAULT_FOV = 60.0f;
    private static final int DEFAULT_JOINT_MATRICES_BUF = 2000000;
    private static final int DEFAULT_MAX_ANIM_WEIGHTS_BUF = 100000;
    private static final int DEFAULT_MAX_INDICES_BUF = 5000000;
    private static final int DEFAULT_MAX_MATERIALS = 500;
    private static final int DEFAULT_MAX_VERTICES_BUF = 20000000;
    private static final int DEFAULT_REQUESTED_IMAGES = 3;
    private static final float DEFAULT_SHADOW_BIAS = 0.00005f;
    private static final int DEFAULT_SHADOW_MAP_SIZE = 2048;
    private static final int DEFAULT_UPS = 30;
    private static final float DEFAULT_Z_FAR = 100.f;
    private static final float DEFAULT_Z_NEAR = 1.0f;
    private static final String FILENAME = "eng.properties";
    private static Settings instance;
    private String defaultTexturePath;
    private float fov;
    private int maxAnimWeightsBuffer;
    private int maxIndicesBuffer;
    private int maxJointMatricesBuffer;
    private int maxMaterials;
    private int maxTextures;
    private int maxVerticesBuffer;
    private String physDeviceName;
    private int requestedImages;
    private boolean shaderRecompilation;
    private float shadowBias;
    private boolean shadowDebug;
    private int shadowMapSize;
    private boolean shadowPcf;
    private int ups;
    private boolean vSync;
    private boolean validate;
    private float zFar;
    private float zNear;

    private Settings() {
        // Singleton
        var props = new Properties();

        try (var stream = Settings.class.getResourceAsStream("/" + FILENAME)) {
            props.load(stream);
            this.ups = Integer.parseInt(props.getOrDefault("ups", DEFAULT_UPS).toString());
            this.validate = Boolean.parseBoolean(props.getOrDefault("vkValidate", false).toString());
            this.physDeviceName = props.getProperty("physDeviceName");
            this.requestedImages = Integer.parseInt(props.getOrDefault("requestedImages", DEFAULT_REQUESTED_IMAGES).toString());
            this.vSync = Boolean.parseBoolean(props.getOrDefault("vsync", true).toString());
            this.shaderRecompilation = Boolean.parseBoolean(props.getOrDefault("shaderRecompilation", false).toString());
            this.fov = (float) Math.toRadians(Float.parseFloat(props.getOrDefault("fov", DEFAULT_FOV).toString()));
            this.zNear = Float.parseFloat(props.getOrDefault("zNear", DEFAULT_Z_NEAR).toString());
            this.zFar = Float.parseFloat(props.getOrDefault("zFar", DEFAULT_Z_FAR).toString());
            this.defaultTexturePath = props.getProperty("defaultTexturePath");
            this.maxMaterials = Integer.parseInt(props.getOrDefault("maxMaterials", DEFAULT_MAX_MATERIALS).toString());
            this.shadowPcf = Boolean.parseBoolean(props.getOrDefault("shadowPcf", false).toString());
            this.shadowBias = Float.parseFloat(props.getOrDefault("shadowBias", DEFAULT_SHADOW_BIAS).toString());
            this.shadowMapSize = Integer.parseInt(props.getOrDefault("shadowMapSize", DEFAULT_SHADOW_MAP_SIZE).toString());
            this.shadowDebug = Boolean.parseBoolean(props.getOrDefault("shadowDebug", false).toString());
            this.maxTextures = this.maxMaterials * 3;
            this.maxVerticesBuffer = Integer.parseInt(props.getOrDefault("maxVerticesBuffer", DEFAULT_MAX_VERTICES_BUF).toString());
            this.maxIndicesBuffer = Integer.parseInt(props.getOrDefault("maxIndicesBuffer", DEFAULT_MAX_INDICES_BUF).toString());
            this.maxAnimWeightsBuffer = Integer.parseInt(props.getOrDefault("maxAnimWeightsBuffer", DEFAULT_MAX_ANIM_WEIGHTS_BUF).toString());
            this.maxJointMatricesBuffer = Integer.parseInt(props.getOrDefault("maxJointMatricesBuffer", DEFAULT_JOINT_MATRICES_BUF).toString());
        } catch (IOException e) {
            Logger.error("Could not read [{}] properties file", FILENAME, e);
        }
    }

    public static synchronized Settings getInstance() {
        if (instance == null) instance = new Settings();
        return instance;
    }

    public String getDefaultTexturePath() {
        return this.defaultTexturePath;
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

    public String getPhysDeviceName() {
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

    public int getUps() {
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
