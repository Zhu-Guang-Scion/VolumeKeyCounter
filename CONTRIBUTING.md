# 贡献指南

## 环境要求

- JDK 17+
- Android SDK（compileSdk 35，或直接用最新版 Android Studio 打开项目）
- 一台**安卓真机**（音量键路由行为在模拟器上不可靠，必须真机验证）

## 构建与测试

```bash
# Debug 构建
./gradlew assembleDebug

# Release 构建（需要自备签名密钥，参考 app/build.gradle.kts 中 signingConfigs）
./gradlew assembleRelease
```

> 注意：`settings.gradle.kts` 配置了阿里云 Maven 镜像优先（适合中国大陆网络环境），
> 海外贡献者可自行调整为 `google()` / `mavenCentral()` 直连。

## 代码规范

- 语言：Kotlin，UI 层 Jetpack Compose
- 注释：中文注释，核心逻辑（音量键路由、水印绘制、Room 迁移）需写清原理
- 数据安全：所有修改计数值的操作必须在 Room 事务内完成（保证进程被杀不丢数）
- 降级设计：新功能须考虑权限拒绝、服务不可用等路径的优雅降级

## PR 约定

- 提交信息格式：`vX.Y.Z：变更摘要` 或 `fix/description`
- 涉及数据库表结构变更时必须提供 Migration（参考 `AppDatabase.kt`）并真机验证迁移不崩溃
