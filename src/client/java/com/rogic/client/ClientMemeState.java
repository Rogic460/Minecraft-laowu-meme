package com.rogic.client;

import com.rogic.client.sound.MemeSoundInstance;
import com.rogic.client.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * 客户端状态：记录哪些猫在对头效果中，管理音频播放。
 */
public class ClientMemeState {
	private static final ClientMemeState INSTANCE = new ClientMemeState();
	private final Map<Integer, PairState> states = new HashMap<>();
	private final Map<String, MemeSoundInstance> sounds = new HashMap<>();

	public static ClientMemeState get() { return INSTANCE; }
	private ClientMemeState() {}

	public static class PairState {
		public int partnerId;
		public float bodyYaw;
		public int soundId;
	}

	public PairState get(int entityId) { return states.get(entityId); }

	public void onTrigger(int catAId, int catBId, int soundId) {
		Minecraft mc = Minecraft.getInstance();
		PairState sa = new PairState();
		sa.partnerId = catBId; sa.soundId = soundId;
		PairState sb = new PairState();
		sb.partnerId = catAId; sb.soundId = soundId;
		if (mc.level != null) {
			Entity a = mc.level.getEntity(catAId);
			Entity b = mc.level.getEntity(catBId);
			if (a != null && b != null) {
				sa.bodyYaw = facingYaw(a.position(), b.position());
				sb.bodyYaw = facingYaw(b.position(), a.position());
			}
		}
		states.put(catAId, sa);
		states.put(catBId, sb);
		startSound(catAId, catBId, soundId);
	}

	public void onStop(int catAId, int catBId) {
		states.remove(catAId);
		states.remove(catBId);
		stopSound(catAId, catBId);
	}

	private String key(int a, int b) { return Math.min(a, b) + "-" + Math.max(a, b); }

	private void startSound(int catAId, int catBId, int soundId) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;
		Vec3 mid = midOf(catAId, catBId);
		if (mid == null) return;
		if (mc.player.distanceToSqr(mid) > 16 * 16) return;
		SoundEvent evt = soundId == ClientMemeManager.SOUND_LAOWU2 ? ModSounds.LAOWU2 : ModSounds.QILIANG;
		if (evt == null) return;
		MemeSoundInstance inst = new MemeSoundInstance(evt, catAId, catBId);
		sounds.put(key(catAId, catBId), inst);
		mc.getSoundManager().play(inst);
	}

	private void stopSound(int catAId, int catBId) {
		MemeSoundInstance inst = sounds.remove(key(catAId, catBId));
		if (inst != null) Minecraft.getInstance().getSoundManager().stop(inst);
	}

	private Vec3 midOf(int a, int b) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return null;
		Entity ea = mc.level.getEntity(a), eb = mc.level.getEntity(b);
		if (ea == null || eb == null) return null;
		return ea.position().add(eb.position()).scale(0.5);
	}

	private static float facingYaw(Vec3 from, Vec3 to) {
		double dx = to.x - from.x, dz = to.z - from.z;
		return (float) Math.toDegrees(Math.atan2(-dx, dz));
	}
}
