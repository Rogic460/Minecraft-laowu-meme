package com.rogic.maodie;

import com.rogic.LaowuMemeMod;
import com.rogic.network.MaodieS2CPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 耄耋多方块结构：服务端权威扫描 + 召猫。
 *
 * - 扫描：每 SCAN_INTERVAL tick，对每个在线玩家周围 SCAN_RADIUS 找楼梯方块（锚点 = 蓝图 [4,1,0] 的楼梯），
 *   以其为锚点反推蓝图原点，逐格严格匹配（不旋转）。匹配成功且未绑定 → 召猫。
 * - 召猫：在锚点（楼梯）10 格内找最近、且命名为"耄耋"的猫，teleport 到楼梯格【上方一格】（坐楼梯顶）。
 *   猫切坐下姿势（不冻结 AI），"玩家靠近播放音频"全在客户端处理。
 * - 破坏检测：每 tick 对已有结构复查蓝图是否仍成立、猫是否还在；任一不满足 → 解除绑定。
 */
public final class MaodieStructureManager {
	private static MaodieBlueprint blueprint;
	private static final Map<BlockPos, MaodieBinding> structures = new HashMap<>();
	private static int scanCounter = 0;

	private MaodieStructureManager() {}

	public static void serverTick(MinecraftServer server) {
		if (blueprint == null) blueprint = MaodieBlueprint.load();
		if (blueprint == null) return;

		// 复查已绑定结构：被改动/破坏 或 猫消失 → 解除
		Iterator<Map.Entry<BlockPos, MaodieBinding>> it = structures.entrySet().iterator();
		while (it.hasNext()) {
			MaodieBinding b = it.next().getValue();
			ServerLevel level = server.getLevel(b.dimension);
			if (level == null || !blueprint.matches(level, b.origin) || level.getEntity(b.catId) == null) {
				release(b, server);
				it.remove();
			}
		}

		scanCounter++;
		if (scanCounter % MaodieBlueprint.SCAN_INTERVAL != 0) return;

		for (ServerLevel level : server.getAllLevels()) {
			for (ServerPlayer sp : level.players()) {
				scanAround(level, sp.blockPosition());
			}
		}
	}

	private static void scanAround(ServerLevel level, BlockPos center) {
		int r = MaodieBlueprint.SCAN_RADIUS;
		BlockPos min = center.offset(-r, -r, -r);
		BlockPos max = center.offset(r, r, r);
		for (BlockPos p : BlockPos.betweenClosed(min, max)) {
			// 锚点方块 = 楼梯（蓝图 [4,1,0]）。结构内有多个楼梯，matches() 会过滤掉非锚点楼梯算出的错误原点，
			// 仅真正的 [4,1,0] 楼梯能反推出全部 part 匹配的原点。
			if (!level.getBlockState(p).is(Blocks.JUNGLE_STAIRS)) continue;
			BlockPos origin = p.offset(-blueprint.anchorOffset.getX(), -blueprint.anchorOffset.getY(), -blueprint.anchorOffset.getZ());
			if (structures.containsKey(origin)) continue;
			if (blueprint.matches(level, origin)) {
				Cat cat = findCat(level, p);
				if (cat != null) bind(level, p, origin, cat);
			}
		}
	}

	private static Cat findCat(ServerLevel level, BlockPos anchor) {
		double best = MaodieBlueprint.CALL_RADIUS * MaodieBlueprint.CALL_RADIUS;
		Cat bestCat = null;
		EntityTypeTest<Entity, Cat> test = EntityTypeTest.forClass(Cat.class);
		List<? extends Cat> cats = level.getEntities(test, cat -> {
			if (cat.getCustomName() == null) return false;
			if (!MaodieBlueprint.MAODIE_NAME.equals(cat.getCustomName().getString())) return false;
			if (isBound(cat.getId())) return false;
			return true;
		});
		for (Cat c : cats) {
			double d = c.distanceToSqr(anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() + 0.5);
			if (d <= best) { best = d; bestCat = c; }
		}
		return bestCat;
	}

	private static void bind(ServerLevel level, BlockPos anchor, BlockPos origin, Cat cat) {
		// 猫落到「座位」格的【上方一格】：座位 = 楼梯 [4,1,0]，猫坐楼梯顶（与原点反推用的楼梯锚点同格）
		BlockPos seat = origin.offset(blueprint.seatOffset);
		cat.teleportTo(seat.getX() + 0.5, seat.getY() + 1, seat.getZ() + 0.5);
		// 切坐下姿势（用户要求），不冻结 AI；结构解除时恢复站立
		cat.setOrderedToSit(true);
		structures.put(origin, new MaodieBinding(origin, anchor, cat.getId(), level.dimension()));
		broadcast(level.getServer(), cat.getId(), true);
		LaowuMemeMod.LOGGER.info("[maodie] 结构激活：召猫 {} 到楼梯 {}", cat.getId(), anchor);
	}

	private static void release(MaodieBinding b, MinecraftServer server) {
		// 解除时让猫恢复站立（取消坐下），AI 正常运行
		ServerLevel level = server.getLevel(b.dimension);
		if (level != null) {
			net.minecraft.world.entity.Entity e = level.getEntity(b.catId);
			if (e instanceof TamableAnimal ta) ta.setOrderedToSit(false);
		}
		broadcast(server, b.catId, false);
		LaowuMemeMod.LOGGER.info("[maodie] 结构解除：猫 {} 恢复自由", b.catId);
	}

	private static void broadcast(MinecraftServer server, int catId, boolean bound) {
		MaodieS2CPacket pkt = new MaodieS2CPacket(catId, bound);
		for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
			ServerPlayNetworking.send(sp, pkt);
		}
	}

	private static boolean isBound(int catId) {
		for (MaodieBinding b : structures.values()) if (b.catId == catId) return true;
		return false;
	}

	static final class MaodieBinding {
		final BlockPos origin;
		final BlockPos anchor;
		final int catId;
		final ResourceKey<Level> dimension;

		MaodieBinding(BlockPos origin, BlockPos anchor, int catId, ResourceKey<Level> dimension) {
			this.origin = origin;
			this.anchor = anchor;
			this.catId = catId;
			this.dimension = dimension;
		}
	}
}
