package com.rogic.client.render;

import net.minecraft.client.renderer.entity.state.CatRenderState;

import java.util.WeakHashMap;

/**
 * 渲染状态桥：CatRendererMixin 在 extractRenderState 时把"是否锁定 / 歪头方向"
 * 写进来，CatModelMixin 在 setupAnim 时读出来给头部加 roll。
 * 用 WeakHashMap 以 CatRenderState 实例为键，避免跨实体串数据、且能随 GC 回收。
 *
 * 注意：本类必须放在 mixin 包（com.rogic.client.mixin）之外。mixin.json 声明的
 * mixin 包内的类由 Mixin 处理器接管，不能被普通代码直接引用，否则会抛
 * IllegalClassLoadError（... is in a defined mixin package ... and cannot be referenced directly）。
 */
public final class RenderStateHolder {
	public static final WeakHashMap<CatRenderState, RollData> DATA = new WeakHashMap<>();

	public static final class RollData {
		public boolean active;
		public float roll;
	}

	private RenderStateHolder() {}
}
