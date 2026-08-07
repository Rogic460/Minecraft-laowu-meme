package com.rogic.client.sound;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * NeoForge 客户端配置（CLIENT 类型）——替代 Fabric 版的 enabled.properties。
 * 存「禁用的音频 key 列表」（builtin:xxx / imported:xxx），不在列表 = 启用。
 * Configured 2.7.5+26.1.2 会自动为 CLIENT 配置生成图形界面（Mods 界面 → 配置）。
 */
public final class LaowuClientConfig {
	public static final ModConfigSpec SPEC;
	/** 禁用的音频 key（builtin:<name> / imported:<name>），默认空 = 全部启用 */
	private static final ModConfigSpec.ConfigValue<List<? extends String>> DISABLED;

	static {
		ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
		DISABLED = builder
				.comment("禁用的音频 key 列表（builtin:xxx 或 imported:xxx）。",
						"不在列表里的音频都可以随机播放；禁用的不会出现。",
						"内置音频 key：builtin:laowu2 / builtin:qiliang / builtin:zhanhou；导入音频 key：imported:<文件名去掉.ogg>")
				.defineList("disabledSounds", List.of(), e -> e instanceof String);
		SPEC = builder.build();
	}

	private LaowuClientConfig() {}

	public static boolean isDisabled(String key) {
		List<? extends String> list = DISABLED.get();
		return list != null && list.contains(key);
	}

	/** 更新禁用状态并保存（同步到配置文件，Configured 界面实时可见） */
	public static void setDisabled(String key, boolean disabled) {
		List<String> list = new ArrayList<>(DISABLED.get());
		if (disabled) {
			if (!list.contains(key)) list.add(key);
		} else {
			list.remove(key);
		}
		DISABLED.set(list);
		DISABLED.save();
	}
}
