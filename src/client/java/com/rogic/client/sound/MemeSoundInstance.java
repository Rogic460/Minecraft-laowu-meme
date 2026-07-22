package com.rogic.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

/**
 * 循环播放的音频实例：跟随两只猫的中点位置，超出 32 格静音。
 * 由 ClientMemeState 创建/停止。
 */
public class MemeSoundInstance extends AbstractTickableSoundInstance {
	private final int catAId, catBId;

	public MemeSoundInstance(SoundEvent sound, int catAId, int catBId) {
		super(sound, SoundSource.NEUTRAL, RandomSource.create());
		this.catAId = catAId;
		this.catBId = catBId;
		this.looping = true;
		this.delay = 0;
		this.volume = 1.0f;
		updatePos();
	}

	@Override
	public void tick() {
		if (!updatePos()) {
			stop();
		}
	}

	/** 更新到两只猫中点；返回 false 表示猫已不存在 */
	private boolean updatePos() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return false;
		Entity a = mc.level.getEntity(catAId);
		Entity b = mc.level.getEntity(catBId);
		if (a == null || b == null) return false;
		this.x = (a.getX() + b.getX()) / 2.0;
		this.y = (a.getY() + b.getY()) / 2.0;
		this.z = (a.getZ() + b.getZ()) / 2.0;
		if (mc.player != null && mc.player.distanceToSqr(this.x, this.y, this.z) > 32 * 32) {
			this.volume = 0.0f;
		} else {
			this.volume = 1.0f;
		}
		return true;
	}
}
