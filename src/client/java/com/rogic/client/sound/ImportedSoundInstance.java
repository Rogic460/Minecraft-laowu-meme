package com.rogic.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

/**
 * 导入音频的循环播放实例：绕过资源系统，直接从磁盘 config/laowu_meme/sounds/<名>.ogg 读取字节流，
 * 由 SoundBufferLibraryMixin 在 getStream 拦截 laowu_meme:sounds/imported/<hex名> 时提供 JOrbis 解码流。
 * 文件名经 SoundIdCodec hex 编码进 Identifier，规避 [a-z0-9/._-] 限制（中文/空格文件名曾导致崩溃）。
 * 行为与 MemeSoundInstance 一致：循环、跟随两只猫中点、过远静音、猫消失即停。
 *
 * 关键坑（v1.1.19 崩溃根因）：AbstractSoundInstance 的 getVolume()/getPitch() 读的是超类
 * protected `sound` 字段，该字段由 resolve() 在内部填充（默认实现 this.sound = events.getSound(random)）。
 * 因此本类不能覆盖 getSound()，且必须在 resolve() 里把 this.sound 填上，否则 SoundEngine.play
 * 调 getVolume() 时 this.sound 为 null 直接 NPE（网络协议错误断连）。
 */
public class ImportedSoundInstance extends AbstractTickableSoundInstance {
	private final WeighedSoundEvents events;
	private final int catAId, catBId;

	public ImportedSoundInstance(String baseName, int catAId, int catBId) {
		// 用 LAOWU2 仅作构造载体（AbstractTickableSoundInstance 必须收 SoundEvent）；
		// 真正播放的声音由下方 disk Sound 提供（path=imported/<hex>，被 mixin 拦截读盘）。
		super(ModSounds.LAOWU2, SoundSource.NEUTRAL, RandomSource.create());
		Sound sound = new Sound(
				Identifier.fromNamespaceAndPath("laowu_meme", "imported/" + SoundIdCodec.encode(baseName)),
				(RandomSource r) -> 1.0f,   // volume
				(RandomSource r) -> 1.0f,   // pitch
				1,
				Sound.Type.SOUND_EVENT,
				true,   // stream：走 SoundBufferLibrary.getStream（被 mixin 拦截）
				false,  // preload
				16);    // 衰减距离
		this.events = new WeighedSoundEvents(getIdentifier(), null);
		this.events.addSound(sound);
		this.catAId = catAId;
		this.catBId = catBId;
		this.looping = true;
		this.delay = 0;
		this.volume = 1.0f;
		updatePos();
	}

	@Override
	public WeighedSoundEvents resolve(SoundManager manager) {
		// 必须填充超类 this.sound 字段（仿默认实现），否则 getVolume() 在 SoundEngine.play 里 NPE
		this.sound = this.events.getSound(this.random);
		return this.events;
	}

	@Override
	public void tick() {
		if (!updatePos()) {
			stop();
		}
	}

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
