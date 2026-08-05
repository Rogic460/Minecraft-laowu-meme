package com.rogic.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation; // 1.21.0/1.21.1 经典命名（26.x 才改名 Identifier）

/**
 * 服务端 → 客户端：通知某只猫进入/退出耄耋绑定状态。
 * bound=true：猫被召到结构锚点，客户端开始"看玩家 + 过近哈气"渲染；
 * bound=false：结构破坏/猫消失，客户端停止渲染、猫恢复自由。
 */
public record MaodieS2CPacket(int catId, boolean bound) implements CustomPacketPayload {
	public static final Type<MaodieS2CPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("laowu_meme", "maodie"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MaodieS2CPacket> CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, MaodieS2CPacket::catId,
			ByteBufCodecs.BOOL, MaodieS2CPacket::bound,
			MaodieS2CPacket::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
