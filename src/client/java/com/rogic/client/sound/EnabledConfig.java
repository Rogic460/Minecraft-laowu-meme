package com.rogic.client.sound;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

/**
 * 启用/禁用状态持久化：以 Properties 格式写到 config/laowu_meme/enabled.properties。
 * Key 用 "builtin:<soundName>" 或 "imported:<真名>"（不用 hex，保持配置文件可读）。
 * 缺省视为启用（首次写文件时只把禁用的写进去，节省篇幅）。
 */
public final class EnabledConfig {
	private static final String FILE_NAME = "enabled.properties";

	private EnabledConfig() {}

	public static File getFile() {
		return new File(AudioPool.getConfigDir(), FILE_NAME);
	}

	public static void load(Map<String, Boolean> enabled) {
		File f = getFile();
		if (!f.isFile()) return;
		Properties p = new Properties();
		try (FileReader r = new FileReader(f)) {
			p.load(r);
		} catch (IOException ignored) {
			return;
		}
		for (String key : p.stringPropertyNames()) {
			String v = p.getProperty(key);
			enabled.put(key, !"false".equalsIgnoreCase(v) && !"0".equals(v));
		}
	}

	public static void save(Map<String, Boolean> enabled) {
		File f = getFile();
		try {
			f.getParentFile().mkdirs();
			Properties p = new Properties();
			// 只保存显式 disabled 的（默认 true，文件小、可读性高）
			for (var e : enabled.entrySet()) {
				if (Boolean.FALSE.equals(e.getValue())) {
					p.setProperty(e.getKey(), "false");
				}
			}
			try (FileWriter w = new FileWriter(f)) {
				p.store(w, "laowu_meme enabled audio map (仅记录禁用的；缺省=启用)");
			}
		} catch (IOException ignored) {
		}
	}
}