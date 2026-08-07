package com.rogic.client.mixin;

import net.minecraft.client.model.animal.feline.AdultFelineModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * B 方案（多段关节模型）v2：mixin EntityModelSet.bakeLayer，猫模型用多段 body。
 *
 * v1 失败原因（用户实测"畸形"）：子段 cube PartPose=ZERO → 旋转中心=body 局部原点（远离段中心）
 * → 大角度旋转时 cube 大幅位移 → 身体零件飞散。
 * v2 修正：每段 cube 的 PartPose 偏移到【连接点】（前一段末端），cube 从连接点延伸，
 * 旋转时绕连接点转 → 真正的关节弯曲。
 *
 * 实现：以原版 AdultFelineModel.createBodyMesh 为底，给 body 加 body_mid/body_front 子节点
 * （连接点 pose）。原 body cube（16 高）作为后段，段2/段3 从连接点弯出。
 */
@Mixin(EntityModelSet.class)
public abstract class FelineBodySegmentMixin {

	private static final float BODY_X = -2.0f;
	private static final float BODY_Z = -8.0f;
	private static final float BODY_W = 4.0f;
	private static final float BODY_H = 16.0f;
	private static final float BODY_D = 6.0f;
	/** 段1 末端 y = 段2 连接点（body 局部坐标，y 沿猫纵向） */
	private static final float SEG1_END = 3.0f + BODY_H / 3.0f;    // 8.33
	/** 段2 末端 y = 段3 连接点 */
	private static final float SEG2_END = 3.0f + 2 * BODY_H / 3.0f; // 13.66
	private static final float SEG_H = BODY_H / 3.0f;               // 5.33

	@Inject(method = "bakeLayer(Lnet/minecraft/client/model/geom/ModelLayerLocation;)Lnet/minecraft/client/model/geom/ModelPart;", at = @At("RETURN"), cancellable = true)
	private void laowuMultiSegmentBody(ModelLayerLocation loc, CallbackInfoReturnable<ModelPart> cir) {
		try {
			if (loc == null || !loc.equals(ModelLayers.CAT)) return;
			cir.setReturnValue(buildSegmentedCat());
		} catch (Throwable t) {
			// 失败保留原版（绝不黑屏）
		}
	}

	private static ModelPart buildSegmentedCat() {
		// 原版全部部件为底（head/4legs/tail1/tail2 原样），只给 body 加多段
		MeshDefinition mesh = AdultFelineModel.createBodyMesh(new CubeDeformation(-0.02f));
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.getChild("body");
		if (body != null) {
			// 段2：连接点=段1末端（body 局部 y=8.33），cube 从连接点向前延伸 5.33
			body.addOrReplaceChild("body_mid",
					CubeListBuilder.create().texOffs(0, 0)
							.addBox(BODY_X, 0.0f, BODY_Z, BODY_W, SEG_H, BODY_D),
					PartPose.offset(0.0f, SEG1_END, 0.0f));
			// 段3：连接点=段2末端（y=13.66）
			body.addOrReplaceChild("body_front",
					CubeListBuilder.create().texOffs(0, 0)
							.addBox(BODY_X, 0.0f, BODY_Z, BODY_W, SEG_H, BODY_D),
					PartPose.offset(0.0f, SEG2_END, 0.0f));
		}
		return LayerDefinition.create(mesh, 512, 256).bakeRoot();
	}
}
