package com.rogic.client.mixin;

import com.rogic.client.render.LaowuStateAccess;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.animal.feline.AdultFelineModel;
import net.minecraft.client.model.animal.feline.BabyFelineModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.FelineRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在猫模型 setupAnim 的 TAIL 设 head.zRot（绕 Z 轴 roll = 歪头杀）。
 * 必须放 TAIL：AdultFelineModel.setupAnim 自身会读写 head.zRot，
 * 只有在它之后写入，歪头角度才会保留到顶点提交。
 *
 * 取模型用 `this`（本 mixin 注入到模型类，`this` 就是模型），不需要 @Shadow 字段
 * （26.1 mojmap 构建无 refmap，@Shadow vanilla 字段必崩黑屏）。
 * 歪头方向从 CatRenderState 经 LaowuStateAccess 读取（extractRenderState 写入）。
 */
@Mixin({ AdultFelineModel.class, BabyFelineModel.class })
public class CatModelMixin {

	/** 歪头角度：45°（设计稿要求），roll 为 ±1，相乘得镜像歪头 */
	private static final float HEAD_ROLL = (float) (Math.PI / 4.0);

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/FelineRenderState;)V", at = @At("TAIL"), require = 0)
	private void laowuTilt(FelineRenderState state, CallbackInfo ci) {
		if (!(state instanceof LaowuStateAccess a)) {
			return;
		}
		if (!a.laowuIsActive()) {
			return;
		}
		try {
			// this 在编译期是 mixin 类；转型到 Model 取 root()（Model.root() 为 public）。
			ModelPart root = ((Model) (Object) this).root();
			ModelPart head = root == null ? null : root.getChild("head");
			if (head != null) {
				head.zRot = a.laowuGetRoll() * HEAD_ROLL;
			}
		} catch (Throwable t) {
			// 静默兜底，绝不崩渲染器
		}
	}
}
