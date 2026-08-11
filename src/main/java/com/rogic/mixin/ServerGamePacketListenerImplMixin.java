package com.rogic.mixin;

import com.rogic.ServerMemeManager;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.item.ShovelItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 直接注入 handleInteract（右键实体包处理），绕开 Fabric UseEntityCallback 事件层。
 * 原因（2026-08-07 实测）：服务器装有 C2ME 等 mod，UseEntityCallback 事件在 26.1.2 上
 * 完全不触发（回调不执行），但 handleInteract 是 vanilla 交互入口，必然被调用。
 * 在此检查：手持铲子右键猫 → 拍扁。
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

	@Shadow
	public ServerPlayer player;

	@Inject(method = "handleInteract(Lnet/minecraft/network/protocol/game/ServerboundInteractPacket;)V", at = @At("HEAD"), cancellable = true)
	private void laowuShovelFlat(ServerboundInteractPacket packet, CallbackInfo ci) {
		try {
			ServerPlayer p = this.player;
			if (p == null) return;
			if (!(p.level() instanceof ServerLevel level)) return;
			Entity entity = level.getEntityOrPart(packet.entityId());
			if (!(entity instanceof Cat cat)) return;
			InteractionHand hand = packet.hand();
			if (p.getItemInHand(hand).getItem() instanceof ShovelItem) {
				ServerMemeManager.onRightClick(cat, p, hand);
			}
		} catch (Throwable t) {
			// 服务端兜底，绝不干扰原版交互
		}
	}
}
