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
 * 客户端本地音频池：管理「可随机播放」的音频集合。
 * - 固有音频：三段注册表 SoundEvent（[那个那个]/[老吴凄凉]/[战吼]），开箱即用、零操作。
 * - 用户导入：扫描 config/laowu_meme/sounds/*.ogg，触发整活时与固有音频一起随机播放，
 *   直接从磁盘读取（无需 F3+T、不进资源包，由 SoundBufferLibraryMixin 提供字节流）。
 *
 * 随机播放从池里随机挑一段（不依赖服务端指定的 soundId），实现「客户端各自随机」——
 * 多人下每只猫的动作仍服务端同步，但各自听到的音频可能不同（各玩各的梗，符合整活定位）。
 */
public class AudioPool {
	/** 固有音频：sound 注册名 -> GUI 显示名 */
	public static final Map<String, String> BUILTIN_DISPLAY = new LinkedHashMap<>();
	static {
		BUILTIN_DISPLAY.put("laowu2", "[那个那个]");
		BUILTIN_DISPLAY.put("qiliang", "[老吴凄凉]");
		BUILTIN_DISPLAY.put("zhanhou", "[战吼]");
	}

	private static final List<SoundEvent> BUILTIN = new ArrayList<>();
	private static final List<String> IMPORTED = new ArrayList<>();

	public static void init() {
		BUILTIN.clear();
		BUILTIN.add(ModSounds.LAOWU2);
		BUILTIN.add(ModSounds.QILIANG);
		BUILTIN.add(ModSounds.ZHANHOU);
		refreshImported();
	}

	/** 重新扫描 config/laowu_meme/sounds/*.ogg（去掉 .ogg 后缀作为显示/匹配名），排序后存入 IMPORTED */
	public static void refreshImported() {
		IMPORTED.clear();
		File dir = getSoundsDir();
		if (!dir.exists()) return;
		File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".ogg"));
		if (files == null) return;
		for (File f : files) IMPORTED.add(stripExt(f.getName()));
		Collections.sort(IMPORTED);
	}

	public static List<String> importedNames() {
		return new ArrayList<>(IMPORTED);
	}

	public static int importedCount() {
		return IMPORTED.size();
	}

	public static int builtinCount() {
		return BUILTIN.size();
	}

	/** 从固有 + 导入合并池随机挑一段；空返回 null */
	public static PlayTarget random() {
		int total = BUILTIN.size() + IMPORTED.size();
		if (total == 0) return null;
		int idx = (int) (Math.random() * total);
		if (idx < BUILTIN.size()) return PlayTarget.builtin(BUILTIN.get(idx));
		return PlayTarget.imported(IMPORTED.get(idx - BUILTIN.size()));
	}

	/** 旧接口：仅从固有池随机（保留兼容，新代码请用 random()） */
	public static SoundEvent randomBuiltin() {
		if (BUILTIN.isEmpty()) return null;
		return BUILTIN.get((int) (Math.random() * BUILTIN.size()));
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
		return new File(Minecraft.getInstance().gameDirectory, "config/laowu_meme/sounds");
	}

	private static String stripExt(String name) {
		if (name.toLowerCase().endsWith(".ogg")) return name.substring(0, name.length() - 4);
		return name;
	}
}
