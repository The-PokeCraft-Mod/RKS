package com.thepokecraftmod.rks.model.extra;

public enum TextureFilter {
    NEAREST(0x2600),
    LINEAR(0x2601);

    public final int glId;

    TextureFilter(int glId) {
        this.glId = glId;
    }
}
