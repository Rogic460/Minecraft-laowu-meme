package com.rogic.client.mixin;

import net.minecraft.client.renderer.entity.state.CatRenderState;

import java.util.WeakHashMap;

/**
 * 渲染状态桥：CatRendererMixin 在 extractRenderState 时把"是否锁定 / 歪头方向"
 * 写进来，CatModelMixin 在 setupAnim 时读出来给头部加 roll。
 * 用 WeakHashMap 以 CatRenderState 实例为键，避免跨实体串数据、且能随 GC 回收。
 */
public final class RenderStateHolder {
	public static final WeakHashMap<CatRenderState, RollData> DATA = new WeakHashMap<>();

	public static final class RollData {
		public boolean active;
		public float roll;
	}

	private RenderStateHolder() {}
}
