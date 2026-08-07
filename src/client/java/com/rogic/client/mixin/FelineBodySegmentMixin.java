package com.rogic.client.mixin;

import net.minecraft.client.model.animal.feline.AdultFelineModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * B 方案失败回退：放弃 body_mid/body_front 子段添加。
 * 原因（2026-08-07 用户实测反馈"畸形"）：子段旋转中心是 body 局部原点（远离段中心），
 * 大角度旋转时 cube 大幅位移（旋转半径 7-8 + 角度 1.25 rad → 位移 7+），视觉上身体零件飞散。
 * 参考图那种分段弯曲需要专门的关节模型（每段绕连接点旋转），MC 原版猫模型结构不支持。
 * 蜷缩退回 A 方案思路：head 下压 + 腿蜷 + 尾巴下卷（无 body 分段）。
 */
@Mixin(AdultFelineModel.class)
public abstract class FelineBodySegmentMixin {

	@Inject(method = "createBodyMesh(Lnet/minecraft/client/model/geom/builders/CubeDeformation;)Lnet/minecraft/client/model/geom/builders/MeshDefinition;", at = @At("RETURN"))
	private static void laowuSegmentBody(CubeDeformation deformation, CallbackInfoReturnable<MeshDefinition> cir) {
		// 故意留空——B 方案不可行
	}
}