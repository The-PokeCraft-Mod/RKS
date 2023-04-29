package com.thepokecraftmod.renderer.vk.manager;

import com.thepokecraftmod.renderer.vk.Device;
import com.thepokecraftmod.renderer.vk.descriptor.DescriptorPool;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;

public class PoolManager implements Closeable {
    private static final int OBJECTS_PER_POOL = 6;
    public final List<ManagedPool> pools = new ArrayList<>();
    private final PoolCreationInfo creationInfo;

    public PoolManager(Device device, List<DescriptorPool.DescriptorTypeCount> descriptorTypeCounts) {
        this.creationInfo = new PoolCreationInfo(device, descriptorTypeCounts);
    }

    public DescriptorPool getPool() {
        for (var pool : pools) if (!pool.full) return pool.vk;
        pools.add(new ManagedPool(new DescriptorPool(creationInfo.device(), creationInfo.descriptorTypeCounts(), OBJECTS_PER_POOL)));
        return getPool();
    }

    @Override
    public void close() {
        for (var pool : pools) pool.vk.close();
    }

    public record PoolCreationInfo(
            Device device,
            List<DescriptorPool.DescriptorTypeCount> descriptorTypeCounts
    ) {}

    private static class ManagedPool {

        public final DescriptorPool vk;
        public boolean full;

        public ManagedPool(DescriptorPool vk) {
            this.vk = vk;
        }
    }
}
