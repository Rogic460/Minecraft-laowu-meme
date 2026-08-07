package com.rogic.client.mixin;

import com.rogic.LaowuMemeMod;
import com.rogic.client.render.LaowuStateAccess;
import com.rogic.maodie.MaodieBlueprint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.animal.feline.AdultFelineModel;
import net.minecraft.client.model.animal.feline.BabyFelineModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.FelineRenderState;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在猫模型 setupAnim 的 TAIL：
 *  - 老吴整活：head.zRot 歪头（绕 Z 轴）+ 弓背哈气（head 低头 + body 微弓 + tail2 翘）。
 *    必须放 TAIL：AdultFelineModel.setupAnim 自身会读写 head.zRot，只有之后写入才保留。
 *  - 耄耋猫：服务端令其坐下（setOrderedToSit）+ 客户端播放音频；模型层让猫头转向最近的玩家
 *    （head.yRot/xRot），BUG 1 修复。同样放 TAIL，避免被原版 setupAnim 覆盖。
 *
 * 取模型用 `this`（本 mixin 注入到模型类，`this` 就是模型），不需要 @Shadow 字段
 * （26.1 mojmap 构建无 refmap，@Shadow vanilla 字段必崩黑屏）。
 * 状态从 CatRenderState 经 LaowuStateAccess 读取（extractRenderState 写入）。
 */
@Mixin({ AdultFelineModel.class, BabyFelineModel.class })
public class CatModelMixin {

	/** 歪头角度：45°（设计稿要求），roll 为 ±1，相乘得镜像歪头 */
	private static final float HEAD_ROLL = (float) (Math.PI / 4.0);
	/** 弓背哈气：头下低（绕 X 轴，正值=头端朝下、低头哈气），叠加在 setupAnim 原动画上 */
	private static final float HEAD_DIP = 0.3f;
	/** 弓背哈气：身体仅微弓（绕 X 轴）。猫模型 body 是单一部件、腿不随 body 旋转，
	 *  角度过大会撕裂腿/头与身体的连接。0.10 是 v1.1.14 教训后收敛的安全值，
	 *  "弓身感"由 HEAD_DIP(0.3，低头) + TAIL_LIFT(0.9，翘尾) 共同承担。 */
	private static final float BODY_PITCH = 0.10f;
	/** 弓背哈气：尾巴翘起。26.1 猫模型 tail1/tail2 平级挂 root，旋转 tail1 时 tail2 不跟随 → 脱节，
	 *  故 tail1 保持不动，只翘 tail2（根部天然落在 tail1 末端初始位置，两节严丝合缝）。 */
	private static final float TAIL_LIFT = 0.9f;
	/** 耄耋头瞄准诊断日志节流计数器（每 32 tick 打一条 [maodie-head]） */
	private static long maodieHeadDebugTick = 0;
	/** 腿形变（yScale）：身体弓起时腿不跟随，腿长度补偿身体位移。历史经验居中值：
	 *  - HIND_SCALE=1.4：后腿 6→8.4（1.3 脱节 / 1.5 不够 / 1.9 插地 → 居中）。
	 *  - FRONT_SCALE=0.85：前腿 10→8.5（0.6 缩过头不到地 / 0.9 穿模 → 居中）。
	 *  yScale 不被原版 setupAnim 重置、且模型实例被所有猫共享，故非整活时四条腿 yScale 全部复位到 1.0。 */
	private static final float HIND_SCALE = 1.4f;
	private static final float FRONT_SCALE = 0.85f;
	private static final float LEG_SCALE_DEFAULT = 1.0f;
	/** 拍扁"X"形：四肢外撇角度（弧度，≈55°，对角腿同向撇开） */
	private static final float FLAT_LEG_SPLAY = 0.96f;
	/** 拍扁"X"形：四肢拉长倍数（补偿身体压扁到 0.175 后腿的缩短，让腿从身体里伸出） */
	private static final float FLAT_LEG_STRETCH = 3.0f;

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

			// 四条腿部件（平级挂 root，javap 核实）。先取到手，下面按状态决定形变或复位。
			ModelPart leftHind = root.getChild("left_hind_leg");
			ModelPart rightHind = root.getChild("right_hind_leg");
			ModelPart leftFront = root.getChild("left_front_leg");
			ModelPart rightFront = root.getChild("right_front_leg");

			// 非老吴整活：腿形变复位（模型实例共享，不复位会让形变永久残留）。
			if (!a.laowuIsActive()) {
				if (leftHind != null) leftHind.yScale = LEG_SCALE_DEFAULT;
				if (rightHind != null) rightHind.yScale = LEG_SCALE_DEFAULT;
				if (leftFront != null) leftFront.yScale = LEG_SCALE_DEFAULT;
				if (rightFront != null) rightFront.yScale = LEG_SCALE_DEFAULT;
			}

			if (a.laowuIsActive()) {
				// 老吴整活：歪头（绕 Z 轴 roll，镜像）+ 低头哈气（绕 X 轴）+ 弓背（body 微弓）+ 翘尾 + 腿形变。
				ModelPart head = root.getChild("head");
				if (head != null) {
					head.zRot = a.laowuGetRoll() * HEAD_ROLL;
					head.xRot += HEAD_DIP;
				}
				ModelPart body = root.getChild("body");
				if (body != null) body.xRot += BODY_PITCH;
				ModelPart tail2 = root.getChild("tail2");
				if (tail2 != null) tail2.xRot += TAIL_LIFT;
				if (leftHind != null) leftHind.yScale = HIND_SCALE;
				if (rightHind != null) rightHind.yScale = HIND_SCALE;
				if (leftFront != null) leftFront.yScale = FRONT_SCALE;
				if (rightFront != null) rightFront.yScale = FRONT_SCALE;
			}

			// 耄耋猫：玩家进入 MAODIE_PROXIMITY_RADIUS(5 格) 时才转头盯最近玩家（用户要求加 5 格条件）。
			// 头瞄准公式（v1.2.3 修根因）：渲染器对整个模型按 rotationDegrees(180 - state.bodyRot) 旋转，
			// bodyRot 是「度」，必须 toRadians；head.yRot 为相对旋转 = 目标绝对 yaw − 模型世界旋转。
			// 诊断日志 [maodie-head]（节流）：确认分支是否生效、数值是否合理（验证"没效果"是真没动还是像普通猫）。
			if (a.maodieIsBound()) {
				ModelPart head = root.getChild("head");
				if (head != null) {
					Minecraft mc = Minecraft.getInstance();
					if (mc.level != null) {
						double cx = state.x, cy = state.y, cz = state.z;
						double best = Double.MAX_VALUE;
						Player nearest = null;
						for (Player p : mc.level.players()) {
							double d = p.distanceToSqr(cx, cy, cz);
							if (d < best) { best = d; nearest = p; }
						}
						if (nearest != null) {
							double dist = Math.sqrt(best);
							// 仅 5 格内才强制转头盯玩家；超出则交给原版（普通猫行为）
							if (dist <= MaodieBlueprint.MAODIE_PROXIMITY_RADIUS) {
								double dx = nearest.getX() - cx;
								double dz = nearest.getZ() - cz;
								double dy = nearest.getY() - cy;
								// 绝对目标 yaw（弧度）：MC forward = (-sin(yaw), -cos(yaw))
								double targetYaw = Math.atan2(-dx, -dz);
								// 模型整体被渲染器按 (180 - bodyRot)° 旋转 → 转弧度作世界基准
								double modelRot = Math.toRadians(180.0 - state.bodyRot);
								// head.yRot 为相对量：相对旋转 = 目标绝对 yaw − 模型世界旋转
								float desiredYaw = (float) (targetYaw - modelRot);
								// 俯仰：玩家在下方(dy<0)→xRot 正（低头）；上方→负（抬头）
								double horiz = Math.sqrt(dx * dx + dz * dz);
								float desiredPitch = (float) Math.atan2(-dy, horiz);
								// 最短路径插值：避免最近玩家切换时头瞬移，并抑制环绕跳变导致的反向狂转
								head.yRot = lerpAngleShortest(head.yRot, desiredYaw, 0.4f);
								head.xRot = lerpAngleShortest(head.xRot, desiredPitch, 0.4f);
								if ((maodieHeadDebugTick++ & 0x1FL) == 0) {
									LaowuMemeMod.LOGGER.info("[maodie-head] dist={} bodyRot={} targetYaw={} modelRot={} desiredYaw={} head.yRot={} head.xRot={}",
											String.format("%.2f", dist), state.bodyRot, targetYaw, modelRot, desiredYaw, head.yRot, head.xRot);
								}
							}
						}
					}
				}
			}
			// 铲子拍扁：四肢拉长 + 对角交叉外撇，形成"X"形（身体被 PoseStack 压扁到 y=0.175，
			// 铲子拍扁：四肢拉长 + zRot 左右张开，形成"X"形（身体被 PoseStack 压扁到 y=0.175，
			// 四条腿向两侧张开；对角腿同向：左前+右后一组、右前+左后一组，交叉成 X）。
			// 注意：必须用 zRot（绕 Z 轴=左右张开），xRot 是前后摆动（左腿会往前/右腿往后，方向错）。
			// 模型实例共享，非扁平态必须复位腿的 yScale（zRot/xRot 由原版 setupAnim 每 tick 重设）。
			boolean flat = a.laowuIsFlat();
			if (flat) {
				if (leftHind != null) { leftHind.zRot = -FLAT_LEG_SPLAY; leftHind.yScale = FLAT_LEG_STRETCH; }
				if (rightHind != null) { rightHind.zRot = FLAT_LEG_SPLAY; rightHind.yScale = FLAT_LEG_STRETCH; }
				if (leftFront != null) { leftFront.zRot = FLAT_LEG_SPLAY; leftFront.yScale = FLAT_LEG_STRETCH; }
				if (rightFront != null) { rightFront.zRot = -FLAT_LEG_SPLAY; rightFront.yScale = FLAT_LEG_STRETCH; }
			} else {
				// 非扁平态：腿 yScale 复位（xRot 由原版 setupAnim 每 tick 覆盖，无需处理）
				if (leftHind != null && !a.laowuIsActive()) leftHind.yScale = LEG_SCALE_DEFAULT;
				if (rightHind != null && !a.laowuIsActive()) rightHind.yScale = LEG_SCALE_DEFAULT;
				if (leftFront != null && !a.laowuIsActive()) leftFront.yScale = LEG_SCALE_DEFAULT;
				if (rightFront != null && !a.laowuIsActive()) rightFront.yScale = LEG_SCALE_DEFAULT;
			}
		} catch (Throwable t) {
			// 静默兜底，绝不崩渲染器
		}
	}

	/** 最短路径角度插值（弧度）：把差值规整到 (-π, π]，避免从 π 到 -π 绕远路导致反向狂转。 */
	private static float lerpAngleShortest(float cur, float target, float t) {
		float diff = target - cur;
		diff = (float) (diff - 2.0 * Math.PI * Math.floor((diff + Math.PI) / (2.0 * Math.PI)));
		return cur + diff * t;
	}
}
