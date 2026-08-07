package com.rogic.client;

import com.rogic.LaowuMemeMod;
import com.rogic.client.sound.AudioPool;
import com.rogic.client.sound.ModSounds;
import com.rogic.network.FlatS2CPacket;
import com.rogic.network.MaodieS2CPacket;
import com.rogic.network.MemeStopS2CPacket;
import com.rogic.network.MemeTriggerS2CPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * NeoForge 版客户端入口（26.1.2 标准写法）。
 * @Mod(dist = Dist.CLIENT) → 本类只在客户端加载，服务端不会触碰（可安全引用 client API）。
 * 职责：S2C payload 收包 handler、音频初始化、客户端 tick（耄耋近距哈气音频检查）。
 */
@Mod(value = LaowuMemeMod.MOD_ID, dist = Dist.CLIENT)
public class LaowuMemeClient {

	public LaowuMemeClient(IEventBus modEventBus) {
		modEventBus.addListener(this::onRegisterPayloads);
		modEventBus.addListener(this::onClientSetup);
		// 客户端 tick 挂 game bus
		net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(this::onClientTick);
	}

	private void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
		LaowuMemeMod.LOGGER.info("[laowu meme] 客户端 payload 注册...");
		var registrar = event.registrar(LaowuMemeMod.MOD_ID);
		registrar.playToClient(MemeTriggerS2CPacket.TYPE, MemeTriggerS2CPacket.CODEC,
				(payload, context) -> context.enqueueWork(() ->
						ClientMemeState.get().onTrigger(payload.catAId(), payload.catBId(), payload.soundId(), payload.rollSign())));
		registrar.playToClient(MemeStopS2CPacket.TYPE, MemeStopS2CPacket.CODEC,
				(payload, context) -> context.enqueueWork(() ->
						ClientMemeState.get().onStop(payload.catAId(), payload.catBId())));
		registrar.playToClient(MaodieS2CPacket.TYPE, MaodieS2CPacket.CODEC,
				(payload, context) -> context.enqueueWork(() -> {
					if (payload.bound()) {
						ClientMemeState.get().onMaodieBind(payload.catId());
						LaowuMemeMod.LOGGER.info("[maodie] 收到绑定包 catId={}", payload.catId());
					} else {
						ClientMemeState.get().onMaodieUnbind(payload.catId());
						LaowuMemeMod.LOGGER.info("[maodie] 收到解除包 catId={}", payload.catId());
					}
				}));
		registrar.playToClient(FlatS2CPacket.TYPE, FlatS2CPacket.CODEC,
				(payload, context) -> context.enqueueWork(() ->
						ClientMemeState.get().onFlat(payload.catId(), payload.flat())));
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
