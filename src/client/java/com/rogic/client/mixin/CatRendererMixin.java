package com.rogic.client.mixin;

import com.rogic.client.ClientMemeState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.CatRenderer;
import net.minecraft.world.entity.animal.Cat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 经典管线（1.21.0/1.21.1）：无 extractRenderState / CatRenderState，放大改在 render() 用 PoseStack.scale。
 * 状态按 cat.getId() 从 ClientMemeState 读（与 1.21.11 同一数据源，服务端仍发同样的包）。
 *
 * 在 render 的 HEAD 推入一个放大 1.25x 的 PoseStack、TAIL 弹出，整个渲染（模型+阴影）随之放大，
 * 仅渲染不改碰撞箱。try/catch 兜底防黑屏。
 */
@Mixin(CatRenderer.class)
public class CatRendererMixin {

	@Inject(method = "render(Lnet/minecraft/world/entity/animal/Cat;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"))
	private void laowuScalePush(Cat cat, float f, float g, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
		if (ClientMemeState.get().isActive(cat.getId())) {
			poseStack.pushPose();
			poseStack.scale(1.25f, 1.25f, 1.25f);
		}
	}

	@Inject(method = "render(Lnet/minecraft/world/entity/animal/Cat;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("TAIL"))
	private void laowuScalePop(Cat cat, float f, float g, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
		if (ClientMemeState.get().isActive(cat.getId())) {
			poseStack.popPose();
		}
	}
}
