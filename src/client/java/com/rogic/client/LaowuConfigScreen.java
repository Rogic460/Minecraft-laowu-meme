package com.rogic.client;

import com.rogic.client.sound.AudioPool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
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
 * - 显示导入音频名（若有）
 * - 显示池内音频总数（固有 + 导入）
 * - 「打开音频文件夹」按钮：调系统文件管理器，把 .ogg 拖进去（触发整活时自动随机播放）
 *
 * 文本用 StringWidget（AbstractWidget 的子类，与按钮同一渲染管线，retained-mode 下可靠渲染；
 * 之前用 Screen.extractRenderState 直绘在 1.1.16/1.1.19 实测不显示，故改走 widget）。
 * 颜色烤进 Component（StringWidget 无 setColor），位置手动 setX 居中。
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

		addCentered(Component.literal("laowu meme 设置").withColor(0xFFFFFF), cx, 20);
		addCentered(Component.literal("固有音频（随机池）：").withColor(0xFFE066), cx, 60);

		int y = 88;
		for (String name : BUILTIN_NAMES) {
			addCentered(Component.literal(name).withColor(0xFFFFFF), cx, y);
			y += 22;
		}

		List<String> imported = listImported();
		for (String name : imported) {
			addCentered(Component.literal("[导入] " + name).withColor(0x66CCFF), cx, y);
			y += 22;
		}

		int total = BUILTIN_NAMES.size() + imported.size();
		addCentered(Component.literal("池内音频总数：" + total + "（固有 " + BUILTIN_NAMES.size() + " + 导入 " + imported.size() + "）").withColor(0x99FF99), cx, y + 12);
		addCentered(Component.literal("把 .ogg 拖进音频文件夹，触发整活时会自动随机播放（无需 F3+T）").withColor(0xAAAAAA), cx, y + 38);
		addCentered(Component.literal("MC 仅支持 Ogg Vorbis（.ogg）；mp3/wav 需先转换").withColor(0x888888), cx, y + 56);

		this.addRenderableWidget(Button.builder(Component.literal("打开音频文件夹"), b -> openSoundsFolder())
				.bounds(cx - 110, this.height - 64, 220, 20)
				.build());
		this.addRenderableWidget(Button.builder(Component.literal("返回"), b -> this.minecraft.setScreen(this.parent))
				.bounds(cx - 110, this.height - 38, 220, 20)
				.build());
	}

	private void addCentered(Component text, int cx, int y) {
		StringWidget w = new StringWidget(text, this.font);
		w.setX(cx - w.getWidth() / 2);
		w.setY(y);
		this.addRenderableWidget(w);
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
