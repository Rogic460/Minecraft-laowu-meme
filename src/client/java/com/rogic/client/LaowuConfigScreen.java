package com.rogic.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.awt.Desktop;
import java.io.File;
import java.util.List;

/**
 * 轻量配置屏（手写，不依赖 YACL 以符合 mod 轻量定位）。
 * - 显示固有音频（随机池）与显示名
 * - 显示池内音频总数（固有 + 导入）
 * - 「打开音频文件夹」按钮：调系统文件管理器，把 .ogg 拖进去（F3+T 重载资源后生效）
 * 无 WatchService 常驻线程：列表在打开界面时扫描，符合轻量设计。
 *
 * 26.1.2 渲染模型为 retained-mode：覆盖 extractRenderState(GuiGraphicsExtractor,...) 绘制文本，
 * 控件由框架在提取渲染状态时自动绘制。
 */
public class LaowuConfigScreen extends Screen {
	private final Screen parent;
	private static final List<String> BUILTIN_NAMES = List.of("[那个那个]", "[老吴凄凉]", "[战吼]");

	public LaowuConfigScreen(Screen parent) {
		super(Component.literal("laowu meme 设置"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		this.addRenderableWidget(Button.builder(Component.literal("打开音频文件夹"), b -> openSoundsFolder())
				.bounds(cx - 110, this.height - 64, 220, 20)
				.build());
		this.addRenderableWidget(Button.builder(Component.literal("返回"), b -> this.minecraft.setScreen(this.parent))
				.bounds(cx - 110, this.height - 38, 220, 20)
				.build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(gfx, mouseX, mouseY, partialTick);
		Font font = this.font;
		int cx = this.width / 2;
		gfx.centeredText(font, Component.literal("laowu meme 设置"), cx, 20, 0xFFFFFF);

		gfx.centeredText(font, Component.literal("固有音频（随机池）："), cx, 60, 0xFFE066);
		int y = 88;
		for (String name : BUILTIN_NAMES) {
			gfx.centeredText(font, Component.literal(name), cx, y, 0xFFFFFF);
			y += 22;
		}

		int imported = countImported();
		int total = BUILTIN_NAMES.size() + imported;
		gfx.centeredText(font,
				Component.literal("池内音频总数：" + total + "（固有 " + BUILTIN_NAMES.size() + " + 导入 " + imported + "）"),
				cx, y + 12, 0x99FF99);

		gfx.centeredText(font,
				Component.literal("把 .ogg 拖进音频文件夹，游戏内按 F3+T 重载资源即生效"),
				cx, y + 38, 0xAAAAAA);
		gfx.centeredText(font,
				Component.literal("MC 仅支持 Ogg Vorbis（.ogg）；mp3/wav 需先转换"),
				cx, y + 56, 0x888888);
	}

	private void openSoundsFolder() {
		File dir = getSoundsDir();
		try {
			if (!dir.exists()) dir.mkdirs();
			if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(dir);
		} catch (Exception ignored) {
		}
	}

	private static File getSoundsDir() {
		return new File(Minecraft.getInstance().gameDirectory, "config/laowu_meme/sounds");
	}

	private static int countImported() {
		File dir = getSoundsDir();
		if (!dir.exists()) return 0;
		File[] files = dir.listFiles((d, n) -> n.endsWith(".ogg"));
		return files == null ? 0 : files.length;
	}
}
