package com.rogic.client.render;

/**
 * 让 CatRenderState 通过 mixin 携带"老吴整活"的渲染状态。
 * CatRenderStateMixin 实现本接口并加 @Unique 字段；同一 CatRenderState 实例
 * 从 CatRendererMixin.extractRenderState（有 Cat 实体，能取 id/roll）流到
 * CatModelMixin.setupAnim（只有 state，无 id），靠本接口在两者间传递 active/roll。
 */
public interface LaowuStateAccess {
	boolean laowuIsActive();

	float laowuGetRoll();

	void laowuSetActive(boolean v);

	void laowuSetRoll(float v);
}
