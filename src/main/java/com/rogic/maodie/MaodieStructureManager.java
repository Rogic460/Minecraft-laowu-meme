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
import net.minecraft.world.entity.animal.Cat; // 1.21.0/1.21.1 经典管线：Cat 在 entity.animal（26.x/1.21.11 才挪到 animal.feline）
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 耄耋多方块结构：服务端权威扫描 + 召猫。
 *
 * - 扫描：每 SCAN_INTERVAL tick，对每个在线玩家周围 SCAN_RADIUS 找**任意楼梯**方块（锚点 = 蓝图 [4,1,0] 的楼梯，木种不限），
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
			if (level == null || !blueprint.matches(level, b.origin, b.rot) || level.getEntity(b.catId) == null) {
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
			// 锚点方块 = 任意楼梯（蓝图 [4,1,0]，木种不限）。用注册表 id 后缀判定，避免 mojmap 下类名解析问题。
			String blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).toString();
			if (!blockId.endsWith("_stairs")) continue;
			// 结构支持 4 向旋转：对每个候选锚点尝试 0/90/180/270°，任一匹配即识别成功。
			for (int rot = 0; rot < 4; rot++) {
				Vec3i ra = MaodieBlueprint.rotateOffset(blueprint.anchorOffset, rot);
				BlockPos origin = p.offset(-ra.getX(), -ra.getY(), -ra.getZ());
				if (structures.containsKey(origin)) continue;
				if (blueprint.matches(level, origin, rot)) {
					Cat cat = findCat(level, p);
					if (cat != null) {
						bind(level, p, origin, rot, cat);
						break; // 该锚点已绑定，跳过其余旋转
					}
				}
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

	private static void bind(ServerLevel level, BlockPos anchor, BlockPos origin, int rot, Cat cat) {
		// 猫落到「座位」格的【上方一格】：座位 = 楼梯 [4,1,0]（随旋转变换），猫坐楼梯顶
		Vec3i seatOff = MaodieBlueprint.rotateOffset(blueprint.seatOffset, rot);
		BlockPos seat = origin.offset(seatOff);
		cat.teleportTo(seat.getX() + 0.5, seat.getY() + 1, seat.getZ() + 0.5);
		// 1.21.0/1.21.1 经典管线：Cat 无独立坐姿方法，坐姿继承 TamableAnimal。
		// 实测仅 setOrderedToSit(true) 对【未驯服】猫无效（AI 会站起来甚至原地跳跃，楼梯顶寻路失败）：
		// → 双设坐姿 flag（setOrderedToSit + setInSittingPose 直接驱动模型坐姿）+ setNoAi(true) 冻结 AI，
		//   猫稳定坐在楼梯顶不跳不走。结构解除时恢复（setNoAi(false) + 清坐姿 flag）。
		cat.setOrderedToSit(true);
		cat.setInSittingPose(true);
		cat.setNoAi(true);
		structures.put(origin, new MaodieBinding(origin, anchor, rot, cat.getId(), level.dimension()));
		broadcast(level.getServer(), cat.getId(), true);
		LaowuMemeMod.LOGGER.info("[maodie] 结构激活：召猫 {} 到楼梯 {} (rot={})", cat.getId(), anchor, rot);
	}

	private static void release(MaodieBinding b, MinecraftServer server) {
		// 解除时让猫恢复站立（取消坐下）+ 解除 AI 冻结，AI 正常运行
		ServerLevel level = server.getLevel(b.dimension);
		if (level != null) {
			net.minecraft.world.entity.Entity e = level.getEntity(b.catId);
			if (e instanceof Cat c) {
				c.setOrderedToSit(false);
				c.setInSittingPose(false);
				c.setNoAi(false);
			}
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
		final int rot;
		final int catId;
		final ResourceKey<Level> dimension;

		MaodieBinding(BlockPos origin, BlockPos anchor, int rot, int catId, ResourceKey<Level> dimension) {
			this.origin = origin;
			this.anchor = anchor;
			this.rot = rot;
			this.catId = catId;
			this.dimension = dimension;
		}
	}
}
