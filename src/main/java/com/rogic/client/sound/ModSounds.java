package com.rogic.client.sound;

import com.rogic.LaowuMemeMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 注册四条音频的 SoundEvent：老吴2、凄凉、战吼、耄耋。
 * 对应资源在 assets/laowu_meme/sounds/*.ogg，sounds.json 里定义。
 *
 * NeoForge 版：用 DeferredRegister（mod bus 的 RegisterEvent 时机注册，
 * 在注册表冻结前完成——不能像 Fabric 版那样在 FMLClientSetupEvent 里直接 Registry.register）。
 * 静态字段在 init()（客户端 setup，注册完成后）从 DeferredHolder 取值。
 */
public class ModSounds {
	public static SoundEvent LAOWU2;
	public static SoundEvent QILIANG;
	public static SoundEvent ZHANHOU;
	public static SoundEvent MAODIE;

	private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, LaowuMemeMod.MOD_ID);

	private static final DeferredHolder<SoundEvent, SoundEvent> LAOWU2_H = SOUNDS.register("laowu2", () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(LaowuMemeMod.MOD_ID, "laowu2")));
	private static final DeferredHolder<SoundEvent, SoundEvent> QILIANG_H = SOUNDS.register("qiliang", () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(LaowuMemeMod.MOD_ID, "qiliang")));
	private static final DeferredHolder<SoundEvent, SoundEvent> ZHANHOU_H = SOUNDS.register("zhanhou", () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(LaowuMemeMod.MOD_ID, "zhanhou")));
	private static final DeferredHolder<SoundEvent, SoundEvent> MAODIE_H = SOUNDS.register("maodie", () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(LaowuMemeMod.MOD_ID, "maodie")));

	/** 在 mod bus 注册 DeferredRegister（一次即可）——由 LaowuMemeClient 构造调用 */
	public static void registerTo(IEventBus modEventBus) {
		SOUNDS.register(modEventBus);
	}

	/** 客户端 setup 后调用（注册完成，DeferredHolder 有值） */
	public static void init() {
		LAOWU2 = LAOWU2_H.get();
		QILIANG = QILIANG_H.get();
		ZHANHOU = ZHANHOU_H.get();
		MAODIE = MAODIE_H.get();
	}
}
