# laowu meme

Minecraft 26.2 Fabric 整活 mod（本分支 `26.2fabric`；其他 MC 版本见 `26.1.2fabric` / `1.21.11fabric` / `1.21.1fabric` 分支）。

一只命名为「老吴」的猫和任意一只猫靠近时，会头对头歪头旋转、体型放大、并随机播放两种 BGM；右键其中一只猫即可打断，两只猫自然走开。支持单人与多人，多人下服务端权威同步、所有玩家看到的效果完全一致。

## 效果

一只命名为"老吴"的猫和 6 格内任意一只猫触发：
- 两只猫先走到一起、身体面对面锁定，头各自歪头（镜像歪头）+ 弓背哈气姿势 + 体型放大
- 随机循环播放选定的 BGM 之一（16 格内玩家听到）
- 玩家右键任意一只猫打断，两只猫自然走开
- 被打断的这对猫 3 分钟内不再触发

服务端权威架构：多人模式下服务端必须装此 mod（整活逻辑在服务端），客户端装了才有歪头 / 放大 / 弓背哈气 / 音乐；单人模式自动包含两端。

## 构建

需要 JDK 25（Minecraft 26.x 是首个不混淆版本，开发强制要求 Java 25）。

在本机 `~/.gradle/gradle.properties` 配置：
```
org.gradle.java.home=<你的 JDK 25 路径>
```

```bash
./gradlew build
# 产物：build/libs/laowu_meme-<版本>+26.2.jar（如 laowu_meme-1.2.1+26.2.jar）
```

## 部署

把 jar 放进 `mods/` 目录。多人服：服务端与客户端都要装（服务端跑整活逻辑，客户端做渲染 / 音效）；单人：装一份即可（两端自动包含）。

## 版本

- Minecraft 26.2
- Fabric Loader 0.19.2
- Fabric API 0.152.1+26.2
- Java 25 / Gradle 9.5.1 / Loom 1.17-SNAPSHOT

## 音频

### 内置音频

音频文件（`laowu2.ogg` / `qiliang.ogg`）未包含在仓库中，请自行放入 `src/main/resources/assets/laowu_meme/sounds/`。
需 Ogg Vorbis 格式（MC 只支持 ogg），可用 ffmpeg 从 mp3 转换：
```
ffmpeg -i input.mp3 -c:a libvorbis -q:a 4 -ar 44100 -ac 1 output.ogg
```

### 导入音频（运行时热插拔）

在游戏内打开本 mod 的配置界面（触发整活后按 Escape 或 Mod 菜单进入），点击「打开音频文件夹」会打开 `config/laowu_meme/sounds/`。
把 `.ogg` 文件放进该文件夹，**重新打开配置界面**即可看到并启用，触发整活时会随内置音频一起随机播放；无需重启游戏，也无需 F3+T 重载。

- **导入音频必须是 `.ogg`（Ogg Vorbis）格式**——Minecraft 只支持 ogg，放 mp3 / wav 等其他格式不会被播放。
- 如果你手上是 mp3，可用在线网站先转成 ogg，例如 **FreeConvert**（https://www.freeconvert.com/mp3-to-ogg）或其他同类站点（CloudConvert、OnlineConvert 等），转完把下载到的 `.ogg` 丢进上面的文件夹即可。
- 配置界面里每条音频都可单独启用 / 禁用，禁用状态会记到 `config/laowu_meme/enabled.properties`，下次进游戏仍生效。
## 友链
类似mod:catfight-mod  Minecraft Fabric 1.20.1
https://github.com/ATLCNND/catfight-mod 
## License

MIT
