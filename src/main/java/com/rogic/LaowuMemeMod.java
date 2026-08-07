package com.rogic;

import com.rogic.maodie.MaodieStructureManager;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.feline.Cat;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NeoForge 版主入口（26.1.2）。
 * 服务端逻辑：服务端 tick（状态机/结构）、右键猫事件。
 * S2C payload 注册与收包在客户端 LaowuMemeClient（Dist.CLIENT，客户端专属类）处理。
 */
@Mod(LaowuMemeMod.MOD_ID)
public class LaowuMemeMod {
	public static final String MOD_ID = "laowu_meme";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public LaowuMemeMod(IEventBus modEventBus) {
		var forgeBus = net.neoforged.neoforge.common.NeoForge.EVENT_BUS;
		forgeBus.addListener(this::onServerTick);
		forgeBus.addListener(this::onInteractEntity);
		LOGGER.info("[laowu meme] 服务端初始化完成（服务端权威架构）");
	}

	private void onServerTick(ServerTickEvent.Post event) {
		var server = event.getServer();
		ServerMemeManager.serverTick(server);
		MaodieStructureManager.serverTick(server);
	}

	private void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
		// 客户端线程不处理（单机集成服务器下客户端事件也触发，交给服务端）
		if (event.getLevel().isClientSide()) return;
		if (event.getTarget() instanceof Cat cat) {
			InteractionResult result = ServerMemeManager.onRightClick(cat, event.getEntity(), event.getHand());
			if (result != InteractionResult.PASS) {
				event.setCanceled(true);
			}
		}
	}
}
