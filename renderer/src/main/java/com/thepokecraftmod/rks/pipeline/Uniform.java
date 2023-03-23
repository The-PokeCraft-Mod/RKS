package com.thepokecraftmod.rks.pipeline;

import com.pokemod.rarecandy.loading.Texture;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

/**
 * @deprecated The use of block uniforms is recommended. This will be removed at a later date.
 */
@Deprecated
public class Uniform {
    private static final FloatBuffer MAT4_TRANSFER_BUFFER = MemoryUtil.memAllocFloat(16);
    private static final FloatBuffer VEC3_TRANSFER_BUFFER = MemoryUtil.memAllocFloat(3);
    private static final FloatBuffer VEC4_TRANSFER_BUFFER = MemoryUtil.memAllocFloat(4);
    public final int type;
    public final int count;
    private final int[] locations;

    public Uniform(int program, String name, int type, int count) {
        this.type = type;
        this.count = count;
        this.locations = new int[count];

        if (count > 1) {
            for (int i = 0; i < count; i++) {
                locations[i] = GL20C.glGetUniformLocation(program, name + "[" + i + "]");
            }
        } else {
            locations[0] = GL20C.glGetUniformLocation(program, name);
        }
    }

    public void uploadMat4f(Matrix4f value) {
        value.get(MAT4_TRANSFER_BUFFER);
        GL20C.glUniformMatrix4fv(getLocation(), false, MAT4_TRANSFER_BUFFER);
    }

    public void uploadMat4fs(Matrix4f[] values) {
        for (var i = 0; i < values.length; i++) {
            if (values[i] == null)
                throw new RuntimeException("Matrix4f at index " + i + " is null. If you are passing an animation, Is it the right animation for this model?");
            values[i].get(MAT4_TRANSFER_BUFFER);
            GL20C.glUniformMatrix4fv(getArrayLocation(i), false, MAT4_TRANSFER_BUFFER);
        }
    }

    public void uploadVec3fs(Vector3f[] values) {
        for (var i = 0; i < values.length; i++) {
            if (values[i] == null) throw new RuntimeException("Vector3f at index " + i + " is null.");
            values[i].get(VEC3_TRANSFER_BUFFER);
            GL20C.glUniform3fv(getArrayLocation(i), VEC3_TRANSFER_BUFFER);
        }
    }

    public void uploadVec3f(Vector3f value) {
        value.get(VEC3_TRANSFER_BUFFER);
        GL20C.glUniform3fv(getLocation(), VEC3_TRANSFER_BUFFER);
    }

    public void uploadInt(int value) {
        GL20C.glUniform1i(getLocation(), value);
    }

    public void uploadBoolean(boolean value) {
        GL20C.glUniform1i(getLocation(), value ? 1 : 0);
    }

    public void uploadFloat(float value) {
        GL20C.glUniform1f(getLocation(), value);
    }

    private int getArrayLocation(int offset) {
        if (offset > locations.length) {
            throw new RuntimeException("Tried to get a uniform location for a place outside of the array. Array length is " + locations.length + ", But we got " + offset);
        }

        return locations[offset];
    }

    private int getLocation() {
        if (locations.length > 1) {
            throw new RuntimeException("Tried to get single uniform location when the Uniform is an array?");
        }

        return locations[0];
    }

    public void uploadTexture(Texture texture, int slot) {
        texture.bind(slot);
        uploadInt(slot);
    }

    public void uploadVec4f(Vector4f value) {
        value.get(VEC4_TRANSFER_BUFFER);
        GL20C.glUniform4fv(getLocation(), VEC4_TRANSFER_BUFFER);
    }
}
