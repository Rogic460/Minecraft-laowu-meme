# 更新日志

所有重要变更记录在此文件。格式参考 [Keep a Changelog](https://keepachangelog.com/)，版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [1.1.1] - 2026-07-22

### 修复（Bug）
- **崩溃修复 · 客户端启动必崩**：v1.1.0 客户端启动即崩溃（`Initializing game` 阶段 `RuntimeException: Could not execute entrypoint stage 'main'`）。
  - 根因：`MemeTriggerS2CPacket` / `MemeStopS2CPacket` 用 `CustomPacketPayload.createType("laowu_meme:trigger")` 注册类型，但 MC 26.1 的 `CustomPacketPayload.createType(String)` 把整个字符串当成 **path**，namespace 默认落到 `minecraft`，于是生成非法标识符 `minecraft:laowu_meme:trigger`（path 里含冒号），在类静态初始化时抛 `IdentifierException`，导致整个 mod 加载失败。
  - 修复：改为 `CustomPacketPayload.createType(Identifier.of("laowu_meme", "trigger"))`（显式 namespace + path），`MemeStopS2CPacket` 同理改为 `"laowu_meme", "stop"`。

### 开发 / 构建
- 版本号 1.1.0 → 1.1.1（崩溃类回归，属补丁级修复）。

## [1.1.0] - 2026-07-22

### 架构
- **重构为服务端权威（server-authoritative）架构**。原"纯客户端驱动"方案在多人下存在同步与瞬移问题，本次改为：服务端检测配对、驱动猫移动/朝向、管理状态机与冷却、向客户端广播 S2C 包；客户端只负责渲染（歪头 / 放大）与循环音乐。
- 单人（integrated server）与多人（专用服务端）现在都能正常运作，且多人下所有玩家看到的效果完全一致。
- 新增 S2C 网络包 `MemeTriggerS2CPacket` / `MemeStopS2CPacket`（`CustomPacketPayload` + `StreamCodec`），用于广播"锁定（含音频 id 与歪头方向）"与"释放"。

### 修复（Bug）
- **Bug 1 · 头部旋转方向**：由之前的左右转（yaw）改为"歪头杀"——头部绕视线轴 roll（`ModelPart.zRot`），且通过模型层实现，不再被原版"猫看人"逻辑覆盖。
- **Bug 2 · 触发流程**：两只猫现在会**先靠近再触发**（APPROACHING 阶段平滑走在一起），触发扫描距离从 3 格改为 6 格。
- **Bug 3 · 贴脸距离**：锁定时两猫中心距收紧到约 0.6 格，实现真正的脸贴脸。
- **Bug 4 · 右键释放**：右键其中一只猫后，服务端恢复猫的 AI 并给一点向外速度，猫**自然走开**（不再瞬移）；被打断的这对猫进入 3 分钟冷却。

### 新增
- **体型放大 50%**：锁定后仅渲染层放大（`LivingEntityRenderState.scale *= 1.5`），**碰撞箱不变**。

### 开发 / 构建
- 确认并适配 MC 26.1.2（首个不混淆版本，mojmap，强制 Java 25）。
- `gradle.properties` 调整 daemon 内存为 `-Xmx2G -XX:MaxMetaspaceSize=512M`，修复 sandbox 下 `-Xmx4G` 导致 Gradle daemon OOM 崩溃的问题。
- 渲染 mixin 适配 26.1 RenderState 模式：`CatRendererMixin`（数据提取 + 放大）、`CatModelMixin`（歪头）、`RenderStateHolder`（跨 mixin 状态桥）。

## [1.0.0] - 2026-07-22

- 初始版本。两只猫（其一命名为"老吴"）靠近触发头对头旋转 + 随机播放老吴2 / 凄凉 BGM，右键打断跑开。
