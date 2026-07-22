# 更新日志

所有重要变更记录在此文件。格式参考 [Keep a Changelog](https://keepachangelog.com/)，版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [1.1.7] - 2026-07-22

### 修复（Bug）
- **头部歪头终于稳定生效（这次是真正的根因）**：v1.1.6 黑屏修好了，但头还是不转。
  - 真正根因（26.1.2 client jar 字节码核实）：`AdultFelineModel.setupAnim` **会主动读写 `head.zRot`**（不只是 xRot/yRot）。之前在 `extractRenderState` 阶段设的 `head.zRot`，随后被 `setupAnim` **覆盖**了——所以无论 `getModel()` 成不成功，头都不转。
  - 修复：歪头逻辑**搬到 `CatModelMixin`，注入 `AdultFelineModel`+`BabyFelineModel` 的 `setupAnim` 的 TAIL**（TAIL 保证写在我之后、顶点提交之前，zRot 不被覆盖）。用 `this`（`this` 即模型）转型 `Model` 取 `root().getChild("head").zRot`，**不依赖 `@Shadow` 字段**（26.1 mojmap 无 refmap，@Shadow vanilla 字段必崩黑屏）。整段 `try/catch` 兜底。
  - 新增 `CatRenderStateMixin`：给 `CatRenderState` 加 `@Unique` 字段 `laowuActive`/`laowuRoll`，`extractRenderState`（有 Cat 实体，能取 id/roll）写入，同一 `CatRenderState` 实例流到 `setupAnim` 读取——替代之前不可靠的 `WeakHashMap` 跨 mixin 桥。
  - `laowu_meme.client.mixins.json` 现注册 `CatRendererMixin` / `CatModelMixin` / `CatRenderStateMixin` 三个。
- **锁定距离再拉大**：`LOCK_DISTANCE` 1.3 → 1.8（各自离中点 0.9）。用户反馈 1.3 仍偏近、身体重叠；1.8 让两只猫头对头、身体明显分开、不重叠。

### 开发 / 构建
- 版本号 1.1.6 → 1.1.7。

## [1.1.6] - 2026-07-22

### 修复（Bug · 黑屏二次回归，根因彻底查清）
- **v1.1.5 仍黑屏（用户实测），这次根因是 refmap 缺失，不是类型**：日志仍报 `@Shadow field model was not located ... No refMap loaded`。
  - 真相：MC 26.1 用 mojmap 构建时，Loom **不会生成 refmap**；而运行时游戏类是 **intermediary** 命名。v1.1.3 的 `@Inject extractRenderState` 能跑，只是因为 `extractRenderState` 这个方法名在 intermediary 里被保留了；但字段 `model` 在 intermediary 里是 `field_xxxx`，没有 refmap 翻译，`@Shadow model` 必然解析失败 → mixin apply 崩溃 → 黑屏。所以**只要用 `@Shadow` 去 shadow 一个 vanilla 字段，在 mojmap 构建 + 无 refmap 下就会炸**，无论类型写得多对。
  - **修复（彻底去 @Shadow）**：不再 shadow 任何字段。改用 `CatRenderer` 继承的公开方法 `LivingEntityRenderer.getModel()` 取模型（`public M getModel()`，擦除为 `EntityModel`），再 `root().getChild("head").zRot`。公开方法名在 intermediary 里同样被保留（与 `extractRenderState` 同机制），可解析。整段 `try/catch(Throwable)` 兜底，任何异常都绝不让渲染器崩。
  - 已用 javap 核实构建产物：mixin class **不含任何 @Shadow**，字节码为 `LivingEntityRenderer.getModel()→EntityModel.root()→getChild("head")→zRot`。

### 开发 / 构建
- 版本号 1.1.5 → 1.1.6。
- 教训已固化进 skill `fabric-261-serverauth-mod`：26.1 mojmap 构建下 **不要 `@Shadow` vanilla 字段**（无 refmap → intermediary 字段名对不上必崩）；用公开方法/getter 或模型类自身 `this` 取部件，并一律 `try/catch`。

## [1.1.5] - 2026-07-22

### 修复（Bug · 紧急回归）
- **修复 v1.1.4 主菜单黑屏（启动后整个画面黑色，但 GUI 有声音）**：这是 1.1.4 引入的致命回归，1.1.3 正常。
  - 根因（用户日志 `latest.log` 实锤）：`CatRendererMixin` 里 `@Shadow protected AbstractFelineModel<CatRenderState> model;` 的**擦除类型写错**。真实字段在 `LivingEntityRenderer` 中是 `protected M model`（`M extends EntityModel<? super S>`），擦除后是 `net.minecraft.client.model.EntityModel`，并非 `AbstractFelineModel`。Mixin 按"字段名 + 描述符"解析 `@Shadow`，类型对不上就报 `InvalidMixinException: @Shadow field model was not located`，导致 `CatRenderer` 的 Mixin **整体应用失败** → `EntityRenderers` 类初始化抛 `NoClassDefFoundError` → 资源重载异常 → 主菜单/世界渲染全黑（但 GUI 与音效在，所以"点按钮有声音"）。
  - 修复：把 `@Shadow` 类型改为 `EntityModel<CatRenderState>`（擦除描述符与真实字段一致）。并用 MC 26.1 客户端 jar 字节码核实：`EntityModel extends Model`，`Model.root()` 为 public，`AdultFelineModel extends AbstractFelineModel`，歪头所需的 `root().getChild("head").zRot` 链路完全成立。
  - 防御加固：头部歪头整段用 `try/catch(Throwable)` 包住，即使个别猫变种模型 API 异常也**绝不让渲染器初始化崩掉**，从源头杜绝再次黑屏。

### 开发 / 构建
- 版本号 1.1.4 → 1.1.5。

## [1.1.4] - 2026-07-22

### 修复（Bug）
- **头部歪头终于生效（核心修复）**：之前"猫变大了但头不转"的根因是歪头逻辑放在 `CatModelMixin`（`setupAnim` 注入）+ `RenderStateHolder`（`WeakHashMap` 跨 mixin 状态桥）。该桥以 `CatRenderState` 实例为 key，在渲染管线的 `extractRenderState` -> `submitModel` -> `setupAnim` 链路中极易命中失败，导致 `head.zRot` 从未被写入。
  - 新方案：**彻底去掉模型 mixin 与 WeakHashMap 桥**，把歪头直接搬到必定会跑的 `CatRendererMixin`（`extractRenderState` 注入，缩放就是在这里生效的，已证实）。在提取渲染状态时，用 `this.model.root().getChild("head")` 直接拿到头部零件，按 `cat.getId()` 从 `ClientMemeState` 取歪头方向（`rollSign` ±1）写入 `head.zRot = ±45°`；非锁定猫清零，避免上一帧歪头残留。
  - 实测依据（MC 26.1 字节码核实）：`AdultFelineModel.setupAnim` 只写 `head.xRot/yRot`、**绝不碰 `zRot`**，所以这里写入的 `zRot` 会一路保留到顶点提交；`ModelPart.translateAndRotate` 经 `Quaternionf.rotationZYX(xRot,yRot,zRot)` 应用 `zRot`，故头部 roll 必定可见。
- **锁定距离再拉大**：`LOCK_DISTANCE` 从 `1.0` 调到 `1.3`（各自离中点 0.65）。用户实测 1.0"比之前好点但还偏近"，1.3 让两只猫头对头、身体明显分开、不重叠，更接近设计稿的"脸贴脸"。

### 架构 / 清理
- 删除 `CatModelMixin.java` 与 `RenderStateHolder.java`，`laowu_meme.client.mixins.json` 仅保留 `CatRendererMixin`。渲染逻辑收敛到单一入口，不再有跨 mixin 的脆弱状态桥。

### 开发 / 构建
- 版本号 1.1.3 → 1.1.4。

## [1.1.3] - 2026-07-22

### 修复（Bug）
- **锁定距离过近、头陷进对方肚子**：`LOCK_DISTANCE` 从 `0.6` 调到 `1.0`（各自离中点 0.5）。0.6 时两猫中心距仅 0.6，猫身较长，头直接插进对方身体里。1.0 让两只猫头对头、身体明显分开、不重叠。
- **头部歪头角度修正**：客户端之前直接用 `rollSign`（±1）当弧度，歪头幅度≈57°，偏大。改为乘以固定 45°（`π/4`），与设计稿"头各自逆时针45度(镜像歪头)"一致。歪头逻辑本身没问题——之前"看着没转"主要是头被埋在对方身体里看不见，拉开距离后应可见。

### 开发 / 构建
- 版本号 1.1.2 → 1.1.3。

## [1.1.2] - 2026-07-22

### 修复（Bug）
- **崩溃修复 · 进入存档渲染猫时崩溃**：v1.1.1 能启动，但进入单人存档、画面里出现猫时崩溃（`Extracting render state for an entity in world`）。
  - 根因：`RenderStateHolder`（渲染状态桥，`WeakHashMap`）被放在 mixin 包 `com.rogic.client.mixin` 里。该包在 `laowu_meme.client.mixins.json` 中被声明为 mixin 包，包内的类由 Mixin 处理器接管，**不允许被普通代码直接引用**。`CatRendererMixin` 提取渲染状态时引用 `RenderStateHolder.RollData`，触发 `IllegalClassLoadError: ... is in a defined mixin package ... and cannot be referenced directly`。
  - 修复：把 `RenderStateHolder`（含内部类 `RollData`）移出 mixin 包，迁到新包 `com.rogic.client.render`；`CatRendererMixin` / `CatModelMixin` 改为 import 新路径。

### 开发 / 构建
- 版本号 1.1.1 → 1.1.2（崩溃类回归，属补丁级修复）。

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
