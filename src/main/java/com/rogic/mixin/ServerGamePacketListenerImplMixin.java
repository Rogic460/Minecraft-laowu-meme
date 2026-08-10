package com.rogic.mixin;

import com.rogic.ServerMemeManager;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.item.ShovelItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 直接注入 handleInteract（右键实体包处理），绕开 Fabric UseEntityCallback 事件层。
 * 原因（2026-08-07 实测）：服务器装有 C2ME 等 mod，UseEntityCallback 事件完全不触发，
 * 但 handleInteract 是 vanilla 交互入口，必然被调用。在此检查：手持铲子右键猫 → 拍扁。
 * 双保险：UseEntityCallback 正常路径 + 本 mixin 兜底（只处理铲子，避免双触发）。
 *
 * 1.21.1 API 差异（vs 26.1.2）：ServerboundInteractPacket 无 entityId()/hand()，
 * 改用 getTarget(ServerLevel) 拿实体、dispatch(Handler) 回调拿 InteractionHand（onInteraction 分支）。
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
			Entity entity = packet.getTarget(level);
			if (!(entity instanceof Cat cat)) return;
			packet.dispatch(new ServerboundInteractPacket.Handler() {
				@Override
				public void onInteraction(InteractionHand hand) {
					if (p.getItemInHand(hand).getItem() instanceof ShovelItem) {
						ServerMemeManager.onRightClick(cat, p, hand);
					}
				}

				@Override
				public void onInteraction(InteractionHand hand, net.minecraft.world.phys.Vec3 location) {
					if (p.getItemInHand(hand).getItem() instanceof ShovelItem) {
						ServerMemeManager.onRightClick(cat, p, hand);
					}
				}

				@Override
				public void onAttack() {
					// 攻击不处理
				}
			});
		} catch (Throwable t) {
			// 服务端兜底，绝不干扰原版交互
		}
	}
}
