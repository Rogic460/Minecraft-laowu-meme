package com.rogic.client.mixin;

import com.rogic.client.render.LaowuStateAccess;
import net.minecraft.client.renderer.entity.state.CatRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 给 CatRenderState 加 @Unique 字段，承载渲染状态：
 *  - 老吴：锁定 + 歪头方向
 *  - 耄耋：是否处于结构中（转头盯最近玩家）
 * 由 CatRendererMixin 在 extractRenderState 写入，由 CatModelMixin 在 setupAnim(TAIL) 读取。
 * 同一 CatRenderState 实例从 extractRenderState 流到 setupAnim，故可靠传递，
 * 不再需要 WeakHashMap 跨 mixin 桥（那套在 26.1 渲染管线里不可靠）。
 */
@Mixin(CatRenderState.class)
public abstract class CatRenderStateMixin implements LaowuStateAccess {

	@Unique
	public boolean laowuActive;

	@Unique
	public float laowuRoll;

	@Unique
	public boolean maodieBound;

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

	@Override
	public boolean maodieIsBound() {
		return maodieBound;
	}

	@Override
	public void maodieSetBound(boolean v) {
		maodieBound = v;
	}

	@Unique
	public boolean maodieNamed;

	@Unique
	public String maodieTexPath;

	@Override
	public boolean maodieIsNamed() {
		return maodieNamed;
	}

	@Override
	public void maodieSetNamed(boolean v) {
		maodieNamed = v;
	}

	@Override
	public String maodieGetTexPath() {
		return maodieTexPath;
	}

	@Override
	public void maodieSetTexPath(String v) {
		maodieTexPath = v;
	}
}
