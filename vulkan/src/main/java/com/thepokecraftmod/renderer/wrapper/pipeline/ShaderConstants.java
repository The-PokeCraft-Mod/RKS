package com.thepokecraftmod.renderer.wrapper.pipeline;

import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkSpecializationInfo;
import org.lwjgl.vulkan.VkSpecializationMapEntry;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ShaderConstants implements Closeable {

    private final ByteBuffer data;
    private final VkSpecializationMapEntry.Buffer specEntryMap;
    public final VkSpecializationInfo specInfo;

    private ShaderConstants(ByteBuffer data, VkSpecializationMapEntry.Buffer specEntryMap, VkSpecializationInfo specInfo) {
        this.data = data;
        this.specEntryMap = specEntryMap;
        this.specInfo = specInfo;
    }

    @Override
    public void close() {
        MemoryUtil.memFree(specEntryMap);
        specInfo.free();
        MemoryUtil.memFree(data);
    }

    public static class Builder {

        private final List<MapEntry> entries = new ArrayList<>();

        public Builder entry(int size, Consumer<ByteBuffer> writer) {
            entries.add(new MapEntry(size, writer));
            return this;
        }

        public ShaderConstants build() {
            var size = 0;
            for (var value : entries) size += value.size;

            var data = MemoryUtil.memAlloc(size);
            for (var value : entries) value.dataWriter.accept(data);
            data.flip();

            var entries = new ArrayList<>(this.entries);
            var specEntryMap = VkSpecializationMapEntry.calloc(entries.size());
            var offset = 0;

            for (var i = 0; i < entries.size(); i++) {
                var entry = entries.get(i);
                specEntryMap.get(i)
                        .constantID(i)
                        .size(entry.size)
                        .offset(offset);
                offset += entry.size;
            }

            var specInfo = VkSpecializationInfo.calloc()
                    .pData(data)
                    .pMapEntries(specEntryMap);

            return new ShaderConstants(data, specEntryMap, specInfo);
        }

        public record MapEntry(
                int size,
                Consumer<ByteBuffer> dataWriter
        ) {}
    }
}
