package com.rogic.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 服务端 → 客户端：通知某两只猫结束锁定状态（右键释放 / 猫消失）。
 * 客户端据此停止歪头渲染与音乐。
 */
public record MemeStopS2CPacket(int catAId, int catBId) implements CustomPacketPayload {
	public static final Type<MemeStopS2CPacket> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("laowu_meme", "stop"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MemeStopS2CPacket> CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, MemeStopS2CPacket::catAId,
			ByteBufCodecs.INT, MemeStopS2CPacket::catBId,
			MemeStopS2CPacket::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
