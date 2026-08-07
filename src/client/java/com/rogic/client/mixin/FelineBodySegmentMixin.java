package com.rogic.client.mixin;

import net.minecraft.client.model.animal.feline.AdultFelineModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * B 方案（多段关节模型）v3：mixin AdultFelineModel.createBodyMesh RETURN，
 * 给返回 mesh 的 body 加 body_mid/body_front 子节点（连接点 pose）。
 *
 * 迭代史：
 * - v1（mixin createBodyMesh，子段 PartPose=ZERO）：旋转中心=body 局部原点 → 大角度位移 → 零件飞散（畸形）
 * - v2（mixin bakeLayer，自己重建 mesh）：head/尾巴/两条腿丢失（"只剩两条腿"）
 * - v3（mixin createBodyMesh，连接点 pose）：完全复用原版 mesh（部件齐全），
 *   子段 PartPose 偏移到【连接点】（前一段末端），cube 从连接点延伸 → 旋转绕连接点 = 真关节。
 *   原版 CAT 注册的 LayerDefinition 就是 createBodyMesh 结果，bakeLayer 自然拿到多段，渲染器零改动。
 *
 * 副作用：createBodyMesh 是猫/豹猫共用——豹猫也会有多段 body，但蜷缩旋转只在猫整活时触发，
 * 豹猫不旋转 = 子段 cube 与原 body 重合，视觉无差异。
 */
@Mixin(AdultFelineModel.class)
public abstract class FelineBodySegmentMixin {

	private static final float BODY_X = -2.0f;
	private static final float BODY_Z = -8.0f;
	private static final float BODY_W = 4.0f;
	private static final float BODY_H = 16.0f;
	private static final float BODY_D = 6.0f;
	/** 段1 末端 y = 段2 连接点（body 局部坐标，y 沿猫纵向；原 cube 从 y=3 开始高 16） */
	private static final float SEG1_END = 3.0f + BODY_H / 3.0f;    // 8.33
	/** 段2 末端 y = 段3 连接点 */
	private static final float SEG2_END = 3.0f + 2 * BODY_H / 3.0f; // 13.66
	private static final float SEG_H = BODY_H / 3.0f;               // 5.33

	@Inject(method = "createBodyMesh(Lnet/minecraft/client/model/geom/builders/CubeDeformation;)Lnet/minecraft/client/model/geom/builders/MeshDefinition;", at = @At("RETURN"))
	private static void laowuMultiSegmentBody(CubeDeformation deformation, CallbackInfoReturnable<MeshDefinition> cir) {
		try {
			MeshDefinition mesh = cir.getReturnValue();
			PartDefinition root = mesh.getRoot();
			PartDefinition body = root.getChild("body");
			if (body == null) return;
			// 段2：连接点 = 段1末端（body 局部 y=8.33），cube 从连接点向前延伸 5.33
			body.addOrReplaceChild("body_mid",
					CubeListBuilder.create().texOffs(0, 0)
							.addBox(BODY_X, 0.0f, BODY_Z, BODY_W, SEG_H, BODY_D, deformation),
					PartPose.offset(0.0f, SEG1_END, 0.0f));
			// 段3：连接点 = 段2末端（y=13.66）
			body.addOrReplaceChild("body_front",
					CubeListBuilder.create().texOffs(0, 0)
							.addBox(BODY_X, 0.0f, BODY_Z, BODY_W, SEG_H, BODY_D, deformation),
					PartPose.offset(0.0f, SEG2_END, 0.0f));
		} catch (Throwable t) {
			// 失败保留原版单 cube（绝不黑屏）
		}
	}
}
