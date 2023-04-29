package com.thepokecraftmod.renderer.impl.lighting;

import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkSpecializationInfo;
import org.lwjgl.vulkan.VkSpecializationMapEntry;
import com.thepokecraftmod.renderer.Settings;
import com.thepokecraftmod.renderer.vk.VkConstants;

import java.nio.ByteBuffer;

public class LightSettingsUploader {

    private final ByteBuffer data;
    private final VkSpecializationMapEntry.Buffer specEntryMap;
    private final VkSpecializationInfo specInfo;

    public LightSettingsUploader() {
        var settings = Settings.getInstance();
        this.data = MemoryUtil.memAlloc(VkConstants.INT_LENGTH * 4 + VkConstants.FLOAT_LENGTH);
        this.data.putInt(VkConstants.MAX_LIGHTS);
        this.data.putInt(VkConstants.SHADOW_MAP_CASCADE_COUNT);
        this.data.putInt(1);
        this.data.putFloat(settings.getShadowBias());
        this.data.putInt(settings.isShadowDebug() ? 1 : 0);
        this.data.flip();

        this.specEntryMap = VkSpecializationMapEntry.calloc(5);
        this.specEntryMap.get(0)
                .constantID(0)
                .size(VkConstants.INT_LENGTH)
                .offset(0);
        this.specEntryMap.get(1)
                .constantID(1)
                .size(VkConstants.INT_LENGTH)
                .offset(VkConstants.INT_LENGTH);
        this.specEntryMap.get(2)
                .constantID(2)
                .size(VkConstants.INT_LENGTH)
                .offset(VkConstants.INT_LENGTH * 2);
        this.specEntryMap.get(3)
                .constantID(3)
                .size(VkConstants.FLOAT_LENGTH)
                .offset(VkConstants.INT_LENGTH * 3);
        this.specEntryMap.get(4)
                .constantID(4)
                .size(VkConstants.INT_LENGTH)
                .offset(VkConstants.INT_LENGTH * 3 + VkConstants.FLOAT_LENGTH);

        this.specInfo = VkSpecializationInfo.calloc();
        this.specInfo.pData(this.data)
                .pMapEntries(this.specEntryMap);
    }

    public void close() {
        MemoryUtil.memFree(this.specEntryMap);
        this.specInfo.free();
        MemoryUtil.memFree(this.data);
    }

    public VkSpecializationInfo getSpecInfo() {
        return this.specInfo;
    }
}
