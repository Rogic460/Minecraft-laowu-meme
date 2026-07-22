package com.rogic.client.mixin;

import com.rogic.client.ClientMemeState;
import net.minecraft.client.renderer.entity.CatRenderer;
import net.minecraft.client.renderer.entity.state.CatRenderState;
import net.minecraft.world.entity.animal.feline.Cat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 CatRenderer 提取渲染状态时，把"锁定状态 + 歪头方向"写入 RenderStateHolder，
 * 并对锁定猫把 scale 放大 50%（仅渲染，不改碰撞箱）。
 */
@Mixin(CatRenderer.class)
public class CatRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/animal/feline/Cat;Lnet/minecraft/client/renderer/entity/state/CatRenderState;F)V", at = @At("TAIL"))
	private void laowuPopulate(Cat cat, CatRenderState state, float partialTick, CallbackInfo ci) {
		ClientMemeState cs = ClientMemeState.get();
		int id = cat.getId();
		RenderStateHolder.RollData d = new RenderStateHolder.RollData();
		d.active = cs.isActive(id);
		d.roll = cs.getRollSign(id);
		RenderStateHolder.DATA.put(state, d);
		if (d.active) {
			state.scale *= 1.5f;
		}
	}
}
