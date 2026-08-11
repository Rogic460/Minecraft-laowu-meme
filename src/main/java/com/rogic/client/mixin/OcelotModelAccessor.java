package com.rogic.client.mixin;

import net.minecraft.client.model.OcelotModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 经典管线（1.21.x）：CatModel 的头部/身体/尾巴/四肢部件字段声明在父类 OcelotModel，
 * 不在 CatModel 自身。Mixin 的 @Shadow 字段只认目标类自身、不上溯父类，直接 @Shadow 会
 * 报 "@Shadow field field_XXXX was not located in class_3680" 直接黑屏。
 * 这里改用 @Accessor 挂在 OcelotModel（字段所在类）上暴露 getter，CatModelMixin 的
 * setupAnim 注入里把 this 强转成 OcelotModelAccessor 取部件即可。
 */
@Mixin(OcelotModel.class)
public interface OcelotModelAccessor {
	@Accessor("head")
	ModelPart laowuHead();

	@Accessor("body")
	ModelPart laowuBody();

	@Accessor("tail2")
	ModelPart laowuTail2();

	@Accessor("leftHindLeg")
	ModelPart laowuLeftHindLeg();

	@Accessor("rightHindLeg")
	ModelPart laowuRightHindLeg();

	@Accessor("leftFrontLeg")
	ModelPart laowuLeftFrontLeg();

	@Accessor("rightFrontLeg")
	ModelPart laowuRightFrontLeg();
}
