package com.rogic.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.LoopingAudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

import com.rogic.client.sound.SoundIdCodec;

/**
 * 拦截 laowu_meme:imported/<hex名> 的资源读取，hex 解码出真实文件名后直接从
 * config/laowu_meme/sounds/<名>.ogg 读取并用 JOrbis 解码，使导入音频无需进资源包即可播放。
 *
 * 关键点（v1.1.20 曾在此栽坑）：SoundEngine.play 调用 getStream(location, looping) 时，
 * 传入的 location 就是 SoundInstance 的 Identifier（laowu_meme:imported/<hex>），
 * 路径前缀是 imported/，没有 sounds/。sounds/ 是 vanilla getStream 内部用 Sound.getPath()
 * 拼资源路径时才加的，不会出现在传给 getStream 的 Identifier 上。所以此处必须匹配 imported/。
 *
 * 仅对 laowu_meme 命名空间 + imported/ 路径生效，其余声音走原逻辑。
 * looping 分支完全照搬原版 getStream：用 LoopingAudioStream 包一层，provider 每次从
 * 重置后的流重新建解码器，实现无缝循环。
 */
@Mixin(SoundBufferLibrary.class)
public class SoundBufferLibraryMixin {
	@Inject(method = "getStream(Lnet/minecraft/resources/Identifier;Z)Ljava/util/concurrent/CompletableFuture;",
			at = @At("HEAD"), cancellable = true)
	private void laowuInterceptImportedStream(Identifier id, boolean looping, CallbackInfoReturnable<CompletableFuture<AudioStream>> cir) {
		if (!id.getNamespace().equals("laowu_meme")) return;
		String path = id.getPath();
		// 传给 getStream 的 Identifier 路径前缀是 imported/（无 sounds/），见类注释
		if (!path.startsWith("imported/")) return;
		String enc = path.substring("imported/".length());  // 形如 <hex>.ogg
		if (enc.isEmpty()) return;
		String hex = enc.endsWith(".ogg") ? enc.substring(0, enc.length() - 4) : enc;
		String name = SoundIdCodec.decode(hex);
		if (name.isEmpty()) return;
		File f = new File(Minecraft.getInstance().gameDirectory, "config/laowu_meme/sounds/" + name + ".ogg");
		if (!f.isFile()) return;
		try {
			InputStream in = Files.newInputStream(f.toPath());
			AudioStream stream;
			if (looping) {
				LoopingAudioStream.AudioStreamProvider provider = (InputStream s) -> (AudioStream) (Object) new JOrbisAudioStream(s);
				stream = (AudioStream) (Object) new LoopingAudioStream(provider, in);
			} else {
				stream = (AudioStream) (Object) new JOrbisAudioStream(in);
			}
			cir.setReturnValue(CompletableFuture.completedFuture(stream));
		} catch (IOException | RuntimeException e) {
			// 读取/解码失败：放行给原逻辑（按缺失资源处理），不崩溃；给玩家提示便于排查
			System.out.println("[laowu meme] 导入音频解码失败（已忽略）：" + name + " —— " + e);
			Minecraft.getInstance().getToastManager().addToast(
					new net.minecraft.client.gui.components.toasts.SystemToast(
							net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
							net.minecraft.network.chat.Component.literal("laowu meme"),
							net.minecraft.network.chat.Component.literal("导入音频解码失败：" + name)));
		}
	}
}
