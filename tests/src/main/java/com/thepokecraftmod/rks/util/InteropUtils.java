package com.thepokecraftmod.rks.util;

import com.thepokecraftmod.renderer.vk.Image;
import com.thepokecraftmod.renderer.vk.ImageView;
import com.thepokecraftmod.renderer.vk.VulkanBuffer;
import com.thepokecraftmod.renderer.vk.init.Device;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocationInfo;
import org.lwjgl.vulkan.*;

import java.awt.image.BufferedImage;
import java.io.Closeable;

import static org.lwjgl.vulkan.VK10.vkGetBufferMemoryRequirements;

public class InteropUtils {
/*    private static final boolean IS_WIN32 = true;

    public static class BufferVkGL implements Closeable {
        public VulkanBuffer bufVk;
        public long win32Handle = -1;
        public int fdHandle = -1;
        public int glMemoryObject;
        public int glObjectId;

        @Override
        public void close() {
            if (win32Handle != -1) {
                // TODO: close win32
            } else if (fdHandle != -1) {
                // TODO: close other
            }

            GL15C.glDeleteBuffers(glObjectId);
            EXTMemoryObject.glDeleteMemoryObjectsEXT(glMemoryObject);
        }
    }

    public static class Texture2DVkGL implements Closeable {
        public Image image;
        public int mipLevels = 1;
        public long win32Handle = -1;
        public long fd = -1;
        public int glMemoryObject;
        public int glObjectId;


        @Override
        public void close() {
            if (win32Handle != -1) {
                // TODO: close win32
            } else if (fd != -1) {
                // TODO: close other
            }

            GL15C.glDeleteBuffers(glObjectId);
            EXTMemoryObject.glDeleteMemoryObjectsEXT(glMemoryObject);
        }
    }

    public void createBufferGl(Device device, BufferVkGL bufGl) {
        try (var stack = MemoryStack.stackPush()) {
            var allocInfo = getBufferMemoryInfo(device, stack, bufGl.bufVk);

            if (IS_WIN32) {
                var pHandle = stack.mallocPointer(1);
                var handleInfo = VkMemoryGetWin32HandleInfoKHR.calloc(stack)
                        .handleType(VK11.VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_WIN32_BIT)
                        .memory(allocInfo.deviceMemory());
                KHRExternalMemoryWin32.vkGetMemoryWin32HandleKHR(device.vk(), handleInfo, pHandle);
                bufGl.win32Handle = pHandle.get();
            } else {
                var pHandle = stack.mallocInt(1);
                var handleInfo = VkMemoryGetFdInfoKHR.calloc(stack)
                        .handleType(VK11.VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_FD_BIT)
                        .memory(allocInfo.deviceMemory());
                KHRExternalMemoryFd.vkGetMemoryFdKHR(device.vk(), handleInfo, pHandle);
                bufGl.fdHandle = pHandle.get();
            }

            var requirements = VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(device.vk(), bufGl.bufVk.getBuffer(), requirements);
            bufGl.glObjectId = GL45C.glCreateBuffers();
            bufGl.glMemoryObject = EXTMemoryObject.glCreateMemoryObjectsEXT();

            if (IS_WIN32)
                EXTMemoryObjectWin32.glImportMemoryWin32HandleEXT(bufGl.glMemoryObject, requirements.size(), EXTSemaphoreWin32.GL_HANDLE_TYPE_OPAQUE_WIN32_EXT, bufGl.win32Handle);
            else {
                EXTMemoryObjectFD.glImportMemoryFdEXT(bufGl.glMemoryObject, requirements.size(), EXTSemaphoreFD.GL_HANDLE_TYPE_OPAQUE_FD_EXT, bufGl.fdHandle);
                bufGl.fdHandle = -1; // fd got consumed
            }

            EXTMemoryObject.glNamedBufferStorageMemEXT(bufGl.glObjectId, requirements.size(), bufGl.glMemoryObject, allocInfo.offset());
        }
    }

    public void createTextureGL(Device device, Texture2DVkGL texGl, int format, int minFilter, int magFilter, int wrap) {
        getBufferMemoryInfo(texGl.image)
    }

    private VmaAllocationInfo getBufferMemoryInfo(Device device, MemoryStack stack, VulkanBuffer bufVk) {
        var allocInfo = VmaAllocationInfo.calloc(stack);
        Vma.vmaGetAllocationInfo(device.memoryAllocator.vma(), bufVk.allocation, allocInfo);
        return allocInfo;
    }*/
}
