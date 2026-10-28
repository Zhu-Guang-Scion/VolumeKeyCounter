package com.volumekey.counter.oem

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * 国产 ROM 后台保活指引：按厂商给出设置路径。
 * 各 ROM 均有「杀后台」行为，口袋模式（前台服务）在以下设置完成后最为稳定。
 */
object OemKeepAlive {

    fun brandName(): String {
        val m = (Build.MANUFACTURER ?: "").lowercase()
        return when {
            m.contains("xiaomi") || m.contains("redmi") -> "小米 / HyperOS / MIUI"
            m.contains("huawei") -> "华为 / HarmonyOS"
            m.contains("honor") -> "荣耀"
            m.contains("oppo") || m.contains("realme") || m.contains("oneplus") -> "OPPO / 一加 / 真我"
            m.contains("vivo") || m.contains("iqoo") -> "vivo / iQOO"
            m.contains("samsung") -> "三星"
            else -> m.replaceFirstChar { it.uppercase() }.ifEmpty { "当前设备" }
        }
    }

    /** 本品牌的保活设置步骤（据 dontkillmyapp.com 与各厂商设置路径整理） */
    fun steps(): List<String> {
        val m = (Build.MANUFACTURER ?: "").lowercase()
        return when {
            m.contains("xiaomi") || m.contains("redmi") -> listOf(
                "设置 → 应用设置 → 应用管理 → 音量键计数器 → 省电策略 → 改为「无限制」",
                "设置 → 隐私保护与安全（或权限管理） → 自启动管理 → 允许本应用自启动",
                "最近任务界面：长按本应用卡片 → 选择「锁定」，避免清理时被结束"
            )
            m.contains("huawei") || m.contains("honor") -> listOf(
                "设置 → 应用和服务 → 应用启动管理 → 找到本应用 → 关闭「自动管理」，手动管理中打开：自启动、关联启动、后台活动 三个开关",
                "设置 → 电池 → 更多电池设置 → 关闭「休眠时始终保持网络连接」对本应用的影响（如有）"
            )
            m.contains("oppo") || m.contains("realme") || m.contains("oneplus") -> listOf(
                "设置 → 电池 → 更多 → 关闭「应用速冻」对本应用的限制",
                "设置 → 应用管理 → 本应用 → 允许后台运行 / 耗电管理选「允许后台运行」"
            )
            m.contains("vivo") || m.contains("iqoo") -> listOf(
                "设置 → 电池 → 后台耗电管理 → 本应用 → 允许后台高耗电",
                "设置 → 应用管理 → 本应用 → 允许自启动"
            )
            else -> listOf(
                "系统设置 → 应用 → 音量键计数器 → 电池 → 不受限制 / 关闭电池优化",
                "如仍有问题：最近任务里锁定本应用"
            )
        }
    }

    /** 打开本应用的系统详情设置页（各品牌通用的兜底入口） */
    fun openAppDetails(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }
}
