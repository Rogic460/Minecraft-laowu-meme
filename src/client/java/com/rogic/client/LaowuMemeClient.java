package com.rogic.client;

import com.rogic.LaowuMemeMod;
import com.rogic.client.sound.AudioPool;
import com.rogic.client.sound.ModSounds;
import com.rogic.network.FlatS2CPacket;
import com.rogic.network.MaodieS2CPacket;
import com.rogic.network.MemeStopS2CPacket;
import com.rogic.network.MemeTriggerS2CPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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
		AudioPool.init();

		ClientPlayNetworking.registerGlobalReceiver(MemeTriggerS2CPacket.TYPE, (packet, context) ->
				ClientMemeState.get().onTrigger(packet.catAId(), packet.catBId(), packet.soundId(), packet.rollSign())
		);
		ClientPlayNetworking.registerGlobalReceiver(MemeStopS2CPacket.TYPE, (packet, context) ->
				ClientMemeState.get().onStop(packet.catAId(), packet.catBId())
		);
		ClientPlayNetworking.registerGlobalReceiver(MaodieS2CPacket.TYPE, (packet, context) -> {
			if (packet.bound()) {
				ClientMemeState.get().onMaodieBind(packet.catId());
				LaowuMemeMod.LOGGER.info("[maodie] 收到绑定包 catId={}", packet.catId());
			} else {
				ClientMemeState.get().onMaodieUnbind(packet.catId());
				LaowuMemeMod.LOGGER.info("[maodie] 收到解除包 catId={}", packet.catId());
			}
		});
		// 铲子拍扁：flat=true 压扁渲染，flat=false 恢复
		ClientPlayNetworking.registerGlobalReceiver(FlatS2CPacket.TYPE, (packet, context) ->
				ClientMemeState.get().onFlat(packet.catId(), packet.flat())
		);
		// 每 tick 检查玩家是否靠近耄耋猫，进入半径则播放一次音频
		ClientTickEvents.START_CLIENT_TICK.register(mc -> ClientMemeState.get().tickMaodieAudio());

		LaowuMemeMod.LOGGER.info("[laowu meme] 客户端初始化完成");
	}
}
