package com.rogic.client.render;

/**
 * 让 CatRenderState 通过 mixin 携带渲染状态：
 *  - 老吴整活（锁定 + 歪头方向）
 *  - 耄耋绑定（猫是否处于耄耋结构中，需要转头盯最近玩家）
 *  - 奶猫换皮（命名"奶猫"→ 强制使用 cat_milkcat 贴图，与耄耋并列）
 * CatRenderStateMixin 实现本接口并加 @Unique 字段；同一 CatRenderState 实例
 * 从 CatRendererMixin.extractRenderState（有 Cat 实体，能取 id）流到
 * CatModelMixin.setupAnim（只有 state，无 id），靠本接口在两者间传递。
 */
public interface LaowuStateAccess {
	boolean laowuIsActive();

	float laowuGetRoll();

	void laowuSetActive(boolean v);

	void laowuSetRoll(float v);

	/** 耄耋：猫是否处于结构中（客户端收包填充），驱动"转头盯最近玩家"。 */
	boolean maodieIsBound();

	void maodieSetBound(boolean v);

	/** 耄耋换皮：猫自定义名是否为"耄耋"（直接读真实体，不依赖 nameTag 可见性）。 */
	boolean maodieIsNamed();

	void maodieSetNamed(boolean v);

	/** 耄耋换皮：对应的贴图路径（已剥离 _baby 后缀，命名空间为 laowu_meme）。null 表示不换。 */
	String maodieGetTexPath();

	void maodieSetTexPath(String v);

	/** 铲子拍扁：猫是否处于扁平态（渲染时 setupRotations 压扁 y 轴）。 */
	boolean laowuIsFlat();

	void laowuSetFlat(boolean v);

	/** 奶猫换皮：猫自定义名是否为"奶猫"（独立于耄耋，固定使用 cat_milkcat 贴图）。 */
	boolean milkcatIsNamed();

	void milkcatSetNamed(boolean v);
}
