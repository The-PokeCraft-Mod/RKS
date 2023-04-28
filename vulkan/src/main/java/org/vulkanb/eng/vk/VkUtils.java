package org.vulkanb.eng.vk;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkCommandBuffer;

import static org.lwjgl.vulkan.VK11.*;

public class VkUtils {

    private VkUtils() {
        // Utility class
    }

    public static void copyMatrixToBuffer(VulkanBuffer vulkanBuffer, Matrix4f matrix) {
        copyMatrixToBuffer(vulkanBuffer, matrix, 0);
    }

    public static void copyMatrixToBuffer(VulkanBuffer vulkanBuffer, Matrix4f matrix, int offset) {
        var mappedMemory = vulkanBuffer.map();
        var matrixBuffer = MemoryUtil.memByteBuffer(mappedMemory, (int) vulkanBuffer.getRequestedSize());
        matrix.get(offset, matrixBuffer);
        vulkanBuffer.unMap();
    }

    public static int memoryTypeFromProperties(PhysicalDevice physDevice, int typeBits, int reqsMask) {
        var result = -1;
        var memoryTypes = physDevice.getVkMemoryProperties().memoryTypes();
        for (var i = 0; i < VK_MAX_MEMORY_TYPES; i++) {
            if ((typeBits & 1) == 1 && (memoryTypes.get(i).propertyFlags() & reqsMask) == reqsMask) {
                result = i;
                break;
            }
            typeBits >>= 1;
        }
        if (result < 0) throw new RuntimeException("Failed to find memoryType");
        return result;
    }

    public static void setMatrixAsPushConstant(Pipeline pipeLine, VkCommandBuffer cmdHandle, Matrix4f matrix) {
        try (var stack = MemoryStack.stackPush()) {
            var pushConstantBuffer = stack.malloc(GraphConstants.MAT4X4_SIZE);
            matrix.get(0, pushConstantBuffer);
            vkCmdPushConstants(cmdHandle, pipeLine.getVkPipelineLayout(),
                    VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstantBuffer);
        }
    }

    public static void ok(int err, String errMsg) {
        if (err != VK_SUCCESS) throw new RuntimeException(errMsg + ": " + err);
    }
}
