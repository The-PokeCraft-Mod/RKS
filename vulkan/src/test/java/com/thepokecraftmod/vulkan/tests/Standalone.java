package com.thepokecraftmod.vulkan.tests;

import com.thepokecraftmod.renderer.Rks;
import com.thepokecraftmod.renderer.wrapper.core.Settings;
import com.thepokecraftmod.renderer.Window;
import com.thepokecraftmod.renderer.scene.*;
import com.thepokecraftmod.renderer.wrapper.init.ExtensionProvider;
import com.thepokecraftmod.rks.ModelLocator;
import com.thepokecraftmod.rks.assimp.AssimpModelLoader;
import com.thepokecraftmod.rks.model.Model;
import com.thepokecraftmod.rks.model.animation.Animation;
import com.thepokecraftmod.rks.model.config.animation.AnimationGroup;
import com.thepokecraftmod.rks.model.texture.TextureType;
import com.thepokecraftmod.vulkan.util.DebugWindow;
import com.thepokecraftmod.vulkan.util.TestModelLocator;
import org.joml.Vector3f;
import org.lwjgl.vulkan.VK10;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.assimp.Assimp.*;
import static org.lwjgl.glfw.GLFW.*;

public class Standalone {
    private static final float MOUSE_SENSITIVITY = 0.2f;
    private static final float MOVEMENT_SPEED = 8 / 1000000000f;
    private final Map<RksEntity, Integer> maxFrameMap = new HashMap<>();
    private final Rks rks;
    private float angleInc;
    private final List<ModelData> models = new ArrayList<>();
    private RksEntity rayquaza;
    private RksEntity jit;
    private final Light directionalLight;
    private float lightAngle = 90.1f;

    public static void main(String[] args) {
        System.loadLibrary("renderdoc");
        new Standalone();
    }

    public Standalone() {
        this.rks = new Rks(new DebugWindow("RKS Standalone Test"), new ExtensionProvider());
        var id = "rayquaza";
        var locator = new TestModelLocator("testModels/rayquaza");
        var model = AssimpModelLoader.load(
                "model.gltf",
                locator,
                aiProcess_GenSmoothNormals
                        | aiProcess_FixInfacingNormals
                        | aiProcess_CalcTangentSpace
                        | aiProcess_LimitBoneWeights
        );
        loadTextures(locator, model);
        var data = ModelProcessor.loadModel(id, locator, model, loadAnimations(locator, model));


        this.rayquaza = new RksEntity(id, id, new Vector3f(0.0f, 0.0f, 0.0f));
        maxFrameMap.put(rayquaza, data.getAnimations().get(0).frames().size());
        rayquaza.getRotation().rotateY((float) Math.toRadians(-90.0f));
        rayquaza.setScale(1);
        rayquaza.updateModelMatrix();
        rayquaza.setEntityAnimation(new RksEntity.AnimationInstance(true, 0, 0));

        rks.scene.addEntity(rayquaza);
        models.add(data);
        rks.renderer.loadModels(models);

        var camera = rks.scene.getCamera();
        camera.setPosition(-6.0f, 2.0f, 0.0f);
        camera.setRotation((float) Math.toRadians(20.0f), (float) Math.toRadians(90.f));

        rks.scene.getAmbientLight().set(0.2f, 0.2f, 0.2f, 1.0f);
        var lights = new ArrayList<>(List.of(this.directionalLight = new Light()));
        directionalLight.getColor().set(1.0f, 1.0f, 1.0f, 1.0f);
        lights.add(directionalLight);
        updateDirectionalLight();

        var lightArr = new Light[lights.size()];
        lightArr = lights.toArray(lightArr);
        rks.scene.setLights(lightArr);

        // Main Loop
        var settings = Settings.getInstance();
        var initialTime = System.nanoTime();
        var timeU = 1000000000d / settings.getUpdatesPerSecond();
        double deltaU = 0;

        var updateTime = initialTime;
        while (!rks.window.shouldClose()) {
            rks.scene.getCamera().setHasMoved(false);
            rks.window.pollEvents();

            var currentTime = System.nanoTime();
            deltaU += (currentTime - initialTime) / timeU;
            initialTime = currentTime;

            if (deltaU >= 1) {
                var diffTimeNanos = currentTime - updateTime;
                handleInput(rks.window, rks.scene, diffTimeNanos, false);
                updateTime = currentTime;
                deltaU--;
            }

            rks.renderer.render(rks.window, rks.scene);
        }
        rks.close();
    }

    private void loadTextures(TestModelLocator locator, Model model) {
        for (var material : model.config().materials.values()) {
            rks.renderer.textureCache.createTexture(rks.renderer.device, material.hashCode() + "-diffuse", locator.readImage(material.getTextures(TextureType.ALBEDO)), false, VK10.VK_FORMAT_R8G8B8A8_SRGB);
            rks.renderer.textureCache.createTexture(rks.renderer.device, material.hashCode() + "-normal", locator.readImage(material.getTextures(TextureType.NORMALS)), false, VK10.VK_FORMAT_R8G8B8A8_SRGB);
            rks.renderer.textureCache.createTexture(rks.renderer.device, material.hashCode() + "-roughnessMetallic", locator.readImage(material.getTextures(TextureType.ROUGHNESS)), false, VK10.VK_FORMAT_R8G8B8A8_SRGB);
        }
    }

    private List<Animation> loadAnimations(ModelLocator locator, Model model) {
        var animations = new ArrayList<Animation>();
        var flyingAnims = model.config().animations.get(AnimationGroup.FLYING);
        for (var entry : flyingAnims.entrySet()) {
            var bytes = locator.getFile(entry.getValue().getMainAnimation());
            var pAnimation = ByteBuffer.wrap(bytes);
            var trAnimation = com.thepokecraftmod.rks.model.animation.tranm.Animation.getRootAsAnimation(pAnimation);
            animations.add(new Animation(entry.getKey(), trAnimation, model.skeleton()));
        }

        return animations;
    }

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

        // if (window.isKeyPressed(GLFW_KEY_O)) if (!loadedJitModel) loadJitModel();
        if (window.isKeyPressed(GLFW_KEY_SPACE)) rayquaza.getAnimation().playing = !rayquaza.getAnimation().playing;

        var mouseInput = window.getMouseInput();
        if (mouseInput.isRightButtonPressed()) {
            var displVec = mouseInput.getDisplVec();
            camera.addRotation((float) Math.toRadians(-displVec.x * MOUSE_SENSITIVITY), (float) Math.toRadians(-displVec.y * MOUSE_SENSITIVITY));
        }

        this.lightAngle += this.angleInc;
        if (this.lightAngle < 0) this.lightAngle = 0;
        else if (this.lightAngle > 180) this.lightAngle = 180;
        updateDirectionalLight();

        var entityAnimation = this.rayquaza.getAnimation();
        if (entityAnimation != null && entityAnimation.playing)
            entityAnimation.currentFrame = Math.floorMod(entityAnimation.currentFrame + 1, maxFrameMap.get(rayquaza));

        if (jit != null) {
            entityAnimation = this.jit.getAnimation();
            if (entityAnimation != null && entityAnimation.playing) {
                var currentFrame = Math.floorMod(entityAnimation.currentFrame + 1, maxFrameMap.computeIfAbsent(jit, entity -> 0));
                entityAnimation.currentFrame = currentFrame;
            }
        }
    }

//    private void loadJitModel() {
//        var id = "typhlosion";
//        var data = ModelProcessor.loadModel(id, "D:\\Projects\\The-PokeCraft-Mod\\RKS\\vulkan\\src\\test\\resources\\models\\typhlosion_hisui\\model.gltf", "D:\\Projects\\The-PokeCraft-Mod\\RKS\\vulkan\\src\\test\\resources\\models\\typhlosion_hisui", true);
//        models.add(data);
//        this.jit = new Entity(id, id, new Vector3f(0.0f, 0.0f, 0.0f));
//        jit.getRotation().rotateY((float) Math.toRadians(-90.0f));
//        jit.updateModelMatrix();
//        jit.setEntityAnimation(new Entity.EntityAnimation(true, 0, 0));
//        rks.scene.addEntity(jit);
//        maxFrameMap.put(jit, data.getAnimationsList().get(0).frames().size());
//
//        rks.renderer.entitiesLoadedTimeStamp = 0;
//        this.loadedJitModel = true;
//        rks.renderer.loadModels(models);
//    }

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
