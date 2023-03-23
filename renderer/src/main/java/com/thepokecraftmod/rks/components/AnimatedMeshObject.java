package com.thepokecraftmod.rks.components;

import com.pokemod.rarecandy.animation.Animation;
import com.pokemod.rarecandy.model.GLModel;
import com.pokemod.rarecandy.model.Material;
import com.pokemod.rarecandy.pipeline.ShaderPipeline;

import java.util.List;
import java.util.Map;

public class AnimatedMeshObject extends MeshObject {
    public Map<String, Animation> animations;

    public void setup(List<Material> glMaterials, Map<String, Material> variants, GLModel model, ShaderPipeline shaderPipeline, Map<String, Animation> animations) {
        this.materials = glMaterials;
        this.variants = variants;
        this.model = model;
        this.shaderPipeline = shaderPipeline;
        this.animations = animations;
        this.ready = true;
    }
}
