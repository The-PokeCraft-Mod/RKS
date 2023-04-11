package com.thepokecraftmod.rks.scene;

import com.thepokecraftmod.rks.model.animation.Animation;
import com.thepokecraftmod.rks.pipeline.Shader;

import java.util.Map;

public class AnimatedMeshObject extends MeshObject {

    public Map<String, Animation> animations;

    public AnimatedMeshObject(String materialReference) {
        super(materialReference);
    }

    public void setup(Shader shader, Map<String, Animation> animations) {
        this.shader = shader;
        this.animations = animations;
    }
}
