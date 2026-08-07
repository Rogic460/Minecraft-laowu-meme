package com.rogic.client.sound;

import net.minecraft.sounds.SoundEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;

/**
 * 客户端本地音频池：管理「可随机播放」的音频集合 + 每条音频的启用/禁用状态。
 * - 固有音频：三段注册表 SoundEvent（[那个那个]/[老吴凄凉]/[战吼]），开箱即用、零操作。
 * - 用户导入：扫描 config/laowu_meme/sounds/*.ogg，触发整活时与固有音频一起随机播放，
 *   直接从磁盘读取（无需 F3+T、不进资源包，由 SoundBufferLibraryMixin 提供字节流）。
 * - 启用/禁用：每条音频可单独启用或禁用，状态持久化到 enabled.properties；
 *   random() 只从启用条目里抽，确保禁用条目永不会被随机出来。
 *
 * 随机播放不依赖服务端指定的 soundId —— 实现「客户端各自随机」——
 * 多人下每只猫的动作仍服务端同步，但各自听到的音频可能不同（各玩各的梗，符合整活定位）。
 */
public class AudioPool {
	/** 固有音频 key（顺序：laowu2 / qiliang / zhanhou）——SoundEvent 运行时从 ModSounds 实时取（懒加载） */
	private static final List<String> BUILTIN_KEYS = List.of("laowu2", "qiliang", "zhanhou");
	/** 固有音频：sound 注册名 -> GUI 显示名 */
	public static final Map<String, String> BUILTIN_DISPLAY = new LinkedHashMap<>();
	static {
		BUILTIN_DISPLAY.put("laowu2", "[那个那个]");
		BUILTIN_DISPLAY.put("qiliang", "[老吴凄凉]");
		BUILTIN_DISPLAY.put("zhanhou", "[战吼]");
	}

	/** 固有音频：key -> SoundEvent（运行时取，避免静态块提前捕获 null——NeoForge 下 ModSounds 字段 setup 才赋值） */
	private static SoundEvent builtinEvent(String key) {
		return switch (key) {
			case "laowu2" -> ModSounds.LAOWU2;
			case "qiliang" -> ModSounds.QILIANG;
			case "zhanhou" -> ModSounds.ZHANHOU;
			default -> null;
		};
	}

	private static final List<String> IMPORTED = new ArrayList<>();

	public static void init() {
		refreshImported();  // 先扫磁盘，确保覆盖所有已知 key
	}

	/** 重新扫描 config/laowu_meme/sounds/*.ogg（去掉 .ogg 后缀作为显示/匹配名），排序后存入 IMPORTED */
	public static void refreshImported() {
		IMPORTED.clear();
		File dir = getSoundsDir();
		if (!dir.exists()) return;
		File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".ogg"));
		if (files == null) return;
		for (File f : files) {
			IMPORTED.add(stripExt(f.getName()));
		}
		Collections.sort(IMPORTED);
	}

	public static List<String> importedNames() {
		return new ArrayList<>(IMPORTED);
	}

	public static int importedCount() {
		return IMPORTED.size();
	}

	public static int builtinCount() {
		return BUILTIN_KEYS.size();
	}

	/** 固有 key 列表（顺序与 BUILTIN_KEYS 一致），用于 UI 列出所有条目 */
	public static List<String> builtinKeys() {
		return new ArrayList<>(BUILTIN_KEYS);
	}

	/** 导入 key 列表（顺序与 IMPORTED 一致） */
	public static List<String> importedKeys() {
		List<String> keys = new ArrayList<>(IMPORTED.size());
		for (String n : IMPORTED) keys.add("imported:" + n);
		return keys;
	}

	public static boolean isEnabled(String key) {
		return LaowuClientConfig.isEnabled(key);
	}

	public static void setEnabled(String key, boolean enabled) {
		LaowuClientConfig.setEnabled(key, enabled);
	}

	/** 翻转 enabled 状态，返回新值 */
	public static boolean toggleEnabled(String key) {
		boolean now = !isEnabled(key);
		setEnabled(key, now);
		return now;
	}

	/** 从 enabled + imported 合并池随机挑一段（只抽启用的）；全空返回 null */
	public static PlayTarget random() {
		List<PlayTarget> pool = new ArrayList<>();
		for (String k : BUILTIN_KEYS) {
			if (isEnabled("builtin:" + k)) {
				SoundEvent ev = builtinEvent(k);
				if (ev != null) pool.add(PlayTarget.builtin(ev));
			}
		}
		for (String n : IMPORTED) {
			if (isEnabled("imported:" + n)) {
				pool.add(PlayTarget.imported(n));
			}
		}
		if (pool.isEmpty()) return null;
		return pool.get((int) (Math.random() * pool.size()));
	}

	/** 一次随机结果：要么一段固有 SoundEvent，要么一个导入音频名（base name，无 .ogg） */
	public static final class PlayTarget {
		public final SoundEvent event;
		public final String importedName;
		private PlayTarget(SoundEvent event, String importedName) {
			this.event = event;
			this.importedName = importedName;
		}
		public static PlayTarget builtin(SoundEvent event) { return new PlayTarget(event, null); }
		public static PlayTarget imported(String name) { return new PlayTarget(null, name); }
		public boolean isImported() { return importedName != null; }
	}

	public static File getSoundsDir() {
		return new File(getConfigDir(), "sounds");
	}

	public static File getConfigDir() {
		return new File(Minecraft.getInstance().gameDirectory, "config/laowu_meme");
	}

	private static String stripExt(String name) {
		if (name.toLowerCase().endsWith(".ogg")) return name.substring(0, name.length() - 4);
		return name;
	}
}