package com.rogic.client.sound;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * NeoForge 客户端配置（CLIENT 类型）——替代 Fabric 版的 enabled.properties。
 * 内置 3 段音频用独立 Boolean 开关（配置界面逐条显示，Configured 可直接点选）；
 * 导入音频用「禁用名字列表」（imported:<文件名>，动态条目只能走列表）。
 */
public final class LaowuClientConfig {
	public static final ModConfigSpec SPEC;
	/** 内置音频开关（true=启用，默认全开） */
	private static final ModConfigSpec.BooleanValue ENABLE_LAOWU2;
	private static final ModConfigSpec.BooleanValue ENABLE_QILIANG;
	private static final ModConfigSpec.BooleanValue ENABLE_ZHANHOU;
	/** 导入音频禁用列表（imported:xxx） */
	private static final ModConfigSpec.ConfigValue<List<? extends String>> DISABLED_IMPORTED;

	static {
		ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
		ENABLE_LAOWU2 = builder.comment("播放音频「那个那个」(laowu2)").define("enableLaowu2", true);
		ENABLE_QILIANG = builder.comment("播放音频「老吴凄凉」(qiliang)").define("enableQiliang", true);
		ENABLE_ZHANHOU = builder.comment("播放音频「战吼」(zhanhou)").define("enableZhanhou", true);
		DISABLED_IMPORTED = builder
				.comment("禁用的导入音频列表（imported:<文件名去掉.ogg>）",
						"不在列表里的导入音频都可以随机播放；禁用的不会出现。",
						"导入音频放 config/laowu_meme/sounds/*.ogg")
				.defineList("disabledImportedSounds", List.of(), e -> e instanceof String);
		SPEC = builder.build();
	}

	private LaowuClientConfig() {}

	/** 某音频是否启用（builtin:xxx 查 Boolean 开关；imported:xxx 查禁用列表） */
	public static boolean isEnabled(String key) {
		if (key.startsWith("builtin:")) {
			return switch (key.substring("builtin:".length())) {
				case "laowu2" -> ENABLE_LAOWU2.get();
				case "qiliang" -> ENABLE_QILIANG.get();
				case "zhanhou" -> ENABLE_ZHANHOU.get();
				default -> true;
			};
		}
		// imported:xxx
		List<? extends String> list = DISABLED_IMPORTED.get();
		return list == null || !list.contains(key);
	}

	/** 更新某音频启用状态并保存（内置走 Boolean 开关；导入走禁用列表） */
	public static void setEnabled(String key, boolean enabled) {
		if (key.startsWith("builtin:")) {
			switch (key.substring("builtin:".length())) {
				case "laowu2" -> ENABLE_LAOWU2.set(enabled);
				case "qiliang" -> ENABLE_QILIANG.set(enabled);
				case "zhanhou" -> ENABLE_ZHANHOU.set(enabled);
			}
		} else {
			List<String> list = new ArrayList<>(DISABLED_IMPORTED.get());
			if (!enabled) {
				if (!list.contains(key)) list.add(key);
			} else {
				list.remove(key);
			}
			DISABLED_IMPORTED.set(list);
		}
		SPEC.save();
	}
}
