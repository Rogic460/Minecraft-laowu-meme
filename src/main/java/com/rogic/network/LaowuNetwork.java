package com.rogic.network;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * NeoForge 网络发送工具：替代 Fabric 的 ServerPlayNetworking。
 * S2C payload 用 PacketDistributor.sendToPlayer 逐个发送给在线玩家。
 */
public final class LaowuNetwork {
	private LaowuNetwork() {}

	public static void sendToAll(MinecraftServer server, CustomPacketPayload pkt) {
		if (server == null) return;
		for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
			PacketDistributor.sendToPlayer(sp, pkt);
		}
	}
}
