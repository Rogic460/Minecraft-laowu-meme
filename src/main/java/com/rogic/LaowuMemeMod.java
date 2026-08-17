package com.rogic;

import com.rogic.maodie.MaodieStructureManager;
import com.rogic.network.FlatS2CPacket;
import com.rogic.network.MaodieS2CPacket;
import com.rogic.network.MemeStopS2CPacket;
import com.rogic.network.MemeTriggerS2CPacket;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Cat;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NeoForge 版主入口（1.21.1，NeoForge 21.1.248）。
 * 服务端逻辑：服务端 tick（状态机/结构）、右键猫事件。
 * 客户端（音频/配置/tick）在 LaowuMemeClient（@Mod dist=CLIENT，服务端不加载）。
 * 注意：1.21.1 的 Cat 在 net.minecraft.world.entity.animal.Cat（非 26.x 的 .feline.Cat）。
 *
 * ⚠️ payload 注册必须放本类（common 端，双端都加载）——不能只放 LaowuMemeClient(dist=CLIENT)：
 * 服务端进程不加载 dist=CLIENT 类，若通道只在客户端注册，服务端握手时缺通道 →
 * 客户端连真服务器报「服务端缺少此客户端需要的网络通道」（2026-08-17 实测）。
 * handler 里用 FMLEnvironment.dist 守卫：服务端注册通道但 handler 永不触达 client 代码（ClientMemeState
 * 是纯客户端类，直接引用会让服务端 NoClassDefFoundError）。
 */
@Mod(LaowuMemeMod.MOD_ID)
public class LaowuMemeMod {
	public static final String MOD_ID = "laowu_meme";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public LaowuMemeMod(IEventBus modEventBus) {
		// payload 注册（common：客户端收包 handler、服务端声明可发送通道）
		modEventBus.addListener(this::onRegisterPayloads);

		var forgeBus = net.neoforged.neoforge.common.NeoForge.EVENT_BUS;
		forgeBus.addListener(this::onServerTick);
		forgeBus.addListener(this::onInteractEntity);
		LOGGER.info("[laowu meme] 服务端初始化完成（服务端权威架构）");
	}

	private void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
		LaowuMemeMod.LOGGER.info("[laowu meme] payload 注册...");
		var registrar = event.registrar(LaowuMemeMod.MOD_ID);
		registrar.playToClient(MemeTriggerS2CPacket.TYPE, MemeTriggerS2CPacket.CODEC,
				(payload, context) -> context.enqueueWork(() -> {
					if (FMLEnvironment.dist.isClient()) {
						com.rogic.client.ClientMemeState.get().onTrigger(payload.catAId(), payload.catBId(), payload.soundId(), payload.rollSign());
					}
				}));
		registrar.playToClient(MemeStopS2CPacket.TYPE, MemeStopS2CPacket.CODEC,
				(payload, context) -> context.enqueueWork(() -> {
					if (FMLEnvironment.dist.isClient()) {
						com.rogic.client.ClientMemeState.get().onStop(payload.catAId(), payload.catBId());
					}
				}));
		registrar.playToClient(MaodieS2CPacket.TYPE, MaodieS2CPacket.CODEC,
				(payload, context) -> context.enqueueWork(() -> {
					if (FMLEnvironment.dist.isClient()) {
						if (payload.bound()) {
							com.rogic.client.ClientMemeState.get().onMaodieBind(payload.catId());
							LaowuMemeMod.LOGGER.info("[maodie] 收到绑定包 catId={}", payload.catId());
						} else {
							com.rogic.client.ClientMemeState.get().onMaodieUnbind(payload.catId());
							LaowuMemeMod.LOGGER.info("[maodie] 收到解除包 catId={}", payload.catId());
						}
					}
				}));
		registrar.playToClient(FlatS2CPacket.TYPE, FlatS2CPacket.CODEC,
				(payload, context) -> context.enqueueWork(() -> {
					if (FMLEnvironment.dist.isClient()) {
						com.rogic.client.ClientMemeState.get().onFlat(payload.catId(), payload.flat());
					}
				}));
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
