package org.vulkanb.eng.impl.geometry;

import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkSpecializationInfo;
import org.lwjgl.vulkan.VkSpecializationMapEntry;
import org.vulkanb.eng.Settings;
import org.vulkanb.eng.vk.VkConstants;

import java.nio.ByteBuffer;

public class GeometrySpecConstants {

    private final ByteBuffer data;
    private final VkSpecializationMapEntry.Buffer specEntryMap;
    private final VkSpecializationInfo specInfo;

    public GeometrySpecConstants() {
        var settings = Settings.getInstance();
        this.data = MemoryUtil.memAlloc(VkConstants.INT_LENGTH);
        this.data.putInt(settings.getMaxTextures());
        this.data.flip();

        this.specEntryMap = VkSpecializationMapEntry.calloc(1);
        this.specEntryMap.get(0)
                .constantID(0)
                .size(VkConstants.INT_LENGTH)
                .offset(0);

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
