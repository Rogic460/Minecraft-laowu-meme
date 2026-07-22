package com.rogic;

import com.rogic.network.MemeStopS2CPacket;
import com.rogic.network.MemeTriggerS2CPacket;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.world.entity.animal.feline.Cat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 主入口（* 环境，服务端/客户端都会执行）。
 * 注册网络包类型（双端），并把服务端逻辑挂到 ServerTick 与右键事件上。
 */
public class LaowuMemeMod implements ModInitializer {
	public static final String MOD_ID = "laowu_meme";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// 注册 S2C 网络包类型（双端都会执行，客户端才能解码）
		PayloadTypeRegistry.clientboundPlay().register(MemeTriggerS2CPacket.TYPE, MemeTriggerS2CPacket.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MemeStopS2CPacket.TYPE, MemeStopS2CPacket.CODEC);

		// 服务端每 tick 推进猫的状态机
		ServerTickEvents.END_SERVER_TICK.register(server -> ServerMemeManager.serverTick(server));

		// 右键猫 → 释放（服务端权威）
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
				ServerMemeManager.onRightClick(entity instanceof Cat c ? c : null));

		LOGGER.info("[laowu meme] 服务端初始化完成（服务端权威架构）");
	}
}
