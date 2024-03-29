package com.thepokecraftmod.renderer.scene;

import org.joml.Vector4f;
import com.thepokecraftmod.renderer.Window;
import com.thepokecraftmod.renderer.wrapper.core.VkConstants;

import java.util.*;

public class Scene {

    private final Vector4f ambientLight;
    private final Camera camera;
    private final Map<String, List<RksEntity>> entitiesMap;
    public final Projection projection;
    private Light directionalLight;
    private long entitiesLoadedTimeStamp;
    private boolean lightChanged;
    private Light[] lights;

    public Scene(Window window) {
        this.entitiesMap = new HashMap<>();
        this.projection = new Projection();
        this.projection.resize(window.getWidth(), window.getHeight());
        this.camera = new Camera();
        this.ambientLight = new Vector4f();
    }

    public void addEntity(RksEntity entity) {
        var entities = this.entitiesMap.computeIfAbsent(entity.getModelId(), k -> new ArrayList<>());
        entities.add(entity);
        this.entitiesLoadedTimeStamp = System.currentTimeMillis();
    }

    public Vector4f getAmbientLight() {
        return this.ambientLight;
    }

    public Camera getCamera() {
        return this.camera;
    }

    public Light getDirectionalLight() {
        return this.directionalLight;
    }

    public List<RksEntity> getEntitiesByModelId(String modelId) {
        return this.entitiesMap.get(modelId);
    }

    public long getEntitiesLoadedTimeStamp() {
        return this.entitiesLoadedTimeStamp;
    }

    public Map<String, List<RksEntity>> getEntitiesMap() {
        return this.entitiesMap;
    }

    public Light[] getLights() {
        return this.lights;
    }

    public void setLights(Light[] lights) {
        this.directionalLight = null;
        var numLights = lights != null ? lights.length : 0;
        if (numLights > VkConstants.MAX_LIGHTS)
            throw new RuntimeException("Maximum number of lights set to: " + VkConstants.MAX_LIGHTS);
        this.lights = lights;
        var option = Arrays.stream(lights).filter(l -> l.getPosition().w == 0).findFirst();
        option.ifPresent(light -> this.directionalLight = light);

        this.lightChanged = true;
    }

    public Projection getProjection() {
        return this.projection;
    }

    public boolean isLightChanged() {
        return this.lightChanged;
    }

    public void setLightChanged(boolean lightChanged) {
        this.lightChanged = lightChanged;
    }

    public void removeAllEntities() {
        this.entitiesMap.clear();
        this.entitiesLoadedTimeStamp = System.currentTimeMillis();
    }

    public void removeEntity(RksEntity entity) {
        var entities = this.entitiesMap.get(entity.getModelId());
        if (entities != null) entities.removeIf(e -> e.getId().equals(entity.getId()));
        this.entitiesLoadedTimeStamp = System.currentTimeMillis();
    }
}
