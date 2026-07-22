package com.rogic.client.sound;

import com.rogic.LaowuMemeMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * 注册两条音频的 SoundEvent：老吴2、凄凉。
 * 对应资源在 assets/laowu_meme/sounds/laowu2.ogg、qiliang.ogg，sounds.json 里定义。
 */
public class ModSounds {
	public static SoundEvent LAOWU2;
	public static SoundEvent QILIANG;

	public static void init() {
		LAOWU2 = register("laowu2");
		QILIANG = register("qiliang");
	}

	private static SoundEvent register(String name) {
		Identifier id = Identifier.fromNamespaceAndPath(LaowuMemeMod.MOD_ID, name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
