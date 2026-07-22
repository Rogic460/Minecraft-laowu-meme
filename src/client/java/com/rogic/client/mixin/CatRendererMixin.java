package com.rogic.client.mixin;

import com.rogic.client.ClientMemeState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.CatRenderer;
import net.minecraft.client.renderer.entity.state.CatRenderState;
import net.minecraft.world.entity.animal.feline.Cat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 CatRenderer 提取渲染状态时：
 *  1) 对锁定猫把 scale 放大 50%（仅渲染，不改碰撞箱）；
 *  2) 直接在模型 head 零件上设 zRot（绕 Z 轴 roll = 歪头杀），
 *     从 ClientMemeState 按 cat.getId() 取歪头方向，不依赖跨 mixin 的弱引用桥。
 *
 * 关键事实（MC 26.1 实测）：
 *  - 本 mixin 在 extractRenderState 阶段写入，之后 submit -> submitModel 内部调用
 *    setupAnim，而 AdultFelineModel.setupAnim 只改 head.xRot/yRot、绝不碰 zRot，
 *    所以这里写入的 zRot 会一直保留到顶点提交，头部歪头必定生效。
 *  - ModelPart.translateAndRotate 通过 Quaternionf.rotationZYX(xRot,yRot,zRot) 应用 zRot，
 *    故 head.zRot 直接驱动头部 roll。
 *  - Model.root() 是 public（Model 基类），head 零件名为 "head"，可由 root.getChild("head") 取到。
 *  - @Shadow 字段必须按字节码擦除类型声明：LivingEntityRenderer 里是 `protected M model`，
 *    擦除后是 net.minecraft.client.model.EntityModel（不是 AbstractFelineModel），否则 Mixin 报
 *    "field model was not located" 导致整个 CatRenderer 注入失败、渲染器初始化崩、主菜单黑屏。
 */
@Mixin(CatRenderer.class)
public class CatRendererMixin {
	// 注意：擦除类型必须用 EntityModel（基类），不能用 AbstractFelineModel，否则 @Shadow 解析失败。
	@Shadow protected EntityModel<CatRenderState> model;

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
		// 整段包 try/catch：万一部分模型 API 在某些猫变种上异常，也绝不让渲染器初始化崩掉（黑屏）。
		try {
			if (this.model != null) {
				ModelPart root = this.model.root();
				ModelPart head = root == null ? null : root.getChild("head");
				if (head != null) {
					head.zRot = active ? roll * HEAD_ROLL : 0f;
				}
			}
		} catch (Throwable t) {
			// 静默吞掉，保证渲染流程不中断
		}
	}
}
