package com.rogic.client.mixin;

import com.rogic.client.ClientMemeState;
import com.rogic.client.render.LaowuStateAccess;
import net.minecraft.client.renderer.entity.CatRenderer;
import net.minecraft.client.renderer.entity.state.CatRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.feline.Cat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 在 CatRenderer 提取渲染状态时：
 *  1) 对锁定猫把 scale 放大 50%（仅渲染，不改碰撞箱）；
 *  2) 把"是否锁定 + 歪头方向"写入 CatRenderState（经 LaowuStateAccess），
 *     供 CatModelMixin 在 setupAnim(TAIL) 读取并设 head.zRot。
 *
 * 关键事实（MC 26.1 实测，字节码核实）：
 *  - AdultFelineModel.setupAnim 会主动读写 head.zRot（不只是 xRot/yRot），
 *    所以**必须在 setupAnim 的 TAIL 设 zRot**，extractRenderState 阶段设会被覆盖。
 */
@Mixin(CatRenderer.class)
public class CatRendererMixin {

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/animal/feline/Cat;Lnet/minecraft/client/renderer/entity/state/CatRenderState;F)V", at = @At("TAIL"))
	private void laowuPopulate(Cat cat, CatRenderState state, float partialTick, CallbackInfo ci) {
		ClientMemeState cs = ClientMemeState.get();
		int id = cat.getId();
		boolean active = cs.isActive(id);
		float roll = cs.getRollSign(id);

		if (active) {
			state.scale *= 1.25f;
		}

		// 把状态写入 render state，供模型层 setupAnim(TAIL) 读取。
		// CatRenderState 经 CatRenderStateMixin 实现 LaowuStateAccess。
		LaowuStateAccess a = (LaowuStateAccess) state;
		a.laowuSetActive(active);
		a.laowuSetRoll(roll);
		a.maodieSetBound(cs.isMaodieBound(id));

		// 耄耋换皮：直接读真实体自定义名（nameTag 仅在名字可见时填充，会随准星离开而失效 → BUG1）。
		// 婴儿猫不换皮（用户定：幼猫保持原版贴图，避免剥离 _baby 走成猫图导致黑紫报错）。
		boolean named = cat.getCustomName() != null && "耄耋".equals(cat.getCustomName().getString());
		boolean baby = cat.isBaby();
		a.maodieSetNamed(named && !baby);
		if (named && !baby && state.texture != null) {
			a.maodieSetTexPath(state.texture.getPath());
		} else {
			a.maodieSetTexPath(null);
		}
	}

	/**
	 * 命名"耄耋"的猫：底图换成 mod 自带、对应它自身颜色的哈基米贴图（颜色不变，只换美术风格）。
	 * 判定与贴图路径均在 extractRenderState 里从真实体算好存入 render state，
	 * 这里只消费——不依赖 nameTag 可见性（准星离开也保持）；幼猫不启用换皮，走原版贴图。
	 */
	@Inject(method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/CatRenderState;)Lnet/minecraft/resources/Identifier;", at = @At("HEAD"), cancellable = true)
	private void laowuMaodieTexture(CatRenderState state, CallbackInfoReturnable<Identifier> cir) {
		LaowuStateAccess a = (LaowuStateAccess) state;
		if (a.maodieIsNamed()) {
			String p = a.maodieGetTexPath();
			if (p != null) {
				// 1.21.11 原版贴图路径无 cat_ 前缀（entity/cat/tabby），mod 内置贴图沿用 26.x 命名（entity/cat/cat_tabby）
				// → 补前缀，否则 laowu_meme:entity/cat/tabby 找不到资源 → 黑紫（2026-08-05 实测 BUG 修复）
				cir.setReturnValue(Identifier.fromNamespaceAndPath("laowu_meme", mapMaodiePath(p)));
			}
		}
	}

	/** 1.21.x 原版猫贴图 <花色>.png → mod 内置 cat_<花色>.png 的路径映射；已带前缀或非猫路径原样返回。 */
	private static String mapMaodiePath(String p) {
		final String PREFIX = "entity/cat/";
		if (p.startsWith(PREFIX)) {
			String name = p.substring(PREFIX.length());
			if (!name.startsWith("cat_")) {
				return PREFIX + "cat_" + name;
			}
		}
		return p;
	}
}
