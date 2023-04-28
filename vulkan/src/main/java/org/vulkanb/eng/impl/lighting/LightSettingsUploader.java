package org.vulkanb.eng.impl.lighting;

import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkSpecializationInfo;
import org.lwjgl.vulkan.VkSpecializationMapEntry;
import org.vulkanb.eng.Settings;
import org.vulkanb.eng.vk.GraphConstants;

import java.nio.ByteBuffer;

public class LightSettingsUploader {

    private final ByteBuffer data;
    private final VkSpecializationMapEntry.Buffer specEntryMap;
    private final VkSpecializationInfo specInfo;

    public LightSettingsUploader() {
        var settings = Settings.getInstance();
        this.data = MemoryUtil.memAlloc(GraphConstants.INT_LENGTH * 4 + GraphConstants.FLOAT_LENGTH);
        this.data.putInt(GraphConstants.MAX_LIGHTS);
        this.data.putInt(GraphConstants.SHADOW_MAP_CASCADE_COUNT);
        this.data.putInt(1);
        this.data.putFloat(settings.getShadowBias());
        this.data.putInt(settings.isShadowDebug() ? 1 : 0);
        this.data.flip();

        this.specEntryMap = VkSpecializationMapEntry.calloc(5);
        this.specEntryMap.get(0)
                .constantID(0)
                .size(GraphConstants.INT_LENGTH)
                .offset(0);
        this.specEntryMap.get(1)
                .constantID(1)
                .size(GraphConstants.INT_LENGTH)
                .offset(GraphConstants.INT_LENGTH);
        this.specEntryMap.get(2)
                .constantID(2)
                .size(GraphConstants.INT_LENGTH)
                .offset(GraphConstants.INT_LENGTH * 2);
        this.specEntryMap.get(3)
                .constantID(3)
                .size(GraphConstants.FLOAT_LENGTH)
                .offset(GraphConstants.INT_LENGTH * 3);
        this.specEntryMap.get(4)
                .constantID(4)
                .size(GraphConstants.INT_LENGTH)
                .offset(GraphConstants.INT_LENGTH * 3 + GraphConstants.FLOAT_LENGTH);

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
