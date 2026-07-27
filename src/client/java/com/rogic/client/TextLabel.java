package com.rogic.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

/**
 * 静态文本标签：继承 AbstractWidget（与 Button 同一渲染管线，26.1.2 已验证 Button 可渲染），
 * 在 extractWidgetRenderState 里画字。直接写在 Screen.extractRenderState 或裸 Renderable 里的字
 * 不进渲染管线（v1.1.16/1.1.17 实测翻车），故必须用 AbstractWidget 派生。
 */
public class TextLabel extends AbstractWidget {
	private final Font font;
	private final int color;
	private final boolean centered;

	public TextLabel(Font font, Component message, int x, int y, int color, boolean centered) {
		super(x, y, 1, 1, message);
		this.font = font;
		this.color = color;
		this.centered = centered;
		this.active = true;
		this.visible = true;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
		if (centered) {
			gfx.centeredText(font, getMessage(), getX(), getY(), color);
		} else {
			gfx.text(font, getMessage(), getX(), getY(), color);
		}
	}

	@Override
	protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
