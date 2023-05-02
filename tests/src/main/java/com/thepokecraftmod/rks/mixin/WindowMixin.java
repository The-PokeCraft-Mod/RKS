package com.thepokecraftmod.rks.mixin;

import com.mojang.blaze3d.platform.DisplayData;
import com.mojang.blaze3d.platform.ScreenManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.WindowEventHandler;
import com.thepokecraftmod.rks.CreeperReplacementTest;
import com.thepokecraftmod.rks.util.DebugWindow;
import org.lwjgl.opengl.GLUtil;
import org.lwjgl.opengl.KHRDebug;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.lwjgl.opengl.GL11C.glEnable;

@Mixin(Window.class)
public class WindowMixin {

    @Shadow @Final private static Logger LOGGER;

    @Shadow private int width;

    @Shadow private int height;

    @ModifyConstant(method = "<init>", constant = @Constant(intValue = 3))
    private int pokecraft$changeGlMajor(int constant) {
        LOGGER.warn("Enabling OpenGL 4.5+");
//        System.loadLibrary("renderdoc");
        return 4;
    }

    @ModifyConstant(method = "<init>", constant = @Constant(intValue = 2))
    private int pokecraft$changeGlMinor(int constant) {
        return 5;
    }

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwMakeContextCurrent(J)V"))
    private void interop$createDebugWindow(WindowEventHandler handler, ScreenManager screenManager, DisplayData data, String videoMode, String title, CallbackInfo ci) {
        new CreeperReplacementTest(width, height);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void interop$debugGl(WindowEventHandler windowEventHandler, ScreenManager screenManager, DisplayData displayData, String string, String string2, CallbackInfo ci) {
        GLUtil.setupDebugMessageCallback(System.err);
        glEnable(KHRDebug.GL_DEBUG_OUTPUT_SYNCHRONOUS);
    }
}
