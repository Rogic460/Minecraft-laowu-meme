package com.rogic.client;

import com.rogic.LaowuMemeMod;
import com.rogic.client.sound.ModSounds;
import com.rogic.network.MemeStopS2CPacket;
import com.rogic.network.MemeTriggerS2CPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * 客户端入口：只做收包 + 音频。
 * 锁定/移动/释放全部由服务端驱动，客户端不跑猫 AI、不挂 tick、不处理右键。
 */
public class LaowuMemeClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		LaowuMemeMod.LOGGER.info("[laowu meme] 客户端初始化中...");
		ModSounds.init();

		ClientPlayNetworking.registerGlobalReceiver(MemeTriggerS2CPacket.TYPE, (packet, context) ->
				ClientMemeState.get().onTrigger(packet.catAId(), packet.catBId(), packet.soundId(), packet.rollSign())
		);
		ClientPlayNetworking.registerGlobalReceiver(MemeStopS2CPacket.TYPE, (packet, context) ->
				ClientMemeState.get().onStop(packet.catAId(), packet.catBId())
		);

		LaowuMemeMod.LOGGER.info("[laowu meme] 客户端初始化完成");
	}
}
