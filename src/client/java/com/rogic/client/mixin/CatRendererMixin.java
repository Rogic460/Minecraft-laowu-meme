package com.rogic.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
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
		a.laowuSetFlat(cs.isFlattened(id));

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

		// 奶猫换皮：命名"奶猫"→ 强制使用 mod 自带的 cat_milkcat 贴图（与花色无关，所有变体共用）。
		// 与耄耋并列（独立 milkcatNamed 字段），互不干扰。
		boolean milkcat = cat.getCustomName() != null && "奶猫".equals(cat.getCustomName().getString());
		a.milkcatSetNamed(milkcat && !baby);
	}

	/**
	 * 命名"耄耋"的猫：底图换成 mod 自带、对应它自身颜色的哈基米贴图（颜色不变，只换美术风格）。
	 * 判定与贴图路径均在 extractRenderState 里从真实体算好存入 render state，
	 * 这里只消费——不依赖 nameTag 可见性（准星离开也保持）；幼猫不启用换皮，走原版贴图。
	 */
	@Inject(method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/CatRenderState;)Lnet/minecraft/resources/Identifier;", at = @At("HEAD"), cancellable = true)
	private void laowuMaodieTexture(CatRenderState state, CallbackInfoReturnable<Identifier> cir) {
		LaowuStateAccess a = (LaowuStateAccess) state;
		// 奶猫换皮优先级最高：命名"奶猫"→ 强制 cat_milkcat 贴图（与耄耋并列）
		if (a.milkcatIsNamed()) {
			// MC 加载纹理的 Identifier 路径不含 textures/ 前缀（自动加）与 .png 后缀（自动加）
			cir.setReturnValue(Identifier.fromNamespaceAndPath("laowu_meme", "entity/cat/cat_milkcat"));
			return;
		}
		if (a.maodieIsNamed()) {
			String p = a.maodieGetTexPath();
			if (p != null) {
				cir.setReturnValue(Identifier.fromNamespaceAndPath("laowu_meme", p));
			}
		}
	}

	/**
	 * 铲子拍扁：在 setupRotations（模型变换早期、实体本地坐标系）把 y 轴压扁。
	 * 非均匀缩放只能对 PoseStack 做（LivingEntityRenderState.scale 是单 float，只能整体缩放）。
	 * TAIL 保证在 super.setupRotations 与猫躺下平移之后施加，模型渲染时即被压扁。
	 */
	@Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/CatRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V", at = @At("TAIL"))
	private void laowuFlat(CatRenderState state, PoseStack poseStack, float ageInTicks, float rotationYaw, CallbackInfo ci) {
		try {
			if (((LaowuStateAccess) state).laowuIsFlat()) {
				// 拍扁：y 轴压到 0.175（先 0.35 再扁一半）。四肢由 CatModelMixin 拉长外撇形成"干"字形。
				poseStack.scale(1f, 0.175f, 1f);
			}
		} catch (Throwable t) {
			// 渲染兜底，绝不崩
		}
	}
}
