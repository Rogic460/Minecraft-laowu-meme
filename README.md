# laowu meme

Minecraft 26.1.2 Fabric mod —— 纯客户端整活 mod。

## 效果

两只猫靠近（≤3格，其中一只需命名为"老吴"）触发：
- 两只猫锁定位置不动，身体面对面，头各自逆时针旋转 45 度（镜像歪头）
- 随机循环播放两条音频之一（16 格内玩家听到）
- 玩家右键任意一只猫打断，两只猫跑开 3 秒后恢复
- 被打断的这对猫 3 分钟内不再触发

纯客户端运行，服务端装不装都行。

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

把 jar 放进客户端 `mods/` 即可（服务端可选）。

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
