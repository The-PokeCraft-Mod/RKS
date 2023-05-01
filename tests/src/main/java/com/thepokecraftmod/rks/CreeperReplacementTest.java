package com.thepokecraftmod.rks;

import com.thepokecraftmod.renderer.Rks;
import com.thepokecraftmod.renderer.Settings;
import com.thepokecraftmod.renderer.Window;
import com.thepokecraftmod.renderer.scene.*;
import com.thepokecraftmod.renderer.vk.init.ExtensionProvider;
import com.thepokecraftmod.rks.assimp.AssimpModelLoader;
import com.thepokecraftmod.rks.model.Model;
import com.thepokecraftmod.rks.model.animation.Animation;
import com.thepokecraftmod.rks.model.config.animation.AnimationGroup;
import com.thepokecraftmod.rks.model.texture.TextureType;
import com.thepokecraftmod.rks.util.DebugWindow;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.vulkan.VK10;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.assimp.Assimp.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.vulkan.KHRExternalMemory.VK_KHR_EXTERNAL_MEMORY_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalMemoryCapabilities.VK_KHR_EXTERNAL_MEMORY_CAPABILITIES_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalMemoryFd.VK_KHR_EXTERNAL_MEMORY_FD_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalMemoryWin32.VK_KHR_EXTERNAL_MEMORY_WIN32_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalSemaphore.VK_KHR_EXTERNAL_SEMAPHORE_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalSemaphoreCapabilities.VK_KHR_EXTERNAL_SEMAPHORE_CAPABILITIES_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalSemaphoreFd.VK_KHR_EXTERNAL_SEMAPHORE_FD_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalSemaphoreWin32.VK_KHR_EXTERNAL_SEMAPHORE_WIN32_EXTENSION_NAME;

public class CreeperReplacementTest {
    public static CreeperReplacementTest INSTANCE;
    public final DebugWindow vkTestWindow;
    private final Rks rks;
    private final List<ModelData> models = new ArrayList<>();
    private final Map<RksEntity, Integer> maxFrameMap = new HashMap<>();
    private final Light directionalLight;
    public final RksEntity rayquaza;
    private final ModelData rayquazaData;
    private final List<Animation> rayquazaAnimations;
    private float lightAngle = 90.1f;
    private float angleInc;

    // Loop vars
    private int deltaU;
    private long initialTime = System.nanoTime();

    public CreeperReplacementTest(int width, int height) {
        INSTANCE = this;
        this.vkTestWindow = new DebugWindow("Vk Online Render", width, height);
        var instanceFactory = new ExtensionProvider()
                .instanceExtension(VK_KHR_EXTERNAL_MEMORY_CAPABILITIES_EXTENSION_NAME)
                .instanceExtension(VK_KHR_EXTERNAL_SEMAPHORE_CAPABILITIES_EXTENSION_NAME)
                .deviceExtension(VK_KHR_EXTERNAL_MEMORY_EXTENSION_NAME)
                .deviceExtension(VK_KHR_EXTERNAL_SEMAPHORE_EXTENSION_NAME);

        // Windows Only
        if (true) instanceFactory
                .deviceExtension(VK_KHR_EXTERNAL_MEMORY_WIN32_EXTENSION_NAME)
                .deviceExtension(VK_KHR_EXTERNAL_SEMAPHORE_WIN32_EXTENSION_NAME);
        else instanceFactory
                .deviceExtension(VK_KHR_EXTERNAL_MEMORY_FD_EXTENSION_NAME)
                .deviceExtension(VK_KHR_EXTERNAL_SEMAPHORE_FD_EXTENSION_NAME);

        this.rks = new Rks(
                vkTestWindow,
                instanceFactory
        );
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

        this.rayquazaAnimations = loadAnimations(locator, model);
        this.rayquazaData = ModelProcessor.loadModel(id, locator, model, rayquazaAnimations);
        this.rayquaza = new RksEntity(id, id, new Vector3f(0.0f, 0.0f, 0.0f));
        maxFrameMap.put(rayquaza, rayquazaData.getAnimations().get(0).frames().size());
        rayquaza.getRotation().rotateY((float) Math.toRadians(-90.0f));
        rayquaza.updateModelMatrix();
        rayquaza.setEntityAnimation(new RksEntity.AnimationInstance(true, 0, 0));

        rks.scene.addEntity(rayquaza);
        models.add(rayquazaData);
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
    }

    public void handleInput(Window window, Scene scene, boolean inputConsumed) {
        if (inputConsumed) return;
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

        if (window.isKeyPressed(GLFW_KEY_SPACE)) rayquaza.getAnimation().playing = !rayquaza.getAnimation().playing;

        this.lightAngle += this.angleInc;
        if (this.lightAngle < 0) this.lightAngle = 0;
        else if (this.lightAngle > 180) this.lightAngle = 180;
        updateDirectionalLight();
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

    public void render(Matrix4f projectionMatrix) {
        rks.scene.projection.projectionMatrix.set(projectionMatrix);
        var settings = Settings.getInstance();
        var timeU = 1000000000d / settings.getUpdatesPerSecond();

        rks.scene.getCamera().setHasMoved(false);
        rks.window.pollEvents();

        var currentTime = System.nanoTime();
        deltaU += (currentTime - initialTime) / timeU;

        var entityAnimation = this.rayquaza.getAnimation();
        if (entityAnimation != null && entityAnimation.playing)
            entityAnimation.currentFrame = (int) rayquazaAnimations.get(0).getAnimationTime((currentTime - initialTime) / 1000000000f);

        if (deltaU >= 1) {
            handleInput(rks.window, rks.scene, false);
            deltaU--;
        }

        rks.renderer.render(rks.window, rks.scene);
    }
}
