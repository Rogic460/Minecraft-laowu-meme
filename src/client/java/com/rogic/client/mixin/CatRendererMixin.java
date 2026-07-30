package com.rogic.client.mixin;

import com.rogic.client.ClientMemeState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.CatRenderer;
import net.minecraft.world.entity.animal.Cat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 经典管线（1.21.0/1.21.1）：无 extractRenderState / CatRenderState，放大改在 CatRenderer.scale()。
 *
 * 关键：render() 声明在父类 EntityRenderer，并不在 CatRenderer 上——mixin 直接注入继承方法时，
 * loom 无法为它生成 refMap，运行期 InvalidInjectionException 导致 CatRenderer 类加载失败→黑屏。
 * scale(Cat, PoseStack, float) 是 CatRenderer 自己声明的方法（javap 实证），可被正常 remap。
 * scale 在 render 的 push/pop 作用域内被调用，直接在 PoseStack 上乘 1.25 即整体放大，无需 push/pop。
 * 仅渲染不改碰撞箱。try/catch 兜底防黑屏。
 */
@Mixin(CatRenderer.class)
public class CatRendererMixin {

	@Inject(method = "scale(Lnet/minecraft/world/entity/animal/Cat;Lcom/mojang/blaze3d/vertex/PoseStack;F)V", at = @At("HEAD"))
	private void laowuScale(Cat cat, PoseStack poseStack, float partialTick, CallbackInfo ci) {
		try {
			if (ClientMemeState.get().isActive(cat.getId())) {
				poseStack.scale(1.25f, 1.25f, 1.25f);
			}
		} catch (Throwable t) {
			// 渲染兜底：任何异常都不影响其他实体渲染，避免黑屏
		}
	}
}
