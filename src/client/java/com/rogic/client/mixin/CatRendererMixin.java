package com.rogic.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rogic.client.ClientMemeState;
import com.rogic.maodie.MaodieBlueprint;
import net.minecraft.client.renderer.entity.CatRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Cat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 经典管线（1.21.0/1.21.1）：无 extractRenderState / CatRenderState，放大改在 CatRenderer.scale()。
 *
 * 关键：render() 声明在父类 EntityRenderer，并不在 CatRenderer 上——mixin 直接注入继承方法时，
 * loom 无法为它生成 refMap，运行期 InvalidInjectionException 导致 CatRenderer 类加载失败→黑屏。
 * scale(Cat, PoseStack, float) 是 CatRenderer 自己声明的方法（javap 实证），可被正常 remap。
 * scale 在 render 的 push/pop 作用域内被调用，直接在 PoseStack 上乘 1.25 即整体放大，无需 push/pop。
 * 仅渲染不改碰撞箱。try/catch 兜底防黑屏。
 *
 * 耄耋换皮（1.3.0）：经典管线 getTextureLocation(Cat) 直接传实体，判定直接读 getCustomName()，
 * 无 RenderState 中间层，也不依赖 nameTag 可见性（准星移开仍保持）。幼猫不换皮（走原版贴图）。
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

	/**
	 * 命名"耄耋"的成猫：底图换成 mod 自带、对应它自身花色的哈基米贴图（颜色不变，只换美术风格）。
	 * 判定直接读真实体自定义名（经典管线 getTextureLocation 有 Cat 实体，无需 nameTag）。
	 * 幼猫不换皮（用户定：幼猫保持原版贴图）。
	 */
	@Inject(method = "getTextureLocation(Lnet/minecraft/world/entity/animal/Cat;)Lnet/minecraft/resources/ResourceLocation;", at = @At("HEAD"), cancellable = true)
	private void laowuMaodieTexture(Cat cat, CallbackInfoReturnable<ResourceLocation> cir) {
		try {
			if (cat.isBaby()) return;
			if (cat.getCustomName() == null) return;
			if (!MaodieBlueprint.MAODIE_NAME.equals(cat.getCustomName().getString())) return;
			ResourceLocation modTex = maodieTextureFor(cat);
			if (modTex != null) {
				cir.setReturnValue(modTex);
			}
		} catch (Throwable t) {
			// 静默兜底，绝不崩渲染器
		}
	}

	/** 按猫变体返回 mod 内置哈基米贴图（cat_<花色>.png）；未知路径返回 null（走原版）。 */
	private static ResourceLocation maodieTextureFor(Cat cat) {
		// 1.21.0/1.21.1 经典管线：Cat.getVariant()（注意：26.x 叫 getCatVariant()，1.21.x 叫 getVariant()）
		// → CatVariant.texture() 返回当前贴图 ResourceLocation。
		// 与 1.21.11 同款策略：保留原版路径前缀（textures/entity/cat/ 或 entity/cat/），只补 cat_ 前缀——
		// 1.21 系纹理加载器按 Identifier 原样找 assets/<ns>/<path>.png，原版 CatVariant.texture()
		// 返回带 textures/ 的完整路径，保留它才能命中 mod 贴图 assets/laowu_meme/textures/entity/cat/cat_<花色>.png。
		ResourceLocation v = cat.getVariant().value().texture();
		String p = v.getPath();
		// 兼容两种原版路径格式：textures/entity/cat/<花色> 或 entity/cat/<花色>
		String[] prefixes = { "textures/entity/cat/", "entity/cat/" };
		for (String PREFIX : prefixes) {
			if (p.startsWith(PREFIX)) {
				String name = p.substring(PREFIX.length());
				if (name.endsWith(".png")) name = name.substring(0, name.length() - 4);
				if (name.startsWith("cat_")) {
					// 原版已带 cat_ 前缀（部分版本）：直接映射
					return ResourceLocation.fromNamespaceAndPath("laowu_meme", PREFIX + name);
				}
				return ResourceLocation.fromNamespaceAndPath("laowu_meme", PREFIX + "cat_" + name);
			}
		}
		return null;
	}
}
