package com.rogic.client.mixin;

import com.rogic.client.ClientMemeState;
import com.rogic.client.render.LaowuStateAccess;
import net.minecraft.client.renderer.entity.CatRenderer;
import net.minecraft.client.renderer.entity.state.CatRenderState;
import net.minecraft.world.entity.animal.feline.Cat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 CatRenderer 提取渲染状态时：
 *  1) 对锁定猫把 scale 放大 50%（仅渲染，不改碰撞箱）；
 *  2) 把"是否锁定 + 歪头方向"写入 CatRenderState（经 LaowuStateAccess），
 *     供 CatModelMixin 在 setupAnim(TAIL) 读取并设 head.zRot。
 *
 * 关键事实（MC 26.1 实测，字节码核实）：
 *  - AdultFelineModel.setupAnim 会主动读写 head.zRot（不只是 xRot/yRot），
 *    所以**必须在 setupAnim 的 TAIL 设 zRot**，extractRenderState 阶段设会被覆盖。
 *  - 因此歪头逻辑搬到 CatModelMixin.setupAnim(TAIL)，本 mixin 只负责放大 + 写入状态。
 *  - 用 CatRenderState 上的 @Unique 字段传递，同一实例从 extractRenderState 流到 setupAnim，
 *    可靠、无需 @Shadow（26.1 mojmap 无 refmap，@Shadow vanilla 字段必崩黑屏）。
 */
@Mixin(CatRenderer.class)
public class CatRendererMixin {

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/animal/feline/Cat;Lnet/minecraft/client/renderer/entity/state/CatRenderState;F)V", at = @At("TAIL"))
	private void laowuPopulate(Cat cat, CatRenderState state, float partialTick, CallbackInfo ci) {
		ClientMemeState cs = ClientMemeState.get();
		int id = cat.getId();
		boolean active = cs.isActive(id);
		float roll = cs.getRollSign(id);

		if (active) {
			state.scale *= 1.25f;
		}

		// 把整活状态写入 render state，供模型层 setupAnim(TAIL) 读取设歪头。
		// CatRenderState 经 CatRenderStateMixin 实现 LaowuStateAccess。
		LaowuStateAccess a = (LaowuStateAccess) state;
		a.laowuSetActive(active);
		a.laowuSetRoll(roll);
	}
}
