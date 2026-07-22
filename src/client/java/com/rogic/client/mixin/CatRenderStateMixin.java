package com.rogic.client.mixin;

import com.rogic.client.render.LaowuStateAccess;
import net.minecraft.client.renderer.entity.state.CatRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 给 CatRenderState 加两个 @Unique 字段，承载"是否锁定 + 歪头方向"，
 * 由 CatRenderStateMixin(CatRenderer) 在 extractRenderState 写入，
 * 由 CatModelMixin 在 setupAnim(TAIL) 读取。
 * 同一 CatRenderState 实例从 extractRenderState 流到 setupAnim，故可靠传递，
 * 不再需要 WeakHashMap 跨 mixin 桥（那套在 26.1 渲染管线里不可靠）。
 */
@Mixin(CatRenderState.class)
public abstract class CatRenderStateMixin implements LaowuStateAccess {

	@Unique
	public boolean laowuActive;

	@Unique
	public float laowuRoll;

	@Override
	public boolean laowuIsActive() {
		return laowuActive;
	}

	@Override
	public float laowuGetRoll() {
		return laowuRoll;
	}

	@Override
	public void laowuSetActive(boolean v) {
		laowuActive = v;
	}

	@Override
	public void laowuSetRoll(float v) {
		laowuRoll = v;
	}
}
