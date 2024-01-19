package com.thepokecraftmod.renderer.impl;

import org.lwjgl.util.shaderc.Shaderc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * ImplDetailsWhichNeedMovingOut
 */
public class ImplUtils {

    public static byte[] get(String location) {
        try {
            return Files.readAllBytes(Paths.get("shaders/" + location));
        } catch (IOException e) {
            throw new RuntimeException("Failed to get shader", e);
        }
    }

    public static byte[] compileShader(String shaderCode, int shaderType) {
        long compiler = 0;
        long options = 0;
        byte[] compiledShader;

        try {
            compiler = Shaderc.shaderc_compiler_initialize();
            options = Shaderc.shaderc_compile_options_initialize();

            var result = Shaderc.shaderc_compile_into_spv(
                    compiler,
                    shaderCode,
                    shaderType,
                    "shader.glsl",
                    "main",
                    options
            );

            if (Shaderc.shaderc_result_get_compilation_status(result) != Shaderc.shaderc_compilation_status_success)
                throw new RuntimeException("Shader compilation failed: " + Shaderc.shaderc_result_get_error_message(result));

            var buffer = Shaderc.shaderc_result_get_bytes(result);
            compiledShader = new byte[Objects.requireNonNull(buffer).remaining()];
            buffer.get(compiledShader);
        } finally {
            Shaderc.shaderc_compile_options_release(options);
            Shaderc.shaderc_compiler_release(compiler);
        }

        return compiledShader;
    }

    public static void compileShaderIfChanged(String glsShaderFile, int shaderType) {
        try {
            Files.createDirectories(Paths.get("shaders"));
            var compiledFile = Paths.get("shaders/" + glsShaderFile + ".spv");

            if (!Files.exists(compiledFile)) {
                var compiledShader = compileShader(new String(Objects.requireNonNull(ImplUtils.class.getResourceAsStream("/shaders/" + glsShaderFile), glsShaderFile).readAllBytes(), StandardCharsets.UTF_8), shaderType);
                Files.write(compiledFile, compiledShader);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
