package com.rogic.client.mixin;

import com.rogic.client.ClientMemeState;
import net.minecraft.client.model.CatModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.animal.Cat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 经典管线（1.21.x）：CatModel.setupAnim 直接传实体（无 RenderState）。在 TAIL 设 head.zRot（歪头），
 * 状态按 cat.getId() 从 ClientMemeState 读。
 * 1.21 的猫模型部件（head/body/tail2/四腿）字段声明在父类 OcelotModel，不在 CatModel 自身——
 * 故部件通过 OcelotModelAccessor（@Accessor 挂在 OcelotModel）取，本 mixin 只负责 setupAnim 注入。
 */
@Mixin(CatModel.class)
public class CatModelMixin {

	/** 歪头角度：45°（设计稿要求），roll 为 ±1，相乘得镜像歪头 */
	private static final float HEAD_ROLL = (float) (Math.PI / 4.0);
	/** 弓背哈气：头下低（绕 X 轴，正值=头端朝下、低头哈气），叠加在 setupAnim 原动画上 */
	private static final float HEAD_DIP = 0.3f;
	/** 弓背哈气：身体仅微弓（绕 X 轴，正值=头低尾高）。单 cube 模型、腿不随 body 旋转，角度过大会撕裂腿/头连接。 */
	private static final float BODY_PITCH = 0.10f;
	/** 弓背哈气：尾巴翘起。tail1/tail2 平级挂 root，旋转 tail1 会脱节，故只翘 tail2 尾尖。 */
	private static final float TAIL_LIFT = 0.9f;
	/** 腿形变（yScale）：身体弓起时腿不跟随，腿长度补偿身体位移。非整活时复位到 1.0。 */
	private static final float HIND_SCALE = 1.4f;
	private static final float FRONT_SCALE = 0.85f;
	private static final float LEG_SCALE_DEFAULT = 1.0f;

	@Inject(method = "setupAnim(Lnet/minecraft/world/entity/animal/Cat;FFFFF)V", at = @At("TAIL"), require = 0)
	private void laowuTilt(Cat cat, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
		ClientMemeState cs = ClientMemeState.get();
		int id = cat.getId();
		boolean active = cs.isActive(id);
		float roll = cs.getRollSign(id);

		// 头/身/尾/腿部件字段声明在父类 OcelotModel，经 accessor 取（不能 @Shadow 继承字段）。
		OcelotModelAccessor acc = (OcelotModelAccessor) (Object) this;
		ModelPart head = acc.laowuHead();
		ModelPart body = acc.laowuBody();
		ModelPart tail2 = acc.laowuTail2();
		ModelPart leftHindLeg = acc.laowuLeftHindLeg();
		ModelPart rightHindLeg = acc.laowuRightHindLeg();
		ModelPart leftFrontLeg = acc.laowuLeftFrontLeg();
		ModelPart rightFrontLeg = acc.laowuRightFrontLeg();

		try {
			if (!active) {
				// 还原：腿形变只在整活时生效。模型实例被所有猫共享，不复位会让形变永久残留。
				leftHindLeg.yScale = LEG_SCALE_DEFAULT;
				rightHindLeg.yScale = LEG_SCALE_DEFAULT;
				leftFrontLeg.yScale = LEG_SCALE_DEFAULT;
				rightFrontLeg.yScale = LEG_SCALE_DEFAULT;
				return;
			}

			// 头部：歪头（绕 Z 轴 roll，镜像）+ 下低（绕 X 轴，低头哈气）。头不与腿相连，旋转头不会撕裂肢体。
			if (head != null) {
				head.zRot = roll * HEAD_ROLL;
				head.xRot += HEAD_DIP;
			}

			// 弓背哈气姿态：身体仅微弓（头低尾高）。模型只有单一 body 部件、腿不随 body 旋转，body 大幅旋转会撕裂连接，故收敛。
			if (body != null) {
				body.xRot += BODY_PITCH;
			}

			// 尾巴：tail1 保持不动（只受原版动画），只翘 tail2 尾尖（两节平级挂 root，tail1 不转则无缝衔接）。
			if (tail2 != null) {
				tail2.xRot += TAIL_LIFT;
			}

			// 后脚拉长：身体弓起后臀部抬高，yScale 把后脚向上拉长，消除"后脚与身体断开"。
			leftHindLeg.yScale = HIND_SCALE;
			rightHindLeg.yScale = HIND_SCALE;

			// 前脚缩短：身体弓起后前段下压，前脚髋端随之下压贴合身体、消除"穿模"。
			leftFrontLeg.yScale = FRONT_SCALE;
			rightFrontLeg.yScale = FRONT_SCALE;
		} catch (Throwable t) {
			// 静默兜底，绝不崩渲染器
		}
	}
}
