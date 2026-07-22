package com.rogic.client.mixin;

import com.rogic.client.render.RenderStateHolder;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.CatRenderState;
import net.minecraft.client.renderer.entity.state.FelineRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在猫模型 setupAnim 后，给锁定猫的头部加一个 zRot（歪头杀 roll）。
 * 歪头方向从 RenderStateHolder 读取（由 CatRendererMixin 写入）。
 * 同时作用于成年猫(AdultCatModel)与幼猫(BabyCatModel)。
 */
@Mixin({net.minecraft.client.model.animal.feline.AdultCatModel.class, net.minecraft.client.model.animal.feline.BabyCatModel.class})
public abstract class CatModelMixin {
	@Shadow protected ModelPart root;

	/** 歪头角度：45°（设计稿要求），d.roll 为 ±1 方向，相乘得镜像歪头 */
	private static final float HEAD_ROLL = (float) (Math.PI / 4.0);

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/FelineRenderState;)V", at = @At("TAIL"))
	private void laowuApplyHeadRoll(FelineRenderState state, CallbackInfo ci) {
		if (!(state instanceof CatRenderState catState)) return;
		RenderStateHolder.RollData d = RenderStateHolder.DATA.get(catState);
		if (d == null || !d.active) return;
		ModelPart head = this.root.getChild("head");
		if (head != null) head.zRot = d.roll * HEAD_ROLL;
	}
}
