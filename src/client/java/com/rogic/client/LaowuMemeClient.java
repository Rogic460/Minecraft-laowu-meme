package com.rogic.client;

import com.rogic.LaowuMemeMod;
import com.rogic.client.sound.ModSounds;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.world.InteractionResult;

/**
 * 客户端入口：纯客户端驱动。
 * ClientMemeManager 做检测/锁定/冷却/右键，ClientMemeState+MemeSoundInstance 做音频。
 */
public class LaowuMemeClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		LaowuMemeMod.LOGGER.info("[laowu meme] 客户端初始化中...");

		ModSounds.init();
		ClientMemeManager manager = ClientMemeManager.get();

		// 客户端 tick：检测配对 + 锁定活跃猫
		ClientTickEvents.END_CLIENT_TICK.register(client -> manager.tick());

		// 右键猫打断对头效果
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
			manager.onUseEntity(entity) ? InteractionResult.SUCCESS : InteractionResult.PASS
		);

		LaowuMemeMod.LOGGER.info("[laowu meme] 客户端初始化完成");
	}
}
