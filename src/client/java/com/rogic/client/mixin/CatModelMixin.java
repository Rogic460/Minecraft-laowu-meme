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
	/** 蜷缩哈气：头下压幅度（弧度，≈57°，头几乎贴地。头不与腿相连，可大幅旋转不撕裂） */
	/** 蜷缩哈气：头贴地（参考图：头端几乎贴地，弧度大。头不与腿相连可大幅旋转） */
	private static final float CURL_HEAD_DIP = 1.4f;
	/** 蜷缩哈气：身体后段（臀/腰）微弓——参考图后段几乎是直的（后腿撑着） */
	private static final float CURL_BODY_PITCH = 0.2f;
	/** 蜷缩哈气：中段（腹腔）大幅下沉——参考图中段明显塌陷 */
	private static final float CURL_MID_PITCH = 0.75f;
	/** 蜷缩哈气：前段（胸腔）几乎贴地——参考图前段呈水平下沉 ≈π/2 级别 */
	private static final float CURL_FRONT_PITCH = 1.25f;
	/** 蜷缩哈气：尾巴下卷（tail1/tail2 平级挂 root，旋转 tail1 时 tail2 不跟随 → 两节都下卷防脱节） */
	private static final float CURL_TAIL_CURL = 0.7f;
	/** 蜷缩哈气：前腿前折蜷起（绕 X 轴） */
	private static final float CURL_FRONT_TUCK = 0.5f;
	/** 蜷缩哈气：后腿几乎不蹲（参考图：后腿站立支撑前半身塌下去） */
	private static final float CURL_HIND_TUCK = 0.05f;
	/** 蜷缩哈气：前后腿微内收（绕 Z 轴，向身体中缝收，营造蜷团感） */
	private static final float CURL_LEG_IN = 0.25f;
	/** 蜷缩哈气：前腿缩短（蜷缩时腿贴身体），后腿拉长（蹲起支撑） */
	private static final float FRONT_CURL_SCALE = 0.7f;
	private static final float HIND_CURL_SCALE = 1.25f;
	private static final float LEG_SCALE_DEFAULT = 1.0f;
	/** 耄耋头瞄准诊断日志节流计数器（每 32 tick 打一条 [maodie-head]） */
	private static long maodieHeadDebugTick = 0;
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
				// 老吴整活：蜷缩哈气（B 方案：多段身体弯曲）——head 大幅下压贴地 + 脊柱逐段弯曲成 C 形
				// （body 前弓 + body_mid 再弯 + body_front 更弯，由 FelineBodySegmentMixin 拆出）+ 尾巴下卷 + 腿蜷。
				ModelPart head = root.getChild("head");
				if (head != null) {
					head.zRot = a.laowuGetRoll() * HEAD_ROLL;
					head.xRot += CURL_HEAD_DIP;      // 头贴地
				}
				// 脊柱分段弯曲：body 前弓 + 中段 + 前段逐级加大，形成 C 形蜷缩。
				// body_mid/body_front 是 body 的子节点（FelineBodySegmentMixin 注入构建），从 body 取。
				ModelPart body = root.getChild("body");
				if (body != null) body.xRot += CURL_BODY_PITCH;
				ModelPart bodyMid = body != null ? body.getChild("body_mid") : null;
				if (bodyMid != null) bodyMid.xRot += CURL_MID_PITCH;
				ModelPart bodyFront = body != null ? body.getChild("body_front") : null;
				ModelPart tail1 = root.getChild("tail1");
				if (tail1 != null) tail1.xRot += CURL_TAIL_CURL;
				ModelPart tail2 = root.getChild("tail2");
				if (tail2 != null) tail2.xRot += CURL_TAIL_CURL;
				// 前腿内收蜷起，后腿蹲
				if (leftFront != null) { leftFront.xRot += CURL_FRONT_TUCK; leftFront.zRot += CURL_LEG_IN; leftFront.yScale = FRONT_CURL_SCALE; }
				if (rightFront != null) { rightFront.xRot += CURL_FRONT_TUCK; rightFront.zRot -= CURL_LEG_IN; rightFront.yScale = FRONT_CURL_SCALE; }
				if (leftHind != null) { leftHind.xRot += CURL_HIND_TUCK; leftHind.zRot += CURL_LEG_IN; leftHind.yScale = HIND_CURL_SCALE; }
				if (rightHind != null) { rightHind.xRot += CURL_HIND_TUCK; rightHind.zRot -= CURL_LEG_IN; rightHind.yScale = HIND_CURL_SCALE; }
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
