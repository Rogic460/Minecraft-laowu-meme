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
 * 客户端状态：收包驱动。记录哪些猫在对头效果中（携带音频 id 与歪头方向），
 * 并管理循环音频的播放/停止。渲染 mixin 通过 isActive / getRollSign 读取。
 */
public class ClientMemeState {
	public static final int SOUND_LAOWU2 = 0;
	public static final int SOUND_QILIANG = 1;

	private static final ClientMemeState INSTANCE = new ClientMemeState();
	public static ClientMemeState get() { return INSTANCE; }

	public static final class ActiveCat {
		public int partnerId;
		public int soundId;
		public int rollSign;
	}

	private final Map<Integer, ActiveCat> active = new HashMap<>();
	private final Map<String, MemeSoundInstance> sounds = new HashMap<>();

	public boolean isActive(int entityId) { return active.containsKey(entityId); }
	public int getRollSign(int entityId) {
		ActiveCat a = active.get(entityId);
		return a == null ? 0 : a.rollSign;
	}

	/** 收到服务端 trigger 包：记录两只猫并起音乐 */
	public void onTrigger(int catAId, int catBId, int soundId, int rollSign) {
		ActiveCat sa = new ActiveCat(); sa.partnerId = catBId; sa.soundId = soundId; sa.rollSign = rollSign;
		ActiveCat sb = new ActiveCat(); sb.partnerId = catAId; sb.soundId = soundId; sb.rollSign = rollSign;
		active.put(catAId, sa);
		active.put(catBId, sb);
		startSound(catAId, catBId, soundId);
	}

	/** 收到服务端 stop 包：清状态 + 停音乐 */
	public void onStop(int catAId, int catBId) {
		active.remove(catAId);
		active.remove(catBId);
		stopSound(catAId, catBId);
	}

	private String key(int a, int b) { return Math.min(a, b) + "-" + Math.max(a, b); }

	private void startSound(int catAId, int catBId, int soundId) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;
		Vec3 mid = midOf(catAId, catBId);
		if (mid == null) return;
		if (mc.player.distanceToSqr(mid) > 16 * 16) return;
		SoundEvent evt = soundId == SOUND_LAOWU2 ? ModSounds.LAOWU2 : ModSounds.QILIANG;
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
}
