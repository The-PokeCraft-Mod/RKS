package com.thepokecraftmod.vulkan.tests;

import com.thepokecraftmod.rks.model.animation.TransformStorage;
import com.thepokecraftmod.vulkan.util.DebugWindow;
import org.joml.Vector3f;
import org.vulkanb.eng.RKS;
import org.vulkanb.eng.RenderingImpl;
import org.vulkanb.eng.Window;
import org.vulkanb.eng.impl.Render;
import org.vulkanb.eng.impl.gui.GuiPassRenderer;
import org.vulkanb.eng.scene.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;

public class Standalone implements RenderingImpl {
    private static final float MOUSE_SENSITIVITY = 0.2f;
    private static final float MOVEMENT_SPEED = 10.0f / 1000000000f;
    private final Map<Entity, Integer> maxFrameMap = new HashMap<>();
    private static RKS RKS;
    private float angleInc;
    private Entity rayquaza;
    private Entity jit;
    private Light directionalLight;
    private float lightAngle = 90.1f;
    private boolean loadedJitModel = false;
    private Render renderer;
    private Scene scene;
    private final List<ModelData> models = new ArrayList<>();

    public static void main(String[] args) {
        System.loadLibrary("renderdoc");
        Standalone.RKS = new RKS(new Standalone(), new DebugWindow("RKS Standalone Test", new GuiPassRenderer.KeyCallback(), new GuiPassRenderer.CharCallBack()));
        RKS.start();
    }

    @Override
    public void close() {
    }

    @Override
    public void handleInput(Window window, Scene scene, long diffTimeMillis, boolean inputConsumed) {
        if (inputConsumed) return;
        var move = diffTimeMillis * MOVEMENT_SPEED;
        var camera = scene.getCamera();
        if (window.isKeyPressed(GLFW_KEY_W)) camera.moveForward(move);
        else if (window.isKeyPressed(GLFW_KEY_S)) camera.moveBackwards(move);
        if (window.isKeyPressed(GLFW_KEY_A)) camera.moveLeft(move);
        else if (window.isKeyPressed(GLFW_KEY_D)) camera.moveRight(move);
        if (window.isKeyPressed(GLFW_KEY_UP)) camera.moveUp(move);
        else if (window.isKeyPressed(GLFW_KEY_DOWN)) camera.moveDown(move);
        if (window.isKeyPressed(GLFW_KEY_LEFT)) {
            this.angleInc -= 0.05f;
            scene.setLightChanged(true);
        } else if (window.isKeyPressed(GLFW_KEY_RIGHT)) {
            this.angleInc += 0.05f;
            scene.setLightChanged(true);
        } else {
            this.angleInc = 0;
            scene.setLightChanged(false);
        }

        if (window.isKeyPressed(GLFW_KEY_O)) if (!loadedJitModel) loadJitModel();

        if (window.isKeyPressed(GLFW_KEY_SPACE))
            this.rayquaza.getEntityAnimation().setStarted(!this.rayquaza.getEntityAnimation().isStarted());

        var mouseInput = window.getMouseInput();
        if (mouseInput.isRightButtonPressed()) {
            var displVec = mouseInput.getDisplVec();
            camera.addRotation((float) Math.toRadians(-displVec.x * MOUSE_SENSITIVITY), (float) Math.toRadians(-displVec.y * MOUSE_SENSITIVITY));
        }

        this.lightAngle += this.angleInc;
        if (this.lightAngle < 0) this.lightAngle = 0;
        else if (this.lightAngle > 180) this.lightAngle = 180;
        updateDirectionalLight();

        var entityAnimation = this.rayquaza.getEntityAnimation();
        if (entityAnimation != null && entityAnimation.isStarted()) {
            var currentFrame = Math.floorMod(entityAnimation.getCurrentFrame() + 1, maxFrameMap.get(rayquaza));
            entityAnimation.setCurrentFrame(currentFrame);
        }

        if(jit != null) {
            entityAnimation = this.jit.getEntityAnimation();
            if (entityAnimation != null && entityAnimation.isStarted()) {
                var currentFrame = Math.floorMod(entityAnimation.getCurrentFrame() + 1, maxFrameMap.computeIfAbsent(jit, entity -> 0));
                entityAnimation.setCurrentFrame(currentFrame);
            }
        }
    }

    private void loadJitModel() {
        var id = "typhlosion";
        var data = ModelLoader.loadModel(id, "D:\\Projects\\The-PokeCraft-Mod\\RKS\\vulkan\\src\\test\\resources\\models\\typhlosion_hisui\\model.gltf", "D:\\Projects\\The-PokeCraft-Mod\\RKS\\vulkan\\src\\test\\resources\\models\\typhlosion_hisui", true);
        this.jit = new Entity("typhlosion", id, new Vector3f(0.0f, 0.0f, 0.0f));
        jit.getRotation().rotateY((float) Math.toRadians(-90.0f));
        jit.updateModelMatrix();
        jit.setEntityAnimation(new Entity.EntityAnimation(true, 0, 0));
        scene.addEntity(jit);
        maxFrameMap.put(jit, data.getAnimationsList().get(0).frames().size());

        renderer.entitiesLoadedTimeStamp = 0;
        this.loadedJitModel = true;
        renderer.loadModels(List.of(data));
    }

    @Override
    public void init(Scene scene, Render renderer) {
        this.renderer = renderer;
        this.scene = scene;
        var id = "rayquaza";
        var data = ModelLoader.loadModel(id, "D:\\Projects\\The-PokeCraft-Mod\\RKS\\vulkan\\src\\test\\resources\\models\\rayquaza\\model.gltf", "D:\\Projects\\The-PokeCraft-Mod\\RKS\\vulkan\\src\\test\\resources\\models\\rayquaza", true);
        this.rayquaza = new Entity("rayquaza", id, new Vector3f(0.0f, 0.0f, 0.0f));
        maxFrameMap.put(rayquaza, data.getAnimationsList().get(0).frames().size());
        rayquaza.getRotation().rotateY((float) Math.toRadians(-90.0f));
        rayquaza.setScale(1);
        rayquaza.updateModelMatrix();
        rayquaza.setEntityAnimation(new Entity.EntityAnimation(true, 0, 0));
        scene.addEntity(rayquaza);
        this.models.add(data);

        renderer.loadModels(models);

        var camera = scene.getCamera();
        camera.setPosition(-6.0f, 2.0f, 0.0f);
        camera.setRotation((float) Math.toRadians(20.0f), (float) Math.toRadians(90.f));

        scene.getAmbientLight().set(0.2f, 0.2f, 0.2f, 1.0f);
        var lights = new ArrayList<>(List.of(this.directionalLight = new Light()));
        directionalLight.getColor().set(1.0f, 1.0f, 1.0f, 1.0f);
        lights.add(directionalLight);
        updateDirectionalLight();

        var lightArr = new Light[lights.size()];
        lightArr = lights.toArray(lightArr);
        scene.setLights(lightArr);
    }

    private void updateDirectionalLight() {
        var zValue = (float) Math.cos(Math.toRadians(this.lightAngle));
        var yValue = (float) Math.sin(Math.toRadians(this.lightAngle));
        var lightDirection = this.directionalLight.getPosition();
        lightDirection.x = 0;
        lightDirection.y = yValue;
        lightDirection.z = zValue;
        lightDirection.normalize();
        lightDirection.w = 0.0f;
    }
}
