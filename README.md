# VolumeKeyCounter · 音量键计数器

> 专为**审计盘点**设计的安卓计数器——用音量键代替手点，熄屏也能数。

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
![Platform: Android 8.0+](https://img.shields.io/badge/Platform-Android%208.0%2B-green)
![Language: Kotlin](https://img.shields.io/badge/Language-Kotlin-orange)

## 适用场景

- **存货监盘**：审计师逐项清点库存，音量键 +1/-1，不用看屏幕
- **固定资产盘点**：导入清单 CSV，逐项计数 + 拍照取证（水印相机）
- **样本抽样清点**：凭单、凭证数量核对
- 任何需要**长时间手持计数**且希望**留痕**的场景

## 核心功能

### 计数器
- **音量上键 +1 / 音量下键 −1**（前台拦截，系统音量不变、不弹音量条）
- **长按防误计**：长按只计 1 次，连发自动抑制
- **检查记忆回退点**：随时保存命名检查点，一键回退（反向事件留痕）
- **口袋模式**：熄屏/锁屏/后台用音量键持续计数，锁屏通知实时显示
- 多计数器管理 / 复位（二次确认）/ CSV 导出（自动弹转发）

### 清单盘点
- **导入 CSV 清单**（五列：盘点对象名称 / 自定义列 / 账面数量 / 实盘数量 / 备注）
- 兼容 UTF-8 / GBK / Tab 分隔 / 全角逗号 / 引号内换行 / 千分位数字
- 点条目 → 全屏盘点页，音量键直接计入该项实盘数
- 筛选标签（全部/未盘/已盘/差异）+ 名称检索 + 下一未盘直达
- 实盘数与备注可手动输入修改，撤销已盘标记（带确认）

### 水印相机（审计取证）
- 拉起**系统原生相机**拍照，自动在左下角叠加水印：
  - 盘点对象名（大号标题）
  - 拍摄时间（精确到秒）
  - 经纬度坐标
  - 经纬度对应的中文地址（逆地理编码，不可用时优雅降级）
- **一次点击连拍多张**，每张水印独立（各自时间/位置）
- 照片按 `盘点对象名-序号.jpg` 自动命名，存入相册 `Pictures/盘点取证/`
- 照片同时回写**拍摄时间与 GPS 到 EXIF**（审计溯源）
- **App 内照片画廊**：缩略图浏览、点开大图、删除单张（计数实时联动）
- 删除后拍照数字一致（自动对账清理相册已不存在的记录）

## 技术选型

| 选型 | 理由 |
|---|---|
| Kotlin + Jetpack Compose | 音量键捕获（前台 `dispatchKeyEvent` + 熄屏 MediaSession/VolumeProvider）必须原生 Kotlin；App 仅 3 个界面，Compose 学习曲线有界 |
| Room | 事务化事件日志，进程被杀不丢数 |
| minSdk 26 / targetSdk 35 | 兼容 Android 8.0 - 16 |

### 熄屏计数原理（Pocket Mode）

熄屏后普通 App 收不到音量键事件。本 App 使用 `mediaPlayback` 前台服务 +
`MediaSessionCompat.setPlaybackToRemote(VolumeProviderCompat)` + 循环静音 `AudioTrack`：
系统将音量键路由给本会话的 `onAdjustVolume` 回调，实现**熄屏/锁屏/后台持续计数**。
（参考开源项目 [VolumePowerApp](https://github.com/depaolaluigi123/VolumePowerApp)，Apache-2.0）

## 兼容性

| 平台 | 支持 | 说明 |
|---|---|---|
| 常规安卓 8.0+ | ✅ | 小米/OPPO/vivo/三星/荣耀等 |
| 老鸿蒙 HarmonyOS 4.x / EMUI | ✅ | 直接装 APK；后台保活需手动设置 |
| **纯血鸿蒙** NEXT/5/6/7 | ❌ | 不支持安装安卓 APK（Mate 70 后部分新机） |
| iOS | 未实现 | 苹果限制：仅前台可推断音量键，熄屏不可行 |

## 构建方法

```bash
git clone https://github.com/Zhu-Guang-Scion/VolumeKeyCounter.git
cd VolumeKeyCounter

# 需要 JDK 17 + Android SDK
export JAVA_HOME="path/to/jdk17"
./gradlew assembleRelease

# 产物: app/build/outputs/apk/release/app-release.apk (~1.4MB)
```

发布签名密钥不入仓库（`keystore/signing.properties` 已 gitignore），debug 构建直接可用。

## 项目结构

```
app/src/main/java/com/volumekey/counter/
├── data/          # Room 实体、DAO、Repository（三张主表 + 盘点三张表）
├── service/       # 熄屏前台服务、音量键路由
├── ui/            # Compose 界面（计数器/清单盘点/照片画廊）
└── util/          # CSV 导入导出、水印绘制、照片存储、定位快照
```

## 开发历程

从需求到 v1.5.2 共 12 个版本迭代，全程真机验证（小米 Android 16/HyperOS）：

| 版本 | 里程碑 |
|---|---|
| v1.0.0 | 音量键计数 + 检查点 + 口袋模式 |
| v1.1.0 | 复位按钮 + 页脚水印 |
| v1.2.0 | 清单盘点板面（CSV 导入/逐项计数/检索） |
| v1.3.0 | 筛选标签 + 盘点导出 + 三智能体评审 |
| v1.4.0 | 水印相机（系统相机/时间/经纬度/地址水印） |
| v1.5.x | 照片画廊 + 缩略图 + 计数一致性 + 水印美化 |

## 隐私声明

- **零网络权限**：本 App 未申请 INTERNET 权限，物理上无法上传任何数据（含定位、照片）
- 所有数据（计数记录、检查点、清单条目、照片）**仅存储在手机本地**
- 定位权限仅用于拍照时获取经纬度生成水印，拒绝授权后水印自动省略位置行

## License

[MIT](LICENSE)

## 致谢

- [VolumePowerApp](https://github.com/depaolaluigi123/VolumePowerApp) — 熄屏音量键捕获方案参考
- [boogie/cordova-plugin-boogie-volumebuttons](https://github.com/boogie/cordova-plugin-boogie-volumebuttons) — 音量键路由兼容性参考
- [dontkillmyapp.com](https://dontkillmyapp.com) — 国产 ROM 保活指引参考

---

**code by 逐光**
