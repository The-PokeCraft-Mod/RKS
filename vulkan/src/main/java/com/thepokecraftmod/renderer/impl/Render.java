package com.thepokecraftmod.renderer.impl;

import org.lwjgl.system.MemoryStack;
import org.tinylog.Logger;
import com.thepokecraftmod.renderer.Settings;
import com.thepokecraftmod.renderer.Window;
import com.thepokecraftmod.renderer.impl.animation.GpuAnimator;
import com.thepokecraftmod.renderer.impl.geometry.GeometryPassRenderer;
import com.thepokecraftmod.renderer.impl.gui.GuiPassRenderer;
import com.thepokecraftmod.renderer.impl.lighting.LightPassRenderer;
import com.thepokecraftmod.renderer.impl.shadows.ShadowRenderActivity;
import com.thepokecraftmod.renderer.scene.ModelData;
import com.thepokecraftmod.renderer.scene.Scene;
import com.thepokecraftmod.renderer.vk.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.lwjgl.vulkan.VK11.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;

public class Render {

    private GpuAnimator gpuAnimator;
    private final CommandPool commandPool;
    private final Device device;
    private final GeometryPassRenderer geometryPassRenderer;
    private final GlobalBuffers globalBuffers;
    private final Queue.GraphicsQueue graphQueue;
    private final GuiPassRenderer guiPassRenderer;
    private final Instance instance;
    private final LightPassRenderer lightPassRenderer;
    private final PhysicalDevice physicalDevice;
    private final PipelineCache pipelineCache;
    private final Queue.PresentQueue presentQueue;
    private final ShadowRenderActivity shadowRenderActivity;
    private final Surface surface;
    private final TextureCache textureCache;
    private final List<VulkanModel> vulkanModels;
    private CommandBuffer[] commandBuffers;
    public long entitiesLoadedTimeStamp;
    private Fence[] fences;
    private SwapChain swapChain;

    public Render(Window window, Scene scene) {
        var engProps = Settings.getInstance();
        this.instance = new Instance(engProps.isValidate(), window != null);
        this.physicalDevice = PhysicalDevice.createPhysicalDevice(this.instance, engProps.getPhysDeviceName());
        this.device = new Device(this.instance, this.physicalDevice);
        this.surface = new Surface(this.physicalDevice, window.getWindowHandle());
        this.graphQueue = new Queue.GraphicsQueue(this.device, 0);
        this.presentQueue = new Queue.PresentQueue(this.device, this.surface, 0);
        this.swapChain = new SwapChain(this.device, this.surface, window, engProps.getRequestedImages(), engProps.isvSync());
        this.commandPool = new CommandPool(this.device, this.graphQueue.getQueueFamilyIndex());
        this.pipelineCache = new PipelineCache(this.device);
        this.vulkanModels = new ArrayList<>();
        this.textureCache = new TextureCache();
        this.globalBuffers = new GlobalBuffers(this.device);
        this.geometryPassRenderer = new GeometryPassRenderer(this.swapChain, this.pipelineCache, scene, this.globalBuffers);
        this.shadowRenderActivity = new ShadowRenderActivity(this.swapChain, this.pipelineCache, scene);
        var attachments = new ArrayList<>(this.geometryPassRenderer.getAttachments());
        attachments.add(this.shadowRenderActivity.getDepthAttachment());
        this.lightPassRenderer = new LightPassRenderer(this.swapChain, this.commandPool, this.pipelineCache, attachments, scene);
        this.gpuAnimator = new GpuAnimator(this.commandPool, this.pipelineCache);
        this.guiPassRenderer = new GuiPassRenderer(this.swapChain, this.commandPool, this.graphQueue, this.pipelineCache, this.lightPassRenderer.getLightingFrameBuffer().getLightingRenderPass().getVkRenderPass());
        this.entitiesLoadedTimeStamp = 0;
        createCommandBuffers();
    }

    private CommandBuffer acquireCurrentCommandBuffer() {
        var idx = this.swapChain.getCurrentFrame();

        var fence = this.fences[idx];
        var commandBuffer = this.commandBuffers[idx];

        fence.waitForFence();
        fence.reset();

        return commandBuffer;
    }

    public void close() {
        this.presentQueue.waitIdle();
        this.graphQueue.waitIdle();
        this.device.waitIdle();
        this.textureCache.close();
        this.pipelineCache.close();
        this.guiPassRenderer.close();
        this.lightPassRenderer.close();
        this.gpuAnimator.close();
        this.shadowRenderActivity.close();
        this.geometryPassRenderer.close();
        Arrays.stream(this.commandBuffers).forEach(CommandBuffer::close);
        Arrays.stream(this.fences).forEach(Fence::close);
        this.commandPool.close();
        this.swapChain.close();
        this.surface.close();
        this.globalBuffers.close();
        this.device.close();
        this.physicalDevice.close();
        this.instance.close();
    }

    private void createCommandBuffers() {
        var numImages = this.swapChain.getNumImages();
        this.commandBuffers = new CommandBuffer[numImages];
        this.fences = new Fence[numImages];

        for (var i = 0; i < numImages; i++) {
            this.commandBuffers[i] = new CommandBuffer(this.commandPool, true, false);
            this.fences[i] = new Fence(this.device, true);
        }
    }

    public void loadModels(List<ModelData> models) {
        Logger.debug("Loading {} model(s)", models.size());
        this.vulkanModels.addAll(this.globalBuffers.loadModels(models, this.textureCache, this.commandPool, this.graphQueue));
        Logger.debug("Loaded {} model(s)", models.size());

        this.geometryPassRenderer.loadModels(this.textureCache);
    }

    private void recordCommands() {
        var idx = 0;
        for (var commandBuffer : this.commandBuffers) {
            commandBuffer.reset();
            commandBuffer.beginRecording();
            this.geometryPassRenderer.recordCommandBuffer(commandBuffer, this.globalBuffers, idx);
            this.shadowRenderActivity.recordCommandBuffer(commandBuffer, this.globalBuffers, idx);
            commandBuffer.endRecording();
            idx++;
        }
    }

    public void render(Window window, Scene scene) {
        if (this.entitiesLoadedTimeStamp < scene.getEntitiesLoadedTimeStamp()) {
            this.entitiesLoadedTimeStamp = scene.getEntitiesLoadedTimeStamp();
            this.device.waitIdle();
            this.globalBuffers.loadEntities(this.vulkanModels, scene, this.commandPool, this.graphQueue, this.swapChain.getNumImages());
            this.gpuAnimator.onAnimatedEntitiesLoaded(this.globalBuffers);
            recordCommands();
        }
        if (window.getWidth() <= 0 && window.getHeight() <= 0) return;
        if (window.isResized() || this.swapChain.acquireNextImage()) {
            window.resetResized();
            resize(window);
            scene.getProjection().resize(window.getWidth(), window.getHeight());
            this.swapChain.acquireNextImage();
        }

        this.globalBuffers.loadInstanceData(scene, this.vulkanModels, this.swapChain.getCurrentFrame());

        this.gpuAnimator.recordCommandBuffer(this.globalBuffers);
        this.gpuAnimator.submit();

        var commandBuffer = acquireCurrentCommandBuffer();
        this.geometryPassRenderer.render();
        this.shadowRenderActivity.render();
        submitSceneCommand(this.graphQueue, commandBuffer);

        commandBuffer = this.lightPassRenderer.beginRecording(this.shadowRenderActivity.getShadowCascades());
        this.lightPassRenderer.recordCommandBuffer(commandBuffer);
        this.guiPassRenderer.recordCommandBuffer(scene, commandBuffer);
        this.lightPassRenderer.endRecording(commandBuffer);
        this.lightPassRenderer.submit(this.graphQueue);

        if (this.swapChain.presentImage(this.graphQueue)) window.setResized(true);
    }

    private void resize(Window window) {
        var settings = Settings.getInstance();
        this.device.waitIdle();
        this.graphQueue.waitIdle();

        this.swapChain.close();
        this.swapChain = new SwapChain(this.device, this.surface, window, settings.getRequestedImages(), settings.isvSync());
        this.geometryPassRenderer.resize(this.swapChain);
        this.shadowRenderActivity.resize(this.swapChain);
        recordCommands();
        var attachments = new ArrayList<>(this.geometryPassRenderer.getAttachments());
        attachments.add(this.shadowRenderActivity.getDepthAttachment());
        this.lightPassRenderer.resize(this.swapChain, attachments);
        this.guiPassRenderer.resize(this.swapChain);
    }

    public void submitSceneCommand(Queue queue, CommandBuffer commandBuffer) {
        try (var stack = MemoryStack.stackPush()) {
            var idx = this.swapChain.getCurrentFrame();
            var currentFence = this.fences[idx];
            var syncSemaphores = this.swapChain.getSyncSemaphoresList()[idx];
            queue.submit(stack.pointers(commandBuffer.getVkCommandBuffer()),
                    stack.longs(syncSemaphores.imgAcquisitionSemaphore().getVkSemaphore()),
                    stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT),
                    stack.longs(syncSemaphores.geometryCompleteSemaphore().getVkSemaphore()), currentFence);
        }
    }
}