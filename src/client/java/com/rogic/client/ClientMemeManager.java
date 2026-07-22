package com.rogic.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * 客户端驱动版管理器：检测配对、锁定猫、右键打断、冷却。
 * 纯客户端运行，不依赖服务端（服务端装不装这个 mod 都行）。
 * 锁定方式：每 tick 强制覆盖猫的位置+朝向（服务端同步来了又被覆盖回锁定值）。
 */
public class ClientMemeManager {
	private static final ClientMemeManager INSTANCE = new ClientMemeManager();

	public static final double TRIGGER_DISTANCE = 3.0;
	public static final String LAOWU_NAME = "老吴";
	public static final long COOLDOWN_MS = 3 * 60 * 1000L;
	public static final int SCAN_INTERVAL = 20;
	public static final int RUN_TICKS = 60;
	public static final double RUN_SPEED = 0.45;
	public static final int SOUND_LAOWU2 = 0;
	public static final int SOUND_QILIANG = 1;

	private final List<MemePair> activePairs = new ArrayList<>();
	private final Map<UUID, Long> cooldowns = new HashMap<>();
	private final Map<UUID, Integer> runTasks = new HashMap<>();
	private int tickCount = 0;

	private ClientMemeManager() {}
	public static ClientMemeManager get() { return INSTANCE; }

	static final class MemePair {
		final UUID catAUuid, catBUuid;
		final int catAId, catBId;
		final Vec3 posA, posB;
		final int soundId;
		boolean invalid;
		MemePair(UUID a, UUID b, int aid, int bid, Vec3 pa, Vec3 pb, int s) {
			catAUuid = a; catBUuid = b; catAId = aid; catBId = bid;
			posA = pa; posB = pb; soundId = s;
		}
	}

	public void tick() {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null) return;
		tickCount++;
		lockActivePairs();
		if (tickCount % SCAN_INTERVAL == 0) scanPairs(level);
		tickRunTasks();
		cleanupCooldowns();
	}

	/** 每 tick 强制锁定配对猫位置+朝向（覆盖服务端同步） */
	private void lockActivePairs() {
		Iterator<MemePair> it = activePairs.iterator();
		while (it.hasNext()) {
			MemePair p = it.next();
			Cat a = findCat(p.catAUuid);
			Cat b = findCat(p.catBUuid);
			if (a == null || b == null || a.isRemoved() || b.isRemoved()) {
				p.invalid = true;
				ClientMemeState.get().onStop(p.catAId, p.catBId);
				it.remove();
				continue;
			}
			a.setDeltaMovement(Vec3.ZERO);
			b.setDeltaMovement(Vec3.ZERO);
			a.setPos(p.posA.x, p.posA.y, p.posA.z);
			b.setPos(p.posB.x, p.posB.y, p.posB.z);
			float yawA = facingYaw(p.posA, p.posB);
			float yawB = facingYaw(p.posB, p.posA);
			float offsetA = (a.getId() < b.getId()) ? 45f : -45f;
			applyRot(a, yawA, yawA + offsetA);
			applyRot(b, yawB, yawB - offsetA);
		}
	}

	private void scanPairs(ClientLevel level) {
		Set<UUID> paired = new HashSet<>();
		List<Cat> cats = level.getEntitiesOfClass(Cat.class,
			AABB.ofSize(new Vec3(0, 0, 0), 1_000_000, 1_000_000, 1_000_000));
		for (Cat laowu : cats) {
			if (paired.contains(laowu.getUUID())) continue;
			if (!isLaowu(laowu)) continue;
			if (isActive(laowu.getUUID()) || isOnCooldown(laowu.getUUID()) || isRunning(laowu.getUUID())) continue;

			Cat partner = null;
			double best = TRIGGER_DISTANCE * TRIGGER_DISTANCE;
			for (Cat c : cats) {
				if (c.getUUID().equals(laowu.getUUID())) continue;
				if (paired.contains(c.getUUID())) continue;
				if (isActive(c.getUUID()) || isOnCooldown(c.getUUID()) || isRunning(c.getUUID())) continue;
				double d = laowu.distanceToSqr(c);
				if (d <= best) { best = d; partner = c; }
			}
			if (partner != null) {
				triggerPair(laowu, partner);
				paired.add(laowu.getUUID());
				paired.add(partner.getUUID());
			}
		}
	}

	private void triggerPair(Cat a, Cat b) {
		Vec3 pa = a.position();
		Vec3 pb = b.position();
		int soundId = a.getRandom().nextBoolean() ? SOUND_LAOWU2 : SOUND_QILIANG;
		activePairs.add(new MemePair(a.getUUID(), b.getUUID(), a.getId(), b.getId(), pa, pb, soundId));
		ClientMemeState.get().onTrigger(a.getId(), b.getId(), soundId);
	}

	public boolean onUseEntity(Entity entity) {
		if (!(entity instanceof Cat target)) return false;
		for (MemePair p : activePairs) {
			if (p.catAUuid.equals(target.getUUID()) || p.catBUuid.equals(target.getUUID())) {
				Cat partner = p.catAUuid.equals(target.getUUID()) ? findCat(p.catBUuid) : findCat(p.catAUuid);
				breakPair(p, target, partner);
				return true;
			}
		}
		return false;
	}

	private void breakPair(MemePair pair, Cat a, Cat b) {
		activePairs.remove(pair);
		if (a != null && b != null) {
			Vec3 dirA = new Vec3(a.getX() - b.getX(), 0, a.getZ() - b.getZ());
			if (dirA.lengthSqr() < 1.0E-4) dirA = new Vec3(1, 0, 0);
			dirA = dirA.normalize();
			a.setDeltaMovement(dirA.scale(RUN_SPEED));
			b.setDeltaMovement(dirA.scale(-RUN_SPEED));
		}
		if (a != null) runTasks.put(a.getUUID(), RUN_TICKS);
		if (b != null) runTasks.put(b.getUUID(), RUN_TICKS);
		long exp = System.currentTimeMillis() + COOLDOWN_MS;
		cooldowns.put(pair.catAUuid, exp);
		cooldowns.put(pair.catBUuid, exp);
		ClientMemeState.get().onStop(pair.catAId, pair.catBId);
	}

	private static boolean isLaowu(Cat cat) {
		return cat.getCustomName() != null && LAOWU_NAME.equals(cat.getCustomName().getString());
	}
	private boolean isActive(UUID id) {
		for (MemePair p : activePairs) if (p.catAUuid.equals(id) || p.catBUuid.equals(id)) return true;
		return false;
	}
	private boolean isOnCooldown(UUID id) {
		Long exp = cooldowns.get(id);
		return exp != null && exp > System.currentTimeMillis();
	}
	private boolean isRunning(UUID id) { return runTasks.containsKey(id); }

	private void tickRunTasks() {
		runTasks.entrySet().removeIf(e -> {
			int v = e.getValue() - 1;
			if (v <= 0) return true;
			e.setValue(v);
			return false;
		});
	}
	private void cleanupCooldowns() {
		long now = System.currentTimeMillis();
		cooldowns.entrySet().removeIf(e -> e.getValue() <= now);
	}

	private static void applyRot(Cat cat, float bodyYaw, float headYaw) {
		cat.setYRot(bodyYaw);
		cat.setYBodyRot(bodyYaw);
		cat.setYHeadRot(headYaw);
	}
	private static float facingYaw(Vec3 from, Vec3 to) {
		double dx = to.x - from.x, dz = to.z - from.z;
		return (float) Math.toDegrees(Math.atan2(-dx, dz));
	}

	private static Cat findCat(UUID uuid) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return null;
		Entity e = mc.level.getEntity(uuid);
		return e instanceof Cat c ? c : null;
	}
}
