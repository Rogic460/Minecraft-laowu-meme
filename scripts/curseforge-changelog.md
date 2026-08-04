# laowu meme v1.2.1

## 修复（Bug）
- **战吼 / 导入音频远离猫不衰退、骤然消失**：根因是 MC 对「流式(stream) / 磁盘读取」音频自带的 distance attenuation 在某些路径不生效。修复：关闭播放实例的 MC 自带衰减（`attenuation = NONE`），改为在 `getVolume()` 里按「玩家到两只猫中点」的距离手动线性计算 —— 0~16 格从 1 平滑降到 0，超过 16 格静音、32 格停止。所有音频（含战吼、导入）行为一致、均为自然衰退。
- **服务端从不选战吼**：`ServerMemeManager` 的 `soundId` 随机原本只在 laowu2 / qiliang 间取，已纳入战吼（zhanhou），三者都可能被服务端选中。

## 新增（New）
- **支持 Minecraft 1.21.11 (Fabric)**：从 26.x 主线下移适配，功能与 26.x 版完全一致（触发动画 / 体型放大 / BGM / 右键打断 / 导入音频与配置界面）。已在 1.21.11 实测通过。
- **支持 Minecraft 1.21.0 / 1.21.1 (Fabric)**：经典渲染管线（无 RenderState）适配，从 1.21.11 下移到经典管线写法。已在 1.21.0 与 1.21.1 两版实测通过，单个 jar 通吃两版。

## 适配说明（Porting notes）
- 1.21.11 构建改用 Mojang 官方映射（mojmap）+ Loom 1.13.6，源码与 26.x 主线几乎共用，仅两处差异：
  - 1.21.11 猫模型不拆分成年/幼年，动画 Mixin 目标改为 `FelineModel` 单类；
  - Fabric API 网络注册方法名 `clientboundPlay()` → `playS2C()`。
- 1.21.0 / 1.21.1 经典管线关键差异（与 26.x / 1.21.11 的 RenderState 写法不同）：
  - 放大注入 `CatRenderer.scale(Cat, PoseStack, float)`（绝不能注入继承的 `render`，否则运行期 `InvalidInjectionException` 黑屏）；
  - 模型部件经 `OcelotModel` 的 `@Accessor` 取（head/body/tail2/四肢字段声明在父类 `OcelotModel` 而非 `CatModel`，`@Shadow` 不跨继承）；
  - 元数据范围为 `[">=1.21", "<1.21.2"]`，一个 jar 同时支持 1.21.0 与 1.21.1。

## 环境要求（Requirements）
- 26.x：Minecraft 26.1.2 / 26.2 + 对应 Fabric Loader 与 Fabric API
- 1.21.11：Minecraft 1.21.11 + Fabric Loader ≥ 0.19.2 + Fabric API ≥ 0.141.4
- 1.21.0 / 1.21.1：Minecraft 1.21.0 / 1.21.1 + Fabric Loader ≥ 0.16.10 + Fabric API ≥ 0.116.12

## 适用版本
- `laowu_meme-1.2.1+26.1.2.jar` — Minecraft 26.1.2 (Fabric)
- `laowu_meme-1.2.1+26.2.jar` — Minecraft 26.2 (Fabric)
- `laowu_meme-1.2.1+1.21.11.jar` — Minecraft 1.21.11 (Fabric)
- `laowu_meme-1.2.1+1.21.0-1.21.1.jar` — Minecraft 1.21.0 / 1.21.1 (Fabric，单 jar 通吃两版)
