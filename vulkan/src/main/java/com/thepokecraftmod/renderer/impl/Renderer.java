package com.thepokecraftmod.renderer.impl;

import com.thepokecraftmod.renderer.wrapper.core.Settings;
import com.thepokecraftmod.renderer.Window;
import com.thepokecraftmod.renderer.impl.animation.GpuAnimator;
import com.thepokecraftmod.renderer.impl.geometry.GeometryPass;
import com.thepokecraftmod.renderer.impl.lighting.LightPass;
import com.thepokecraftmod.renderer.impl.shadows.ShadowPass;
import com.thepokecraftmod.renderer.scene.ModelData;
import com.thepokecraftmod.renderer.scene.Scene;
import com.thepokecraftmod.renderer.wrapper.cmd.CmdBuffer;
import com.thepokecraftmod.renderer.wrapper.cmd.CmdPool;
import com.thepokecraftmod.renderer.wrapper.core.Fence;
import com.thepokecraftmod.renderer.wrapper.core.Queue;
import com.thepokecraftmod.renderer.wrapper.core.Swapchain;
import com.thepokecraftmod.renderer.wrapper.init.Device;
import com.thepokecraftmod.renderer.wrapper.init.ExtensionProvider;
import com.thepokecraftmod.renderer.wrapper.init.Instance;
import com.thepokecraftmod.renderer.wrapper.init.PhysicalDevice;
import com.thepokecraftmod.renderer.wrapper.pipeline.PipelineCache;
import com.thepokecraftmod.renderer.wrapper.window.Surface;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.lwjgl.vulkan.VK11.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;

public class Renderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(Renderer.class);
    private final GpuAnimator computeAnimator;
    private final GeometryPass geometryPass;
    private final LightPass lightPass;
    private final ShadowPass shadowPass;
    public final CmdPool cmdPool;

    public final Device device;
    public final GlobalBuffers globalBuffers;
    public final Queue.GraphicsQueue graphicsQueue;
    public final Instance instance;
    public final PhysicalDevice physicalDevice;
    public final PipelineCache pipelineCache;
    public final Queue.PresentQueue presentQueue;
    public final Surface surface;
    public final TextureCache textureCache;
    public final List<GpuModel> gpuModels;
    public CmdBuffer[] cmdBuffers;
    public long entitiesLoadedTimeStamp;
    public Fence[] fences;
    public Swapchain swapChain;

    public Renderer(Window window, ExtensionProvider provider, Scene scene) {
        var settings = Settings.getInstance();
        this.instance = new Instance(settings.isValidate(), window != null, provider);
        this.physicalDevice = PhysicalDevice.createPhysicalDevice(this.instance, settings.preferredDevice());
        this.device = new Device(this.instance, this.physicalDevice, provider);
        this.surface = new Surface(this.physicalDevice, window.handle());
        this.graphicsQueue = new Queue.GraphicsQueue(this.device, 0);
        this.presentQueue = new Queue.PresentQueue(this.device, this.surface, 0);
        this.swapChain = new Swapchain(this.device, this.surface, window, settings.getRequestedImages(), settings.isvSync());
        this.cmdPool = new CmdPool(this.device, this.graphicsQueue.getQueueFamilyIndex());
        this.pipelineCache = new PipelineCache(this.device);
        this.gpuModels = new ArrayList<>();
        this.textureCache = new TextureCache();
        this.globalBuffers = new GlobalBuffers(this.device);
        this.geometryPass = new GeometryPass(this.swapChain, this.pipelineCache, scene, this.globalBuffers);
        this.shadowPass = new ShadowPass(this.swapChain, this.pipelineCache, scene);
        var attachments = new ArrayList<>(this.geometryPass.getAttachments());
        attachments.add(this.shadowPass.getDepthAttachment());
        this.lightPass = new LightPass(this.swapChain, this.cmdPool, this.pipelineCache, attachments, scene);
        this.computeAnimator = new GpuAnimator(this.cmdPool, this.pipelineCache);
        this.entitiesLoadedTimeStamp = 0;
        createCommandBuffers();
    }

    private CmdBuffer acquireCurrentCommandBuffer() {
        var idx = this.swapChain.getCurrentFrame();

        var fence = this.fences[idx];
        var commandBuffer = this.cmdBuffers[idx];

        fence.waitForFence();
        fence.reset();

        return commandBuffer;
    }

    public void close() {
        this.presentQueue.waitIdle();
        this.graphicsQueue.waitIdle();
        this.device.waitIdle();
        this.textureCache.close();
        this.pipelineCache.close();
        this.lightPass.close();
        this.computeAnimator.close();
        this.shadowPass.close();
        this.geometryPass.close();
        Arrays.stream(this.cmdBuffers).forEach(CmdBuffer::close);
        Arrays.stream(this.fences).forEach(Fence::close);
        this.cmdPool.close();
        this.swapChain.close();
        this.surface.close();
        this.globalBuffers.close();
        this.device.close();
        this.physicalDevice.close();
        this.instance.close();
    }

    private void createCommandBuffers() {
        var imageCount = swapChain.getImageCount();
        this.cmdBuffers = new CmdBuffer[imageCount];
        this.fences = new Fence[imageCount];

        for (var i = 0; i < imageCount; i++) {
            this.cmdBuffers[i] = cmdPool.newBuffer(true, false);
            this.fences[i] = new Fence(this.device, true);
        }
    }

    public void loadModels(List<ModelData> models) {
        LOGGER.info("Loading {} model(s)", models.size());
        this.gpuModels.addAll(this.globalBuffers.loadModels(models, this.textureCache, this.cmdPool, this.graphicsQueue));
        LOGGER.info("Loaded {} model(s)", models.size());

        this.geometryPass.loadModels(this.textureCache);
    }

    private void recordCommands() {
        var idx = 0;
        for (var cmdBuffer : this.cmdBuffers) {
            var currentIdx = idx;
            cmdBuffer.reset();
            cmdBuffer.record(null, false, () -> {
                this.geometryPass.recordCommandBuffer(cmdBuffer, this.globalBuffers, currentIdx);
                this.shadowPass.recordCommandBuffer(cmdBuffer, this.globalBuffers, currentIdx);
                return null;
            });
            idx++;
        }
    }

    public void render(Window window, Scene scene) {
        if (this.entitiesLoadedTimeStamp < scene.getEntitiesLoadedTimeStamp()) {
            this.entitiesLoadedTimeStamp = scene.getEntitiesLoadedTimeStamp();
            this.device.waitIdle();
            this.globalBuffers.loadEntities(this.gpuModels, scene, this.cmdPool, this.graphicsQueue, this.swapChain.getImageCount());
            this.computeAnimator.onAnimatedEntitiesLoaded(this.globalBuffers);
            recordCommands();
        }
        if (window.getWidth() <= 0 && window.getHeight() <= 0) return;
        if (window.isResized() || this.swapChain.acquireNextImage()) {
            window.resetResized();
            resize(window);
            scene.getProjection().resize(window.getWidth(), window.getHeight());
            this.swapChain.acquireNextImage();
        }

        this.globalBuffers.loadInstanceData(scene, this.gpuModels, this.swapChain.getCurrentFrame());

        this.computeAnimator.recordCommandBuffer(this.globalBuffers);
        this.computeAnimator.submit();

        var commandBuffer = acquireCurrentCommandBuffer();
        this.geometryPass.render();
        this.shadowPass.render();
        submitSceneCommand(this.graphicsQueue, commandBuffer);

        commandBuffer = this.lightPass.beginRecording(this.shadowPass.getShadowCascades());
        this.lightPass.recordCommandBuffer(commandBuffer);
        this.lightPass.endRecording(commandBuffer);
        this.lightPass.submit(this.graphicsQueue);

        if (this.swapChain.presentImage(this.graphicsQueue)) window.setResized(true);
    }

    private void resize(Window window) {
        var settings = Settings.getInstance();
        this.device.waitIdle();
        this.graphicsQueue.waitIdle();

        this.swapChain.close();
        this.swapChain = new Swapchain(this.device, this.surface, window, settings.getRequestedImages(), settings.isvSync());
        this.geometryPass.resize(this.swapChain);
        this.shadowPass.resize(this.swapChain);
        recordCommands();
        var attachments = new ArrayList<>(this.geometryPass.getAttachments());
        attachments.add(this.shadowPass.getDepthAttachment());
        this.lightPass.resize(this.swapChain, attachments);
    }

    public void submitSceneCommand(Queue queue, CmdBuffer cmdBuffer) {
        try (var stack = MemoryStack.stackPush()) {
            var idx = this.swapChain.getCurrentFrame();
            var currentFence = this.fences[idx];
            var syncSemaphores = this.swapChain.getSyncSemaphoresList()[idx];
            queue.submit(stack.pointers(cmdBuffer.vk()),
                    stack.longs(syncSemaphores.imgAcquisitionSemaphore().getVkSemaphore()),
                    stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT),
                    stack.longs(syncSemaphores.geometryCompleteSemaphore().getVkSemaphore()), currentFence);
        }
    }
}