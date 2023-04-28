package org.vulkanb.eng.impl;

import org.lwjgl.system.MemoryStack;
import org.tinylog.Logger;
import org.vulkanb.eng.Settings;
import org.vulkanb.eng.Window;
import org.vulkanb.eng.impl.animation.AnimationComputeActivity;
import org.vulkanb.eng.impl.geometry.GeometryRenderActivity;
import org.vulkanb.eng.impl.gui.GuiRenderActivity;
import org.vulkanb.eng.impl.lighting.LightingRenderActivity;
import org.vulkanb.eng.impl.shadows.ShadowRenderActivity;
import org.vulkanb.eng.scene.ModelData;
import org.vulkanb.eng.scene.Scene;
import org.vulkanb.eng.vk.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.lwjgl.vulkan.VK11.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;

public class Render {

    private final AnimationComputeActivity animationComputeActivity;
    private final CommandPool commandPool;
    private final Device device;
    private final GeometryRenderActivity geometryRenderActivity;
    private final GlobalBuffers globalBuffers;
    private final Queue.GraphicsQueue graphQueue;
    private final GuiRenderActivity guiRenderActivity;
    private final Instance instance;
    private final LightingRenderActivity lightingRenderActivity;
    private final PhysicalDevice physicalDevice;
    private final PipelineCache pipelineCache;
    private final Queue.PresentQueue presentQueue;
    private final ShadowRenderActivity shadowRenderActivity;
    private final Surface surface;
    private final TextureCache textureCache;
    private final List<VulkanModel> vulkanModels;
    private CommandBuffer[] commandBuffers;
    private long entitiesLoadedTimeStamp;
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
        this.geometryRenderActivity = new GeometryRenderActivity(this.swapChain, this.pipelineCache, scene, this.globalBuffers);
        this.shadowRenderActivity = new ShadowRenderActivity(this.swapChain, this.pipelineCache, scene);
        var attachments = new ArrayList<>(this.geometryRenderActivity.getAttachments());
        attachments.add(this.shadowRenderActivity.getDepthAttachment());
        this.lightingRenderActivity = new LightingRenderActivity(this.swapChain, this.commandPool, this.pipelineCache, attachments, scene);
        this.animationComputeActivity = new AnimationComputeActivity(this.commandPool, this.pipelineCache);
        this.guiRenderActivity = new GuiRenderActivity(this.swapChain, this.commandPool, this.graphQueue, this.pipelineCache, this.lightingRenderActivity.getLightingFrameBuffer().getLightingRenderPass().getVkRenderPass());
        this.entitiesLoadedTimeStamp = 0;
        createCommandBuffers();
    }

    private CommandBuffer acquireCurrentCommandBuffer() {
        var idx = this.swapChain.getCurrentFrame();

        var fence = this.fences[idx];
        var commandBuffer = this.commandBuffers[idx];

        fence.fenceWait();
        fence.reset();

        return commandBuffer;
    }

    public void close() {
        this.presentQueue.waitIdle();
        this.graphQueue.waitIdle();
        this.device.waitIdle();
        this.textureCache.close();
        this.pipelineCache.close();
        this.guiRenderActivity.close();
        this.lightingRenderActivity.close();
        this.animationComputeActivity.close();
        this.shadowRenderActivity.close();
        this.geometryRenderActivity.close();
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

    public void loadModels(List<ModelData> modelDataList) {
        Logger.debug("Loading {} model(s)", modelDataList.size());
        this.vulkanModels.addAll(this.globalBuffers.loadModels(modelDataList, this.textureCache, this.commandPool, this.graphQueue));
        Logger.debug("Loaded {} model(s)", modelDataList.size());

        this.geometryRenderActivity.loadModels(this.textureCache);
    }

    private void recordCommands() {
        var idx = 0;
        for (var commandBuffer : this.commandBuffers) {
            commandBuffer.reset();
            commandBuffer.beginRecording();
            this.geometryRenderActivity.recordCommandBuffer(commandBuffer, this.globalBuffers, idx);
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
            this.animationComputeActivity.onAnimatedEntitiesLoaded(this.globalBuffers);
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

        this.animationComputeActivity.recordCommandBuffer(this.globalBuffers);
        this.animationComputeActivity.submit();

        var commandBuffer = acquireCurrentCommandBuffer();
        this.geometryRenderActivity.render();
        this.shadowRenderActivity.render();
        submitSceneCommand(this.graphQueue, commandBuffer);

        commandBuffer = this.lightingRenderActivity.beginRecording(this.shadowRenderActivity.getShadowCascades());
        this.lightingRenderActivity.recordCommandBuffer(commandBuffer);
        this.guiRenderActivity.recordCommandBuffer(scene, commandBuffer);
        this.lightingRenderActivity.endRecording(commandBuffer);
        this.lightingRenderActivity.submit(this.graphQueue);

        if (this.swapChain.presentImage(this.graphQueue)) window.setResized(true);
    }

    private void resize(Window window) {
        var engProps = Settings.getInstance();

        this.device.waitIdle();
        this.graphQueue.waitIdle();

        this.swapChain.close();

        this.swapChain = new SwapChain(this.device, this.surface, window, engProps.getRequestedImages(),
                engProps.isvSync());
        this.geometryRenderActivity.resize(this.swapChain);
        this.shadowRenderActivity.resize(this.swapChain);
        recordCommands();
        List<Attachment> attachments = new ArrayList<>(this.geometryRenderActivity.getAttachments());
        attachments.add(this.shadowRenderActivity.getDepthAttachment());
        this.lightingRenderActivity.resize(this.swapChain, attachments);
        this.guiRenderActivity.resize(this.swapChain);
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