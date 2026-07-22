package com.rogic.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 服务端 → 客户端：通知某两只猫进入锁定（对头）状态。
 * 携带：两只猫的 entity id、选中的音频 id、歪头方向(±1)。
 */
public record MemeTriggerS2CPacket(int catAId, int catBId, int soundId, int rollSign) implements CustomPacketPayload {
	public static final Type<MemeTriggerS2CPacket> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("laowu_meme", "trigger"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MemeTriggerS2CPacket> CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, MemeTriggerS2CPacket::catAId,
			ByteBufCodecs.INT, MemeTriggerS2CPacket::catBId,
			ByteBufCodecs.INT, MemeTriggerS2CPacket::soundId,
			ByteBufCodecs.INT, MemeTriggerS2CPacket::rollSign,
			MemeTriggerS2CPacket::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
