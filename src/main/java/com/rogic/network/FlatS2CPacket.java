package com.rogic.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 服务端 → 客户端：通知某只猫进入/退出「铲子拍扁」扁平态。
 * flat=true：猫被铲子拍扁（渲染 scale.y 压缩）；flat=false：8 秒到自动恢复原状。
 * 纯 mojmap 结构（CustomPacketPayload + StreamCodec），NeoForge 适配时仅换注册胶水。
 */
public record FlatS2CPacket(int catId, boolean flat) implements CustomPacketPayload {
	public static final Type<FlatS2CPacket> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("laowu_meme", "flat"));
	public static final StreamCodec<RegistryFriendlyByteBuf, FlatS2CPacket> CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, FlatS2CPacket::catId,
			ByteBufCodecs.BOOL, FlatS2CPacket::flat,
			FlatS2CPacket::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
