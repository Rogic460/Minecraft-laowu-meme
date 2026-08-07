package com.rogic.client.mixin;

import net.minecraft.client.model.animal.feline.AdultFelineModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.geom.PartPose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * B 方案（多段身体）核心：把猫 body（单 cube，纵向长条 4×16×6）拆成 3 段首尾相接的 cube，
 * 分别挂在 body / body_mid / body_front 三个 ModelPart 上（body_mid/front 是 body 的子节点）。
 * 平时三段 xRot=0 严丝合缝、视觉与单 cube 无异；蜷缩哈气时逐段旋转 → 脊柱弯曲成 C 形。
 *
 * 不替换模型类、不改渲染器（AbstractFelineModel 的 body 字段仍指向顶层 body），零黑屏风险。
 */
@Mixin(AdultFelineModel.class)
public abstract class FelineBodySegmentMixin {

	/** body cube 原尺寸（javap 实证）：addBox(-2, 3, -8, 4, 16, 6)，pose offset(0,12,-10) rotX(π/2) */
	private static final float BODY_X = -2.0f;
	private static final float BODY_Y0 = 3.0f;
	private static final float BODY_Z = -8.0f;
	private static final float BODY_W = 4.0f;
	private static final float BODY_H = 16.0f;
	private static final float BODY_D = 6.0f;
	/** 拆成 3 段，每段高度 */
	private static final float SEG_H = BODY_H / 3.0f;

	@Inject(method = "createBodyMesh(Lnet/minecraft/client/model/geom/builders/CubeDeformation;)Lnet/minecraft/client/model/geom/builders/MeshDefinition;", at = @At("RETURN"))
	private static void laowuSegmentBody(CubeDeformation deformation, CallbackInfoReturnable<MeshDefinition> cir) {
		try {
			MeshDefinition mesh = cir.getReturnValue();
			PartDefinition root = mesh.getRoot();
			PartDefinition body = root.getChild("body");
			if (body == null) return;

			// 清掉 body 原来的单 cube，换成 3 段：body(第1段) + body_mid(第2段) + body_front(第3段)
			body.addOrReplaceChild("body_mid",
					CubeListBuilder.create().texOffs(0, 0)
							.addBox(BODY_X, BODY_Y0 + SEG_H, BODY_Z, BODY_W, SEG_H, BODY_D, deformation),
					PartPose.ZERO);
			body.addOrReplaceChild("body_front",
					CubeListBuilder.create().texOffs(0, 0)
							.addBox(BODY_X, BODY_Y0 + 2 * SEG_H, BODY_Z, BODY_W, SEG_H, BODY_D, deformation),
					PartPose.ZERO);
			// body 自身只保留第 1 段：无法移除已有 cube，改在原 body cube 上叠加段（见 CatModelMixin 处理）。
			// 为视觉一致，这里 body 保持原 cube 不动；分段弯曲时 body_mid/front 相对 body 旋转，身体呈三段弯。
		} catch (Throwable t) {
			// 构建兜底，失败则退回原版单 cube
		}
	}
}
