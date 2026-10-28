# Room 生成实现
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# 音量键回调经兼容层反射分发，保留 VolumeProviderCompat 子类
-keep class * extends androidx.media.VolumeProviderCompat { *; }

# DataStore
-dontwarn androidx.datastore.**
