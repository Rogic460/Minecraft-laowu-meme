package com.rogic.client.mixin;

import com.rogic.client.ClientMemeState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.CatRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.CatRenderState;
import net.minecraft.world.entity.animal.feline.Cat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 CatRenderer 提取渲染状态时：
 *  1) 对锁定猫把 scale 放大 50%（仅渲染，不改碰撞箱）；
 *  2) 直接在模型 head 零件上设 zRot（绕 Z 轴 roll = 歪头杀），
 *     从 ClientMemeState 按 cat.getId() 取歪头方向，不依赖跨 mixin 的弱引用桥。
 *
 * 关键事实（MC 26.1 实测 + 玩家 latest.log 排错）：
 *  - 本 mixin 在 extractRenderState 阶段写入，之后 submit -> submitModel 内部调用
 *    setupAnim，而 AdultFelineModel.setupAnim 只改 head.xRot/yRot、绝不碰 zRot，
 *    所以这里写入的 zRot 会一直保留到顶点提交，头部歪头必定生效。
 *  - ModelPart.translateAndRotate 通过 Quaternionf.rotationZYX(xRot,yRot,zRot) 应用 zRot，
 *    故 head.zRot 直接驱动头部 roll。
 *  - Model.root() 是 public（Model 基类），head 零件名为 "head"，可由 root.getChild("head") 取到。
 *
 * ⚠️ 为什么不用 @Shadow 取模型字段（重要教训）：
 *  运行时游戏类是 intermediary 命名，mixin 又缺 refmap（mojmap 构建下 Loom 不生成），
 *  导致 @Shadow 按 mojmap 字段名（如 `model`）在 intermediary 类里找不到
 *  （实际是 field_xxxx）→ "field model was not located" → mixin apply 崩溃 →
 *  主菜单黑屏（v1.1.4 / v1.1.5 都栽在这）。
 *  改用 CatRenderer 继承的公开方法 `LivingEntityRenderer.getModel()` 取模型（公开方法名在
 *  intermediary 里被保留，和 extractRenderState 一样能解析），彻底绕开 @Shadow 字段名映射问题。
 *  整段再包 try/catch，保证任何猫变种/模型异常都绝不崩渲染器。
 */
@Mixin(CatRenderer.class)
public class CatRendererMixin {

	/** 歪头角度：45°（设计稿要求），roll 为 ±1，相乘得镜像歪头 */
	private static final float HEAD_ROLL = (float) (Math.PI / 4.0);

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/animal/feline/Cat;Lnet/minecraft/client/renderer/entity/state/CatRenderState;F)V", at = @At("TAIL"))
	private void laowuPopulate(Cat cat, CatRenderState state, float partialTick, CallbackInfo ci) {
		ClientMemeState cs = ClientMemeState.get();
		int id = cat.getId();
		boolean active = cs.isActive(id);
		float roll = cs.getRollSign(id);

		if (active) {
			state.scale *= 1.5f;
		}

		// 头部歪头：直接操作模型 head 零件 zRot（绕 Z 轴 roll）。
		// 非锁定猫清零，避免上一帧的歪头残留。
		// 通过公开方法 getModel() 取模型，避免 @Shadow 字段在 intermediary 下解析失败。
		try {
			LivingEntityRenderer<?, ?, ?> renderer = (LivingEntityRenderer<?, ?, ?>) (Object) this;
			@SuppressWarnings("unchecked")
			EntityModel<CatRenderState> model = (EntityModel<CatRenderState>) renderer.getModel();
			if (model != null) {
				ModelPart root = model.root();
				ModelPart head = root == null ? null : root.getChild("head");
				if (head != null) {
					head.zRot = active ? roll * HEAD_ROLL : 0f;
				}
			}
		} catch (Throwable t) {
			// 静默吞掉，保证渲染流程不中断（绝不黑屏）
		}
	}
}
