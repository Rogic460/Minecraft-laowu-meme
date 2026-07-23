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
	/** 弓背哈气：头下低（绕 X 轴，正值=头端朝下、低头哈气），叠加在 setupAnim 原动画上 */
	private static final float HEAD_DIP = 0.3f;
	/** 弓背哈气：身体仅微弓（绕 X 轴，正值=头低尾高）。猫模型 body 是单一部件、腿不随 body 旋转，
	 *  角度过大会撕裂腿/头与身体的连接；v1.1.9 为 0.1 偏弱，v1.1.10 略加到 0.18（用户要求"略微加一点"）。 */
	private static final float BODY_PITCH = 0.18f;
	/** 弓背哈气：尾巴翘起。经查证 26.1 猫模型 tail1/tail2 是**平级**挂在 root 下（用 PartDefinition.addOrReplaceChild 构建，非串联子部件），
	 *  旋转 tail1 时 tail2 不跟随，连接处必产生偏差；故两节用**相同角度**平行翘起，使偏差最小、视觉像一条直尾上翘。
	 *  v1.1.9 用 0.9/1.4（tail2 过大→尾尖过度弯折、与 tail1 末端明显脱节），本版收敛为 0.6/0.6。 */
	private static final float TAIL_LIFT_1 = 0.6f;
	private static final float TAIL_LIFT_2 = 0.6f;

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
			if (root == null) {
				return;
			}

			// 头部：歪头（绕 Z 轴 roll，镜像）+ 下低（绕 X 轴，低头哈气）。头不与腿相连，旋转头不会撕裂肢体。
			ModelPart head = root.getChild("head");
			if (head != null) {
				head.zRot = a.laowuGetRoll() * HEAD_ROLL;
				head.xRot += HEAD_DIP;
			}

			// 弓背哈气姿态：身体仅微弓（头低尾高）+ 尾巴翘起（尾尖竖直），叠加在 setupAnim 原动画上。
			// 方向：绕 X 轴正值 = 头端下沉、尾端上翘（之前 -= 写成头高尾低，已翻正）。
			// 模型只有单一 body 部件、腿不随 body 旋转，body 大幅旋转会撕裂腿/头连接，故 BODY_PITCH 收敛到很小，
			// "哈气感"主要靠 HEAD_DIP（低头）+ 翘尾承担。
			ModelPart body = root.getChild("body");
			if (body != null) {
				body.xRot += BODY_PITCH;
			}
			ModelPart tail1 = root.getChild("tail1");
			if (tail1 != null) {
				tail1.xRot += TAIL_LIFT_1;
			}
			ModelPart tail2 = root.getChild("tail2");
			if (tail2 != null) {
				tail2.xRot += TAIL_LIFT_2;
			}
		} catch (Throwable t) {
			// 静默兜底，绝不崩渲染器
		}
	}
}
