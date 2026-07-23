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
	 *  角度过大会撕裂腿/头与身体的连接（v1.1.13 教训：0.18 致后腿插地+前后腿与身体断开）。
	 *  v1.1.14：0.18 → 0.10，从根源减少 body 髋部连接点位移，断开/插地随之解决；
	 *  弓身感由 HEAD_DIP(0.3，低头) + TAIL_LIFT(0.9，翘尾) 共同承担。 */
	private static final float BODY_PITCH = 0.10f;
	/** 弓背哈气：尾巴翘起。26.1 猫模型 tail1/tail2 **平级**挂 root（javap 核实 addOrReplaceChild 父均为 root），
	 *  旋转 tail1 时 tail2 不跟随 → 两节必脱节。故 tail1 保持不动（只受原版动画），只翘 tail2：
	 *  tail2 根部天然落在 tail1 末端初始位置，tail1 不转则末端不动 → 两节严丝合缝衔接。
	 *  TAIL_LIFT 仅作用于 tail2（尾尖上翘）。 */
	private static final float TAIL_LIFT = 0.9f;
	/** 腿形变（yScale）：身体弓起时腿不跟随，腿长度补偿身体位移。
	 *  v1.1.14 关键认知：单靠 yScale 治不了"够不到身体"与"插进地板"的矛盾——
	 *  body 是单一 cube 部件、腿不随 body 旋转，身体弓得越厉害髋部连接点跑得越远，
	 *  腿要么短（够不到）、要么长（插地），且 body 下降后腿顶与 body 底出现缝（"断开"）。
	 *  本版根本修复：把 BODY_PITCH 0.18→0.10 减少弓身幅度，body 髋部位移显著减小，
	 *  腿断开/插地问题随之消失。腿形变退居次要补偿位（取历史经验中间值）：
	 *  - HIND_SCALE=1.4：后腿 6→8.4。历史：1.3 脱节 / 1.5 不够 / 1.9 插地 → 居中。
	 *  - FRONT_SCALE=0.85：前腿 10→8.5。历史：0.6 缩过头不到地 / 0.9 穿模 → 居中。
	 *  yScale 不被原版 setupAnim 重置、且模型实例被所有猫共享，故非整活时四条腿 yScale 全部复位到 1.0。 */
	private static final float HIND_SCALE = 1.4f;
	private static final float FRONT_SCALE = 0.85f;
	private static final float LEG_SCALE_DEFAULT = 1.0f;

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/FelineRenderState;)V", at = @At("TAIL"), require = 0)
	private void laowuTilt(FelineRenderState state, CallbackInfo ci) {
		if (!(state instanceof LaowuStateAccess a)) {
			return;
		}
		try {
			// this 在编译期是 mixin 类；转型到 Model 取 root()（Model.root() 为 public）。
			ModelPart root = ((Model) (Object) this).root();
			if (root == null) {
				return;
			}

			// 四条腿部件（平级挂 root，javap 核实）。先取到手，下面按是否整活决定形变或复位。
			ModelPart leftHind = root.getChild("left_hind_leg");
			ModelPart rightHind = root.getChild("right_hind_leg");
			ModelPart leftFront = root.getChild("left_front_leg");
			ModelPart rightFront = root.getChild("right_front_leg");

			if (!a.laowuIsActive()) {
				// 还原：腿形变只在整活时生效。yScale 不被原版 setupAnim 重置、
				// 且模型实例被所有猫共享，不复位会让形变永久残留（所有猫腿都变样）。四条腿全复位。
				if (leftHind != null) {
					leftHind.yScale = LEG_SCALE_DEFAULT;
				}
				if (rightHind != null) {
					rightHind.yScale = LEG_SCALE_DEFAULT;
				}
				if (leftFront != null) {
					leftFront.yScale = LEG_SCALE_DEFAULT;
				}
				if (rightFront != null) {
					rightFront.yScale = LEG_SCALE_DEFAULT;
				}
				return;
			}

			// 头部：歪头（绕 Z 轴 roll，镜像）+ 下低（绕 X 轴，低头哈气）。头不与腿相连，旋转头不会撕裂肢体。
			ModelPart head = root.getChild("head");
			if (head != null) {
				head.zRot = a.laowuGetRoll() * HEAD_ROLL;
				head.xRot += HEAD_DIP;
			}

			// 弓背哈气姿态：身体仅微弓（头低尾高）。
			// 方向：绕 X 轴正值 = 头端下沉、尾端上翘（之前 -= 写成头高尾低，已翻正）。
			// 模型只有单一 body 部件、腿不随 body 旋转，body 大幅旋转会撕裂腿/头连接，故 BODY_PITCH 收敛到很小，
			// "哈气感"主要靠 HEAD_DIP（低头）+ 翘尾承担。
			ModelPart body = root.getChild("body");
			if (body != null) {
				body.xRot += BODY_PITCH;
			}

			// 尾巴：tail1 保持不动（只受原版动画），只翘 tail2 尾尖。
			// 26.1 猫模型 tail1/tail2 平级挂 root，tail1 不转则 tail2 根部与 tail1 末端始终对齐 → 两节严丝合缝。
			ModelPart tail2 = root.getChild("tail2");
			if (tail2 != null) {
				tail2.xRot += TAIL_LIFT;
			}

			// 后脚拉长：身体弓起后臀部抬高，用 yScale 把后脚向上拉长（脚尖贴地、膝/髋端上抬衔接身体），
			// 消除"后脚与身体断开"。仅改 yScale，脚宽/深度不变，脚的位置不动（不浮空）。
			if (leftHind != null) {
				leftHind.yScale = HIND_SCALE;
			}
			if (rightHind != null) {
				rightHind.yScale = HIND_SCALE;
			}

			// 前脚缩短：身体弓起后前段下压，前脚髋端需随之下压去贴合身体、消除"穿模"。
			// 前脚 cube 自 foot 向上 10 单位，FRONT_SCALE<1 把髋端下压（脚尖仍贴地）。
			if (leftFront != null) {
				leftFront.yScale = FRONT_SCALE;
			}
			if (rightFront != null) {
				rightFront.yScale = FRONT_SCALE;
			}
		} catch (Throwable t) {
			// 静默兜底，绝不崩渲染器
		}
	}
}
