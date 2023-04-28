package org.vulkanb.eng.impl.shadows;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.vulkanb.eng.scene.Scene;
import org.vulkanb.eng.vk.GraphConstants;

import java.util.List;

public class CascadeShadow {

    private Matrix4f projViewMatrix;
    private float splitDistance;

    public CascadeShadow() {
        this.projViewMatrix = new Matrix4f();
    }

    // Function are derived from Vulkan examples from Sascha Willems, and licensed under the MIT License:
    // https://github.com/SaschaWillems/Vulkan/tree/master/examples/shadowmappingcascade, which are based on
    // https://johanmedestrom.wordpress.com/2016/03/18/opengl-cascaded-shadow-maps/
    public static void updateCascadeShadows(List<CascadeShadow> cascadeShadows, Scene scene) {
        var viewMatrix = scene.getCamera().getViewMatrix();
        var projMatrix = scene.getProjection().getProjectionMatrix();
        var lightPos = scene.getDirectionalLight().getPosition();

        var cascadeSplitLambda = 0.95f;

        var cascadeSplits = new float[GraphConstants.SHADOW_MAP_CASCADE_COUNT];

        var nearClip = projMatrix.perspectiveNear();
        var farClip = projMatrix.perspectiveFar();
        var clipRange = farClip - nearClip;

        var maxZ = nearClip + clipRange;

        var range = maxZ - nearClip;
        var ratio = maxZ / nearClip;

        // Calculate split depths based on view camera frustum
        // Based on method presented in https://developer.nvidia.com/gpugems/GPUGems3/gpugems3_ch10.html
        for (var i = 0; i < GraphConstants.SHADOW_MAP_CASCADE_COUNT; i++) {
            var p = (i + 1) / (float) (GraphConstants.SHADOW_MAP_CASCADE_COUNT);
            var log = (float) (nearClip * java.lang.Math.pow(ratio, p));
            var uniform = nearClip + range * p;
            var d = cascadeSplitLambda * (log - uniform) + uniform;
            cascadeSplits[i] = (d - nearClip) / clipRange;
        }

        // Calculate orthographic projection matrix for each cascade
        var lastSplitDist = 0.0f;
        for (var i = 0; i < GraphConstants.SHADOW_MAP_CASCADE_COUNT; i++) {
            var splitDist = cascadeSplits[i];

            var frustumCorners = new Vector3f[]{
                    new Vector3f(-1.0f, 1.0f, -1.0f),
                    new Vector3f(1.0f, 1.0f, -1.0f),
                    new Vector3f(1.0f, -1.0f, -1.0f),
                    new Vector3f(-1.0f, -1.0f, -1.0f),
                    new Vector3f(-1.0f, 1.0f, 1.0f),
                    new Vector3f(1.0f, 1.0f, 1.0f),
                    new Vector3f(1.0f, -1.0f, 1.0f),
                    new Vector3f(-1.0f, -1.0f, 1.0f),
            };

            // Project frustum corners into world space
            var invCam = (new Matrix4f(projMatrix).mul(viewMatrix)).invert();
            for (var j = 0; j < 8; j++) {
                var invCorner = new Vector4f(frustumCorners[j], 1.0f).mul(invCam);
                frustumCorners[j] = new Vector3f(invCorner.x / invCorner.w, invCorner.y / invCorner.w, invCorner.z / invCorner.w);
            }

            for (var j = 0; j < 4; j++) {
                var dist = new Vector3f(frustumCorners[j + 4]).sub(frustumCorners[j]);
                frustumCorners[j + 4] = new Vector3f(frustumCorners[j]).add(new Vector3f(dist).mul(splitDist));
                frustumCorners[j] = new Vector3f(frustumCorners[j]).add(new Vector3f(dist).mul(lastSplitDist));
            }

            // Get frustum center
            var frustumCenter = new Vector3f(0.0f);
            for (var j = 0; j < 8; j++) frustumCenter.add(frustumCorners[j]);
            frustumCenter.div(8.0f);

            var radius = 0.0f;
            for (var j = 0; j < 8; j++) {
                var distance = (new Vector3f(frustumCorners[j]).sub(frustumCenter)).length();
                radius = java.lang.Math.max(radius, distance);
            }
            radius = (float) java.lang.Math.ceil(radius * 16.0f) / 16.0f;

            var maxExtents = new Vector3f(radius);
            var minExtents = new Vector3f(maxExtents).mul(-1);

            var lightDir = (new Vector3f(lightPos.x, lightPos.y, lightPos.z).mul(-1)).normalize();
            var eye = new Vector3f(frustumCenter).sub(new Vector3f(lightDir).mul(-minExtents.z));
            var up = new Vector3f(0.0f, 1.0f, 0.0f);
            var lightViewMatrix = new Matrix4f().lookAt(eye, frustumCenter, up);
            var lightOrthoMatrix = new Matrix4f().ortho
                    (minExtents.x, maxExtents.x, minExtents.y, maxExtents.y, 0.0f, maxExtents.z - minExtents.z, true);

            // Store split distance and matrix in cascade
            var cascadeShadow = cascadeShadows.get(i);
            cascadeShadow.splitDistance = (nearClip + splitDist * clipRange) * -1.0f;
            cascadeShadow.projViewMatrix = lightOrthoMatrix.mul(lightViewMatrix);

            lastSplitDist = cascadeSplits[i];
        }
    }

    public Matrix4f getProjViewMatrix() {
        return this.projViewMatrix;
    }

    public float getSplitDistance() {
        return this.splitDistance;
    }

}
