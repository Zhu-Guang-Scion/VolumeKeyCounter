# 音量键计数器（VolumeKeyCounter）

以手机**音量键**为核心交互的手动计数器 App（安卓优先）。为盘点、清点等需要长时间
手持计数的场景设计，核心差异化功能是**可自定义命名的检查记忆回退点**。

## 功能（v1.1.0）

- **音量键计数**：音量上键 +1，音量下键 −1（App 在前台时按键被拦截，系统音量不变、不弹音量条）
- **长按防误计（P0 修复）**：前台长按严格只计 1 次、事件全部消费（系统音量不动）；熄屏/后台长按由
  服务端突发抑制处理（200ms 最小间隔 + 400ms 内 ≥4 次判定为连发并静默至松手）
- 屏幕大字号计数；屏幕按钮 +1/−1 备用；每次计数震动反馈；屏幕常亮开关（顶栏锁图标）
- **检查点**：命名保存 / 回退（反向事件留痕、不抹历史）/ 删除
- **复位**（v1.1.0）：红色按钮 + 二次确认防呆；计数归零并清空本计数器检查点，
  事件日志完整保留（复位动作本身记一条 source=reset 事件）
- **口袋模式（M3）**：mediaPlayback 前台服务 + MediaSessionCompat.setPlaybackToRemote
  (VolumeProviderCompat) + 循环静音 AudioTrack——熄屏/锁屏/后台时音量键持续计数；
  通知标题实时显示「当前计数：N」（锁屏直接可见，VISIBILITY_PUBLIC + BigText），
  下拉展开通知可见「−1 修正」「结束」按钮；开启时申请音频焦点（会暂停后台音乐）
- **多计数器管理**：新建/重命名/切换/删除，各计数器独立检查点与日志（顶栏列表图标）
- **CSV 导出**：概要+检查点+事件明细，UTF-8 带 BOM（Excel 中文不乱码），存入 Download/
  （Android 10+，低版本走分享兜底）；另有「分享汇总」纯文本快速分享
- **国产 ROM 保活指引**：按厂商（小米/华为/荣耀/OPPO 系/vivo 等）给出设置步骤，
  首次开启口袋模式时提示，也可从计数器管理页进入
- **安全启动**：前台服务启动带 Android 12+ 后台启动限制保护（挂起待恢复后补启动）
- **事件日志**：每次 +1/−1/回退 原子落库（Room 事务），进程被杀不丢数

## 技术选型（调研结论）

- **Kotlin + Jetpack Compose 原生**：音量键捕获（前台 `dispatchKeyEvent`；后续 M3 的
  熄屏方案 MediaSession+VolumeProvider+前台服务）必须原生 Kotlin，跨平台框架无收益
- 版本：Gradle 8.9 / AGP 8.7.3 / Kotlin 2.0.21 / compileSdk 35 / minSdk 26（Android 8.0+）
- 数据：Room（counters / checkpoints / count_events 三表）+ DataStore（激活计数器）
- 已排除方案：无障碍服务（熄屏收不到事件）、`VOLUME_CHANGED_ACTION` 广播（非公开 API）

## 兼容性边界

| 平台 | 支持 | 说明 |
|---|---|---|
| 常规安卓 8.0+ | ✅ | 前台音量键计数可用 |
| 老鸿蒙 HarmonyOS 2/3/4/EMUI | ✅ 直接装 APK | AOSP 底座；后台保活需手动设置（M4 做引导） |
| 纯血鸿蒙 NEXT/5/6/7 | ❌ 装不上 APK | 需 ArkTS 独立开发，且仅前台计数（平台限制） |
| iOS | 日后 SwiftUI 独立实现 | 仅前台音量键推断计数（苹果限制） |

## 本机构建（Windows + Git Bash）

工具链位置（均已装好）：

- JDK 17：`C:/Program Files/Java/jdk-17.0.5`
- Android SDK：`D:/AI Coding/VolumeKeyCounter/android-sdk`（platform-tools / android-35 / build-tools 34）
- Gradle 缓存：`GRADLE_USER_HOME=D:/AI Coding/VolumeKeyCounter/.gradle-home`
- Gradle 8.9 发行版：`D:/AI Coding/VolumeKeyCounter/tools/gradle-8.9`（工程内 wrapper 指向腾讯镜像）

构建 debug APK：

```bash
cd /e/ai_coding_xiangmu/VolumeKeyCounter
export JAVA_HOME="C:/Program Files/Java/jdk-17.0.5"
export GRADLE_USER_HOME="D:/AI Coding/VolumeKeyCounter/.gradle-home"
./gradlew.bat assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

安装到真机（需 USB 调试）：

```bash
"/d/AI Coding/VolumeKeyCounter/android-sdk/platform-tools/adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk
```

> 音量键路由行为模拟器不可靠，务必真机验证。

## 路线图

- [x] M1 前台可用版：工程骨架 / 前台音量键计数 / Room 持久化 / 震动反馈
- [x] M2 检查点核心：命名检查点 / 回退留痕 / 事件日志
- [x] M3 熄屏口袋模式：前台服务 + MediaSession + 静音音轨 + 常驻通知（物理按键熄屏终验待做）
- [x] M4 打磨：多计数器 UI / ROM 保活指引 / CSV 导出（多机型测试待补）
- [ ] 后续：事件日志查看界面 / Widget 桌面计数 / release 混淆与签名 / 应用市场分发

## 参考

音量键熄屏捕获方案参考开源项目 [VolumePowerApp](https://github.com/depaolaluichi123/VolumePowerApp)
（Apache-2.0）与 [boogie/cordova-plugin-boogie-volumebuttons](https://github.com/boogie/cordova-plugin-boogie-volumebuttons)（MIT）。
