package com.rogic.client;

import com.rogic.client.sound.AudioPool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * 轻量配置屏（手写，不依赖 YACL 以符合 mod 轻量定位）。
 * - 显示固有音频（随机池）与显示名
 * - 显示池内音频总数（固有 + 导入）
 * - 「打开音频文件夹」按钮：调系统文件管理器，把 .ogg 拖进去（F3+T 重载资源后生效）
 * 无 WatchService 常驻线程：列表在打开界面时扫描，符合轻量设计。
 *
 * 26.1.2 retained-mode：所有可视元素都做成 AbstractWidget（背景由 Screen.extractBackground 提供），
 * 文本用 {@link TextLabel}（继承 AbstractWidget，与按钮同一渲染管线）。
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
		Font font = this.font;
		int cx = this.width / 2;

		// 标题 + 副标题
		this.addRenderableWidget(new TextLabel(font, Component.literal("laowu meme 设置"), cx, 20, 0xFFFFFF, true));
		this.addRenderableWidget(new TextLabel(font, Component.literal("固有音频（随机池）："), cx, 60, 0xFFE066, true));

		// 三段固有音频名
		int y = 88;
		for (String name : BUILTIN_NAMES) {
			this.addRenderableWidget(new TextLabel(font, Component.literal(name), cx, y, 0xFFFFFF, true));
			y += 22;
		}

		// 导入音频名（若有）
		List<String> imported = listImported();
		for (String name : imported) {
			this.addRenderableWidget(new TextLabel(font, Component.literal("[导入] " + name), cx, y, 0x66CCFF, true));
			y += 22;
		}

		// 池内总数 + 提示
		int total = BUILTIN_NAMES.size() + imported.size();
		this.addRenderableWidget(new TextLabel(font,
				Component.literal("池内音频总数：" + total + "（固有 " + BUILTIN_NAMES.size() + " + 导入 " + imported.size() + "）"),
				cx, y + 12, 0x99FF99, true));
		this.addRenderableWidget(new TextLabel(font,
				Component.literal("把 .ogg 拖进音频文件夹，触发整活时会自动随机播放（无需 F3+T）"),
				cx, y + 38, 0xAAAAAA, true));
		this.addRenderableWidget(new TextLabel(font,
				Component.literal("MC 仅支持 Ogg Vorbis（.ogg）；mp3/wav 需先转换"),
				cx, y + 56, 0x888888, true));

		// 按钮
		this.addRenderableWidget(Button.builder(Component.literal("打开音频文件夹"), b -> openSoundsFolder())
				.bounds(cx - 110, this.height - 64, 220, 20)
				.build());
		this.addRenderableWidget(Button.builder(Component.literal("返回"), b -> this.minecraft.setScreen(this.parent))
				.bounds(cx - 110, this.height - 38, 220, 20)
				.build());
	}

	private static List<String> listImported() {
		AudioPool.refreshImported();
		return AudioPool.importedNames();
	}

	private void openSoundsFolder() {
		File dir = getSoundsDir();
		try {
			if (!dir.exists() && !dir.mkdirs()) {
				notify("无法创建文件夹：" + dir.getAbsolutePath());
				return;
			}
			boolean opened = false;
			if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
				try {
					Desktop.getDesktop().open(dir);
					opened = true;
				} catch (IOException ignored) {
				}
			}
			if (!opened) opened = openViaOs(dir);
			if (opened) {
				notify("已打开音频文件夹");
			} else {
				copyToClipboard(dir.getAbsolutePath());
				notify("无法打开，路径已复制到剪贴板");
			}
		} catch (Exception e) {
			copyToClipboard(dir.getAbsolutePath());
			notify("无法打开，路径已复制到剪贴板");
		}
	}

	private static boolean openViaOs(File dir) {
		try {
			String os = System.getProperty("os.name").toLowerCase();
			if (os.contains("win")) {
				Runtime.getRuntime().exec(new String[]{"explorer.exe", dir.getAbsolutePath()});
			} else if (os.contains("mac")) {
				Runtime.getRuntime().exec(new String[]{"open", dir.getAbsolutePath()});
			} else {
				Runtime.getRuntime().exec(new String[]{"xdg-open", dir.getAbsolutePath()});
			}
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private void notify(String msg) {
		if (this.minecraft != null && this.minecraft.getToastManager() != null) {
			this.minecraft.getToastManager().addToast(
					new SystemToast(SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
							Component.literal("laowu meme"), Component.literal(msg)));
		}
	}

	private static void copyToClipboard(String text) {
		try {
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
		} catch (Exception ignored) {
		}
	}

	private static File getSoundsDir() {
		return new File(Minecraft.getInstance().gameDirectory, "config/laowu_meme/sounds");
	}
}
