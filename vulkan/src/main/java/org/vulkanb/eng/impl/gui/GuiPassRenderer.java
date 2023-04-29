package org.vulkanb.eng.impl.gui;

import imgui.ImGui;
import imgui.ImVec4;
import imgui.flag.ImGuiKey;
import imgui.type.ImInt;
import org.lwjgl.glfw.GLFWCharCallbackI;
import org.lwjgl.glfw.GLFWKeyCallbackI;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkViewport;
import org.vulkanb.eng.Settings;
import org.vulkanb.eng.scene.Scene;
import org.vulkanb.eng.vk.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.vulkan.VK11.*;

public class GuiPassRenderer {
    private static final String GUI_FRAGMENT_SHADER_FILE_GLSL = "gui_fragment.glsl";
    private static final String GUI_FRAGMENT_SHADER_FILE_SPV = GUI_FRAGMENT_SHADER_FILE_GLSL + ".spv";
    private static final String GUI_VERTEX_SHADER_FILE_GLSL = "gui_vertex.glsl";
    private static final String GUI_VERTEX_SHADER_FILE_SPV = GUI_VERTEX_SHADER_FILE_GLSL + ".spv";
    private final Device device;
    private DescriptorPool descriptorPool;
    private DescriptorSetLayout[] descriptorSetLayouts;
    private Texture fontsTexture;
    private TextureSampler fontsTextureSampler;
    private VulkanBuffer[] indicesBuffers;
    private Pipeline pipeline;
    private ShaderProgram shaderProgram;
    private SwapChain swapChain;
    private TextureDescriptorSet textureDescriptorSet;
    private DescriptorSetLayout.SamplerDescriptorSetLayout textureDescriptorSetLayout;
    private VulkanBuffer[] vertexBuffers;

    public GuiPassRenderer(SwapChain swapChain, CommandPool commandPool, Queue queue, PipelineCache pipelineCache,
                           long vkRenderPass) {
        this.swapChain = swapChain;
        this.device = swapChain.getDevice();

        createShaders();
        createUIResources(swapChain, commandPool, queue);
        createDescriptorPool();
        createDescriptorSets();
        createPipeline(pipelineCache, vkRenderPass);
    }

    public void close() {
        this.textureDescriptorSetLayout.close();
        this.fontsTextureSampler.close();
        this.descriptorPool.close();
        this.fontsTexture.close();
        Arrays.stream(this.vertexBuffers).filter(Objects::nonNull).forEach(VulkanBuffer::close);
        Arrays.stream(this.indicesBuffers).filter(Objects::nonNull).forEach(VulkanBuffer::close);
        ImGui.destroyContext();
        this.pipeline.close();
        this.shaderProgram.close();
    }

    private void createDescriptorPool() {
        List<DescriptorPool.DescriptorTypeCount> descriptorTypeCounts = new ArrayList<>();
        descriptorTypeCounts.add(new DescriptorPool.DescriptorTypeCount(1, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER));
        this.descriptorPool = new DescriptorPool(this.device, descriptorTypeCounts);
    }

    private void createDescriptorSets() {
        this.textureDescriptorSetLayout = new DescriptorSetLayout.SamplerDescriptorSetLayout(this.device, 1, 0, VK_SHADER_STAGE_FRAGMENT_BIT);
        this.descriptorSetLayouts = new DescriptorSetLayout[]{
                this.textureDescriptorSetLayout,
        };
        this.fontsTextureSampler = new TextureSampler(this.device, 1);
        this.textureDescriptorSet = new TextureDescriptorSet(this.descriptorPool, this.textureDescriptorSetLayout, this.fontsTexture,
                this.fontsTextureSampler, 0);
    }

    private void createPipeline(PipelineCache pipelineCache, long vkRenderPass) {
        var pipeLineCreationInfo = new Pipeline.PipeLineCreationInfo(vkRenderPass,
                this.shaderProgram, 1, false, true, VkConstants.FLOAT_LENGTH * 2,
                new ImGuiVertexBufferStructure(), this.descriptorSetLayouts);
        this.pipeline = new Pipeline(pipelineCache, pipeLineCreationInfo);
        pipeLineCreationInfo.close();
    }

    private void createShaders() {
        var settings = Settings.getInstance();
        if (settings.isShaderRecompilation()) {
            ShaderCompiler.compileShaderIfChanged(GUI_VERTEX_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_vertex_shader);
            ShaderCompiler.compileShaderIfChanged(GUI_FRAGMENT_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_fragment_shader);
        }
        this.shaderProgram = new ShaderProgram(this.device, new ShaderProgram.ShaderModuleData[]
                {
                        new ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_VERTEX_BIT, GUI_VERTEX_SHADER_FILE_SPV),
                        new ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_FRAGMENT_BIT, GUI_FRAGMENT_SHADER_FILE_SPV),
                });
    }

    private void createUIResources(SwapChain swapChain, CommandPool commandPool, Queue queue) {
        ImGui.createContext();

        var imGuiIO = ImGui.getIO();
        imGuiIO.setIniFilename(null);
        var swapChainExtent = swapChain.getSwapChainExtent();
        imGuiIO.setDisplaySize(swapChainExtent.width(), swapChainExtent.height());
        imGuiIO.setDisplayFramebufferScale(1.0f, 1.0f);

        var texWidth = new ImInt();
        var texHeight = new ImInt();
        var buf = imGuiIO.getFonts().getTexDataAsRGBA32(texWidth, texHeight);
        this.fontsTexture = new Texture(this.device, buf, texWidth.get(), texHeight.get(), VK_FORMAT_R8G8B8A8_SRGB);

        var cmd = new CommandBuffer(commandPool, true, true);
        cmd.beginRecording();
        this.fontsTexture.recordTextureTransition(cmd);
        cmd.endRecording();
        cmd.submitAndWait(this.device, queue);
        cmd.close();

        this.vertexBuffers = new VulkanBuffer[swapChain.getNumImages()];
        this.indicesBuffers = new VulkanBuffer[swapChain.getNumImages()];

        var io = ImGui.getIO();
        io.setKeyMap(ImGuiKey.Tab, GLFW_KEY_TAB);
        io.setKeyMap(ImGuiKey.LeftArrow, GLFW_KEY_LEFT);
        io.setKeyMap(ImGuiKey.RightArrow, GLFW_KEY_RIGHT);
        io.setKeyMap(ImGuiKey.UpArrow, GLFW_KEY_UP);
        io.setKeyMap(ImGuiKey.DownArrow, GLFW_KEY_DOWN);
        io.setKeyMap(ImGuiKey.PageUp, GLFW_KEY_PAGE_UP);
        io.setKeyMap(ImGuiKey.PageDown, GLFW_KEY_PAGE_DOWN);
        io.setKeyMap(ImGuiKey.Home, GLFW_KEY_HOME);
        io.setKeyMap(ImGuiKey.End, GLFW_KEY_END);
        io.setKeyMap(ImGuiKey.Insert, GLFW_KEY_INSERT);
        io.setKeyMap(ImGuiKey.Delete, GLFW_KEY_DELETE);
        io.setKeyMap(ImGuiKey.Backspace, GLFW_KEY_BACKSPACE);
        io.setKeyMap(ImGuiKey.Space, GLFW_KEY_SPACE);
        io.setKeyMap(ImGuiKey.Enter, GLFW_KEY_ENTER);
        io.setKeyMap(ImGuiKey.Escape, GLFW_KEY_ESCAPE);
        io.setKeyMap(ImGuiKey.KeyPadEnter, GLFW_KEY_KP_ENTER);
    }

    public void recordCommandBuffer(Scene scene, CommandBuffer commandBuffer) {
        try (var stack = MemoryStack.stackPush()) {
            var idx = this.swapChain.getCurrentFrame();

            var guiInstance = scene.getGuiInstance();
            if (guiInstance == null) return;
            guiInstance.render();
            updateBuffers(idx);
            if (this.vertexBuffers[idx] == null) return;

            var swapChainExtent = this.swapChain.getSwapChainExtent();
            var width = swapChainExtent.width();
            var height = swapChainExtent.height();

            var cmdHandle = commandBuffer.getVkCommandBuffer();

            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, this.pipeline.getVkPipeline());

            var viewport = VkViewport.calloc(1, stack)
                    .x(0)
                    .y(height)
                    .height(-height)
                    .width(width)
                    .minDepth(0.0f)
                    .maxDepth(1.0f);
            vkCmdSetViewport(cmdHandle, 0, viewport);

            var vtxBuffer = stack.mallocLong(1);
            vtxBuffer.put(0, this.vertexBuffers[idx].getBuffer());
            var offsets = stack.mallocLong(1);
            offsets.put(0, 0L);
            vkCmdBindVertexBuffers(cmdHandle, 0, vtxBuffer, offsets);
            vkCmdBindIndexBuffer(cmdHandle, this.indicesBuffers[idx].getBuffer(), 0, VK_INDEX_TYPE_UINT16);

            var io = ImGui.getIO();
            var pushConstantBuffer = stack.mallocFloat(2);
            pushConstantBuffer.put(0, 2.0f / io.getDisplaySizeX());
            pushConstantBuffer.put(1, -2.0f / io.getDisplaySizeY());
            vkCmdPushConstants(cmdHandle, this.pipeline.getVkPipelineLayout(),
                    VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstantBuffer);

            var descriptorSets = stack.mallocLong(1)
                    .put(0, this.textureDescriptorSet.vk());
            vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS,
                    this.pipeline.getVkPipelineLayout(), 0, descriptorSets, null);

            var imVec4 = new ImVec4();
            var rect = VkRect2D.calloc(1, stack);
            var imDrawData = ImGui.getDrawData();
            var numCmdLists = imDrawData.getCmdListsCount();
            var offsetIdx = 0;
            var offsetVtx = 0;
            for (var i = 0; i < numCmdLists; i++) {
                var cmdBufferSize = imDrawData.getCmdListCmdBufferSize(i);
                for (var j = 0; j < cmdBufferSize; j++) {
                    imDrawData.getCmdListCmdBufferClipRect(i, j, imVec4);
                    rect.offset(it -> it.x((int) Math.max(imVec4.x, 0)).y((int) Math.max(imVec4.y, 1)));
                    rect.extent(it -> it.width((int) (imVec4.z - imVec4.x)).height((int) (imVec4.w - imVec4.y)));
                    vkCmdSetScissor(cmdHandle, 0, rect);
                    var numElements = imDrawData.getCmdListCmdBufferElemCount(i, j);
                    vkCmdDrawIndexed(cmdHandle, numElements, 1,
                            offsetIdx + imDrawData.getCmdListCmdBufferIdxOffset(i, j),
                            offsetVtx + imDrawData.getCmdListCmdBufferVtxOffset(i, j), 0);
                }
                offsetIdx += imDrawData.getCmdListIdxBufferSize(i);
                offsetVtx += imDrawData.getCmdListVtxBufferSize(i);
            }
        }
    }

    public void resize(SwapChain swapChain) {
        this.swapChain = swapChain;
        var imGuiIO = ImGui.getIO();
        var swapChainExtent = swapChain.getSwapChainExtent();
        imGuiIO.setDisplaySize(swapChainExtent.width(), swapChainExtent.height());
    }

    private void updateBuffers(int idx) {
        var imDrawData = ImGui.getDrawData();

        var vertexBufferSize = imDrawData.getTotalVtxCount() * ImGuiVertexBufferStructure.VERTEX_SIZE;
        var indexBufferSize = imDrawData.getTotalIdxCount() * VkConstants.SHORT_LENGTH;

        if (vertexBufferSize == 0 || indexBufferSize == 0) return;
        var vertexBuffer = this.vertexBuffers[idx];
        if (vertexBuffer == null || vertexBufferSize != vertexBuffer.getRequestedSize()) {
            if (vertexBuffer != null) vertexBuffer.close();
            vertexBuffer = new VulkanBuffer(this.device, vertexBufferSize, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0);
            this.vertexBuffers[idx] = vertexBuffer;
        }

        var indicesBuffer = this.indicesBuffers[idx];
        if (indicesBuffer == null || indexBufferSize != indicesBuffer.getRequestedSize()) {
            if (indicesBuffer != null) indicesBuffer.close();
            indicesBuffer = new VulkanBuffer(this.device, indexBufferSize, VK_BUFFER_USAGE_INDEX_BUFFER_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0);
            this.indicesBuffers[idx] = indicesBuffer;
        }

        var dstVertexBuffer = MemoryUtil.memByteBuffer(vertexBuffer.map(), vertexBufferSize);
        var dstIdxBuffer = MemoryUtil.memByteBuffer(indicesBuffer.map(), indexBufferSize);

        var numCmdLists = imDrawData.getCmdListsCount();
        for (var i = 0; i < numCmdLists; i++) {
            var imguiVertexBuffer = imDrawData.getCmdListVtxBufferData(i);
            dstVertexBuffer.put(imguiVertexBuffer);

            // Always get the indices buffer after finishing with the vertices buffer
            var imguiIndicesBuffer = imDrawData.getCmdListIdxBufferData(i);
            dstIdxBuffer.put(imguiIndicesBuffer);
        }

        vertexBuffer.flush();
        indicesBuffer.flush();

        vertexBuffer.unMap();
        indicesBuffer.unMap();
    }

    public static class CharCallBack implements GLFWCharCallbackI {
        @Override
        public void invoke(long windowHandle, int c) {
            var io = ImGui.getIO();
            if (!io.getWantCaptureKeyboard()) return;
            io.addInputCharacter(c);
        }
    }

    public static class KeyCallback implements GLFWKeyCallbackI {
        @Override
        public void invoke(long windowHandle, int key, int scancode, int action, int mods) {
            var io = ImGui.getIO();
            if (!io.getWantCaptureKeyboard()) return;
            if (action == GLFW_PRESS) io.setKeysDown(key, true);
            else if (action == GLFW_RELEASE) io.setKeysDown(key, false);
            io.setKeyCtrl(io.getKeysDown(GLFW_KEY_LEFT_CONTROL) || io.getKeysDown(GLFW_KEY_RIGHT_CONTROL));
            io.setKeyShift(io.getKeysDown(GLFW_KEY_LEFT_SHIFT) || io.getKeysDown(GLFW_KEY_RIGHT_SHIFT));
            io.setKeyAlt(io.getKeysDown(GLFW_KEY_LEFT_ALT) || io.getKeysDown(GLFW_KEY_RIGHT_ALT));
            io.setKeySuper(io.getKeysDown(GLFW_KEY_LEFT_SUPER) || io.getKeysDown(GLFW_KEY_RIGHT_SUPER));
        }
    }
}