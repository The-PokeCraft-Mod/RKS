package com.thepokecraftmod.rks.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.thepokecraftmod.rks.CreeperReplacementTest;
import net.minecraft.client.model.CreeperModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.CreeperRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Creeper;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(CreeperRenderer.class)
public abstract class CreeperRendererMixin extends MobRenderer<Creeper, CreeperModel<Creeper>> {

    public CreeperRendererMixin(EntityRendererProvider.Context context, CreeperModel<Creeper> entityModel, float f) {
        super(context, entityModel, f);
    }

    @Override
    public void render(@NotNull Creeper entity, float entityYaw, float partialTicks, @NotNull PoseStack modelViewStack, @NotNull MultiBufferSource buffer, int packedLight) {
        super.render(entity, entityYaw, partialTicks, modelViewStack, buffer, packedLight);
        modelViewStack.pushPose();
        modelViewStack.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot)));
        modelViewStack.mulPose(Axis.XP.rotationDegrees(-90));
        var transform = modelViewStack.last().pose();
        CreeperReplacementTest.INSTANCE.rayquaza.modelMatrix.set(transform);
        modelViewStack.popPose();
    }
}