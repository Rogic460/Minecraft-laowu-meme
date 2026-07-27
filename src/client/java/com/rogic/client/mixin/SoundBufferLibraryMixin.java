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
		if (!path.startsWith("imported/")) return;
		String enc = path.substring("imported/".length());
		if (enc.isEmpty()) return;
		String name = SoundIdCodec.decode(enc);
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
			// 读取/解码失败：放行给原逻辑（按缺失资源处理），不崩溃
		}
	}
}
