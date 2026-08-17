package com.rogic.client;

import com.rogic.LaowuMemeMod;
import com.rogic.client.sound.AudioPool;
import com.rogic.client.sound.ModSounds;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * NeoForge 版客户端入口（1.21.1）。
 * @Mod(dist = Dist.CLIENT) → 本类只在客户端加载，服务端不会触碰（可安全引用 client API）。
 * 职责：音频初始化、配置界面、客户端 tick（耄耋近距哈气音频检查）。
 * ⚠️ payload 注册在 LaowuMemeMod（common 端）——服务端进程不加载本类，通道若只在此注册
 * 服务端握手会缺通道 → 报「服务端缺少此客户端需要的网络通道」（2026-08-17 实测修复）。
 */
@Mod(value = LaowuMemeMod.MOD_ID, dist = Dist.CLIENT)
public class LaowuMemeClient {

	public LaowuMemeClient(IEventBus modEventBus, net.neoforged.fml.ModContainer modContainer) {
		// 注册客户端配置（CLIENT 类型）——ModConfigSpec 持久化；Configured 也能编辑
		modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, com.rogic.client.sound.LaowuClientConfig.SPEC);
		// 提供配置界面工厂：自绘界面（中文显示 + 动态列出导入音频）
		modContainer.registerExtensionPoint(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
				(container, screen) -> new LaowuConfigScreen(screen));

		// SoundEvent 注册（DeferredRegister 挂 mod bus——必须在注册表冻结前）
		com.rogic.client.sound.ModSounds.registerTo(modEventBus);

		modEventBus.addListener(this::onClientSetup);
		// 客户端 tick 挂 game bus
		net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(this::onClientTick);
	}

	private void onClientSetup(FMLClientSetupEvent event) {
		LaowuMemeMod.LOGGER.info("[laowu meme] 客户端初始化中...");
		ModSounds.init();
		AudioPool.init();
		LaowuMemeMod.LOGGER.info("[laowu meme] 客户端初始化完成");
	}

	private void onClientTick(ClientTickEvent.Post event) {
		ClientMemeState.get().tickMaodieAudio();
	}
}
