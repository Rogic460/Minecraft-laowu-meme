package com.rogic.client;

import com.rogic.LaowuMemeMod;
import com.rogic.client.sound.AudioPool;
import com.rogic.client.sound.ModSounds;
import com.rogic.network.FlatS2CPacket;
import com.rogic.network.MaodieS2CPacket;
import com.rogic.network.MemeStopS2CPacket;
import com.rogic.network.MemeTriggerS2CPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * NeoForge 版客户端入口（Dist.CLIENT 专属，服务端不加载此类）。
 * 职责：音频初始化、S2C payload 收包处理、客户端 tick（耄耋近距哈气音频检查）。
 * 锁定/移动/释放全部由服务端驱动，客户端不跑猫 AI、不处理右键。
 */
@EventBusSubscriber(modid = LaowuMemeMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class LaowuMemeClient {

	@SubscribeEvent
	public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
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

	@SubscribeEvent
	public static void onClientSetup(FMLClientSetupEvent event) {
		LaowuMemeMod.LOGGER.info("[laowu meme] 客户端初始化中...");
		ModSounds.init();
		AudioPool.init();
		// 客户端 tick（耄耋近距哈气音频检查）挂 game bus——在客户端 setup 时注册，避免双 bus 冲突
		net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(LaowuMemeClient::onClientTick);
		LaowuMemeMod.LOGGER.info("[laowu meme] 客户端初始化完成");
	}

	public static void onClientTick(ClientTickEvent.Post event) {
		ClientMemeState.get().tickMaodieAudio();
	}
}
