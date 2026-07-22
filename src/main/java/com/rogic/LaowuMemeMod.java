package com.rogic;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 服务端入口。逻辑全在客户端（ClientMemeManager），服务端无需配置。
 * 服务端装不装这个 mod 都不影响——装了只是加载这个空入口。
 */
public class LaowuMemeMod implements ModInitializer {
	public static final String MOD_ID = "laowu_meme";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("[laowu meme] 服务端加载（逻辑在客户端，服务端无需配置）");
	}
}
