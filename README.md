# laowu meme

Minecraft 26.1.2 Fabric 整活 mod。

一只命名为「老吴」的猫和任意一只猫靠近时，会头对头歪头旋转、体型放大、并随机播放两种 BGM；右键其中一只猫即可打断，两只猫自然走开。支持单人与多人，多人下服务端权威同步、所有玩家看到的效果完全一致。

## 效果

一只命名为"老吴"的猫和 6 格内任意一只猫触发：
- 两只猫先走到一起、身体面对面锁定，头各自歪头（镜像歪头）+ 弓背哈气姿势 + 体型放大
- 随机循环播放两条 BGM 之一（16 格内玩家听到）
- 玩家右键任意一只猫打断，两只猫自然走开
- 被打断的这对猫 3 分钟内不再触发

服务端权威架构：多人模式下服务端必须装此 mod（整活逻辑在服务端），客户端装了才有歪头 / 放大 / 弓背哈气 / 音乐；单人模式自动包含两端。

## 构建

需要 JDK 25（Minecraft 26.1 是首个不混淆版本，开发强制要求 Java 25）。

在本机 `~/.gradle/gradle.properties` 配置：
```
org.gradle.java.home=<你的 JDK 25 路径>
```

```bash
./gradlew build
# 产物：build/libs/laowu_meme-1.0.0.jar
```

## 部署

把 jar 放进 `mods/` 目录。多人服：服务端与客户端都要装（服务端跑整活逻辑，客户端做渲染 / 音效）；单人：装一份即可（两端自动包含）。

## 版本

- Minecraft 26.1.2
- Fabric Loader 0.19.3
- Fabric API 0.152.1+26.1.2
- Java 25 / Gradle 9.5.1 / Loom 1.17-SNAPSHOT

## 音频

音频文件（`laowu2.ogg` / `qiliang.ogg`）未包含在仓库中，请自行放入 `src/main/resources/assets/laowu_meme/sounds/`。
需 Ogg Vorbis 格式（MC 只支持 ogg），可用 ffmpeg 从 mp3 转换：
```
ffmpeg -i input.mp3 -c:a libvorbis -q:a 4 -ar 44100 -ac 1 output.ogg
```

## License

MIT
