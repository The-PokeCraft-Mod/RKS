package com.thepokecraftmod.rks.util;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;
import com.thepokecraftmod.renderer.vk.Image;
import com.thepokecraftmod.renderer.vk.VulkanBuffer;
import com.thepokecraftmod.renderer.vk.init.Device;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocationInfo;
import org.lwjgl.vulkan.*;

import java.io.Closeable;

import static com.thepokecraftmod.renderer.vk.VkUtils.ok;
import static org.lwjgl.vulkan.VK10.vkGetBufferMemoryRequirements;
import static org.lwjgl.vulkan.VK11.VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT;
import static org.lwjgl.vulkan.VK11.VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_FD_BIT;

public class InteropUtils {
    private static final boolean IS_WIN32 = true;

    public static class BufferVkGL implements Closeable {
        public VulkanBuffer bufVk;
        public long win32Handle = -1;
        public int fdHandle = -1;
        public int glMemoryObject;
        public int glObjectId;

        @Override
        public void close() {
            if (win32Handle != -1) Kernel32.INSTANCE.CloseHandle(new WinNT.HANDLE(new Pointer(win32Handle)));
            GL15C.glDeleteBuffers(glObjectId);
            EXTMemoryObject.glDeleteMemoryObjectsEXT(glMemoryObject);
        }
    }

    public static class Texture2DVkGL implements Closeable {
        public Image image;
        public int mipLevels;
        public long win32Handle = -1;
        public int fdHandle = -1;
        public int glMemoryObject;
        public int glObjectId;

        public Texture2DVkGL(Device device, int width, int height, int format, int usage, int properties, int mipLevels) {
            this.image = new Image(
                    device,
                    new Image.ImageData()
                            .format(format)
                            .usage(usage)
                            .mipLevels(mipLevels)
                            .width(width)
                            .height(height)
                            .properties(properties)
                            .pNext(stack -> {
                                var extra = VkExternalMemoryImageCreateInfo.calloc(stack)
                                        .sType$Default()
                                        .handleTypes(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT);

                                return extra.address();
                            })
            );
            this.mipLevels = mipLevels;
        }

        @Override
        public void close() {
            if (win32Handle != -1) Kernel32.INSTANCE.CloseHandle(new WinNT.HANDLE(new Pointer(win32Handle)));
            GL15C.glDeleteBuffers(glObjectId);
            EXTMemoryObject.glDeleteMemoryObjectsEXT(glMemoryObject);
        }
    }

    public static void createBufferGl(Device device, BufferVkGL bufGl) {
        try (var stack = MemoryStack.stackPush()) {
            var allocInfo = getBufferMemoryInfo(device, stack, bufGl.bufVk.allocation);

            if (IS_WIN32) {
                var pHandle = stack.mallocPointer(1);
                var handleInfo = VkMemoryGetWin32HandleInfoKHR.calloc(stack)
                        .sType$Default()
                        .handleType(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT)
                        .memory(allocInfo.deviceMemory());
                KHRExternalMemoryWin32.vkGetMemoryWin32HandleKHR(device.vk(), handleInfo, pHandle);
                bufGl.win32Handle = pHandle.get();
            } else {
                var pHandle = stack.mallocInt(1);
                var handleInfo = VkMemoryGetFdInfoKHR.calloc(stack)
                        .sType$Default()
                        .handleType(VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_FD_BIT)
                        .memory(allocInfo.deviceMemory());
                KHRExternalMemoryFd.vkGetMemoryFdKHR(device.vk(), handleInfo, pHandle);
                bufGl.fdHandle = pHandle.get();
            }

            var requirements = VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(device.vk(), bufGl.bufVk.getBuffer(), requirements);
            bufGl.glObjectId = GL45C.glCreateBuffers();
            bufGl.glMemoryObject = EXTMemoryObject.glCreateMemoryObjectsEXT();

            if (IS_WIN32)
                EXTMemoryObjectWin32.glImportMemoryWin32HandleEXT(bufGl.glMemoryObject, requirements.size() + allocInfo.offset(), EXTSemaphoreWin32.GL_HANDLE_TYPE_OPAQUE_WIN32_EXT, bufGl.win32Handle);
            else {
                EXTMemoryObjectFD.glImportMemoryFdEXT(bufGl.glMemoryObject, requirements.size(), EXTSemaphoreFD.GL_HANDLE_TYPE_OPAQUE_FD_EXT, bufGl.fdHandle);
                bufGl.fdHandle = -1; // fd got consumed
            }

            EXTMemoryObject.glNamedBufferStorageMemEXT(bufGl.glObjectId, requirements.size(), bufGl.glMemoryObject, allocInfo.offset());
        }
    }

    public static void createGlTexture(Device device, Texture2DVkGL texGl, int format, int minFilter, int magFilter, int wrap, int width, int height) {
        try (var stack = MemoryStack.stackPush()) {
            var allocInfo = getBufferMemoryInfo(device, stack, texGl.image.allocation);

            // TODO: fix duplication from above method
            if (IS_WIN32) {
                var pHandle = stack.mallocPointer(1);
                var handleInfo = VkMemoryGetWin32HandleInfoKHR.calloc(stack)
                        .sType$Default()
                        .handleType(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT)
                        .memory(allocInfo.deviceMemory());
                ok(KHRExternalMemoryWin32.vkGetMemoryWin32HandleKHR(device.vk(), handleInfo, pHandle), "Failed to get win32 memory handle");
                texGl.win32Handle = pHandle.get(0);
                if (texGl.win32Handle == 0) throw new IllegalStateException();
            } else {
                var pHandle = stack.mallocInt(1);
                var handleInfo = VkMemoryGetFdInfoKHR.calloc(stack)
                        .sType$Default()
                        .handleType(VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_FD_BIT)
                        .memory(allocInfo.deviceMemory());
                KHRExternalMemoryFd.vkGetMemoryFdKHR(device.vk(), handleInfo, pHandle);
                texGl.fdHandle = pHandle.get();
            }

            var requirements = VkMemoryRequirements.calloc(stack);
            VK10.vkGetImageMemoryRequirements(device.vk(), texGl.image.vk(), requirements);

            // Create a 'memory object' in OpenGL, and associate it with the memory allocated in Vulkan
            texGl.glMemoryObject = EXTMemoryObject.glCreateMemoryObjectsEXT();

            // TODO: fix duplication from above method
            if (IS_WIN32)
                EXTMemoryObjectWin32.glImportMemoryWin32HandleEXT(texGl.glMemoryObject, requirements.size() + allocInfo.offset(), EXTSemaphoreWin32.GL_HANDLE_TYPE_OPAQUE_WIN32_EXT, texGl.win32Handle);
            else {
                EXTMemoryObjectFD.glImportMemoryFdEXT(texGl.glMemoryObject, requirements.size(), EXTSemaphoreFD.GL_HANDLE_TYPE_OPAQUE_FD_EXT, texGl.fdHandle);
                texGl.fdHandle = -1; // fd got consumed
            }

            texGl.glObjectId = GL45C.glCreateTextures(GL20C.GL_TEXTURE_2D);
            EXTMemoryObject.glTextureStorageMem2DEXT(texGl.glMemoryObject, texGl.mipLevels, format, width, height, texGl.glMemoryObject, allocInfo.offset());
            GL45C.glTextureParameteri(texGl.glObjectId, GL11C.GL_TEXTURE_MIN_FILTER, minFilter);
            GL45C.glTextureParameteri(texGl.glObjectId, GL11C.GL_TEXTURE_MAG_FILTER, magFilter);
            GL45C.glTextureParameteri(texGl.glObjectId, GL11C.GL_TEXTURE_WRAP_S, wrap);
            GL45C.glTextureParameteri(texGl.glObjectId, GL11C.GL_TEXTURE_WRAP_T, wrap);
        }
    }

    private static VmaAllocationInfo getBufferMemoryInfo(Device device, MemoryStack stack, long allocation) {
        var allocInfo = VmaAllocationInfo.calloc(stack);
        Vma.vmaGetAllocationInfo(device.memoryAllocator.vma(), allocation, allocInfo);
        return allocInfo;
    }
}
