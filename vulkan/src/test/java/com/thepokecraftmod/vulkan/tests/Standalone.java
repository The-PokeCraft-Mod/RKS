package com.thepokecraftmod.vulkan.tests;

import com.thepokecraftmod.vulkan.util.DebugWindow;
import org.joml.Vector3f;
import org.vulkanb.eng.RKS;
import org.vulkanb.eng.RenderingImpl;
import org.vulkanb.eng.Window;
import org.vulkanb.eng.impl.Render;
import org.vulkanb.eng.impl.gui.GuiRenderActivity;
import org.vulkanb.eng.scene.*;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;

public class Standalone implements RenderingImpl {
    private static final float MOUSE_SENSITIVITY = 0.2f;
    private static final float MOVEMENT_SPEED = 10.0f / 1000000000f;
    private float angleInc;
    private Entity bobEntity;
    private Light directionalLight;
    private float lightAngle = 90.1f;
    private int maxFrames = 0;

    public static void main(String[] args) {
        new RKS(new Standalone(), new DebugWindow("RKS Standalone Test", new GuiRenderActivity.KeyCallback(), new GuiRenderActivity.CharCallBack())).start();
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

        if (window.isKeyPressed(GLFW_KEY_SPACE))
            this.bobEntity.getEntityAnimation().setStarted(!this.bobEntity.getEntityAnimation().isStarted());

        var mouseInput = window.getMouseInput();
        if (mouseInput.isRightButtonPressed()) {
            var displVec = mouseInput.getDisplVec();
            camera.addRotation((float) Math.toRadians(-displVec.x * MOUSE_SENSITIVITY), (float) Math.toRadians(-displVec.y * MOUSE_SENSITIVITY));
        }

        this.lightAngle += this.angleInc;
        if (this.lightAngle < 0) this.lightAngle = 0;
        else if (this.lightAngle > 180) this.lightAngle = 180;
        updateDirectionalLight();

        var entityAnimation = this.bobEntity.getEntityAnimation();
        if (entityAnimation != null && entityAnimation.isStarted()) {
            var currentFrame = Math.floorMod(entityAnimation.getCurrentFrame() + 1, this.maxFrames);
            entityAnimation.setCurrentFrame(currentFrame);
        }
    }

    @Override
    public void init(Scene scene, Render render) {
        List<ModelData> modelDataList = new ArrayList<>();

        var bobModelId = "bob-model";
        var bobModelData = ModelLoader.loadModel(bobModelId, "D:\\Projects\\The-PokeCraft-Mod\\RKS\\vulkan\\src\\test\\resources\\models\\rayquaza\\model.gltf", "D:\\Projects\\The-PokeCraft-Mod\\RKS\\vulkan\\src\\test\\resources\\models\\rayquaza", true);
        this.maxFrames = bobModelData.getAnimationsList().get(0).frames().size();
        modelDataList.add(bobModelData);
        this.bobEntity = new Entity("BobEntity", bobModelId, new Vector3f(0.0f, 0.0f, 0.0f));
        this.bobEntity.getRotation().rotateY((float) Math.toRadians(-90.0f));
        this.bobEntity.setScale(1);
        this.bobEntity.updateModelMatrix();
        this.bobEntity.setEntityAnimation(new Entity.EntityAnimation(true, 0, 0));
        scene.addEntity(this.bobEntity);

        render.loadModels(modelDataList);

        var camera = scene.getCamera();
        camera.setPosition(-6.0f, 2.0f, 0.0f);
        camera.setRotation((float) Math.toRadians(20.0f), (float) Math.toRadians(90.f));

        scene.getAmbientLight().set(0.2f, 0.2f, 0.2f, 1.0f);
        List<Light> lights = new ArrayList<>();
        this.directionalLight = new Light();
        this.directionalLight.getColor().set(1.0f, 1.0f, 1.0f, 1.0f);
        lights.add(this.directionalLight);
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
