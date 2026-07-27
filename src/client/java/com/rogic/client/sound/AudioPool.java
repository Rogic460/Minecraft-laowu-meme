package com.rogic.client.sound;

import net.minecraft.sounds.SoundEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户端本地音频池：管理「可随机播放」的音频集合。
 * - 固有音频：三段注册表 SoundEvent（[那个那个]/[老吴凄凉]/[战吼]），开箱即用、零操作。
 * - 用户导入：扫描 config/laowu_meme/sounds/*.ogg（经 Fabric 动态资源包注册，导入后 F3+T 重载生效），
 *   后续版本接入，本期先用固有三段。
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

	public static void init() {
		BUILTIN.clear();
		BUILTIN.add(ModSounds.LAOWU2);
		BUILTIN.add(ModSounds.QILIANG);
		BUILTIN.add(ModSounds.ZHANHOU);
	}

	/** 从固有池随机挑一段；空则返回 null */
	public static SoundEvent randomBuiltin() {
		if (BUILTIN.isEmpty()) return null;
		return BUILTIN.get((int) (Math.random() * BUILTIN.size()));
	}

	public static int builtinCount() {
		return BUILTIN.size();
	}
}
