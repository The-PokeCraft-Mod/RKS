package com.thepokecraftmod.rks.model;

import com.thepokecraftmod.rks.model.bone.Bone;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.List;

public record Mesh(
        String name,
        int material,
        List<Integer> indices,
        List<Vector3f> positions,
        List<Vector2f> uvs,
        List<Vector3f> normals,
        List<Vector3f> tangents,
        List<Vector3f> biTangents,
        List<Bone> bones
) {}
