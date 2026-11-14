# 闪退诊断日志

游戏异常退出后，弹窗显示退出类别及可获取的异常或信号。点击游戏页的日志导出按钮，
ZIP 中的 `game-sidecar/<游戏包名>/crash-report.txt` 包含原因摘要、证据和运行上下文。
关闭弹窗不会删除报告；下次检测到异常时更新报告。报告中的会话时间用于区分不同运行。

诊断包含：

- Java/Kotlin 未捕获异常的异常链、最深层 `Caused by` 和对应调用位置。
- .NET 明确记录的未处理异常；普通 BepInEx Error 不单独作为闪退原因。
- 原生崩溃的信号、signal code、fault address、abort message 和可读取的堆栈。
- Android 提供的进程退出原因、PID、描述、PSS/RSS（KB）及 ANR 等系统记录。
- 游戏/启动器版本、整合包、设备、启动时可用内存和启动阶段日志。

`launcher-session.txt` 记录当前启动会话；诊断按启动时间和 PID 过滤 logcat。
游戏进程持续采集所有日志标签，覆盖 Fusion、LibMain、Dobby、CoreCLR 和模组输出。
每个 logcat 分片上限 4 MiB，保留当前及上一分片；原因报告读取有界尾部，
导出的 sidecar 文件保留更长上下文。

Android 11 起可以查询系统退出记录。Android 12 起系统可能提供原生 tombstone，
其格式是二进制 protobuf，导出为 ZIP 根目录的 `native-<时间>-<PID>.pb`，
供开发者使用 Android tombstone 工具进一步解析。系统 trace 最多读取 512 KiB；
过大的原生 protobuf 不导出残缺文件，并在报告中说明。

系统权限、日志轮转及退出记录保留期限会影响可用证据。库名、调用位置或最后一个
报错的模组不等于已确认的根因；仅有 SIGKILL 时也不会推断为内存不足。
原生符号缺失时需要对应版本的符号文件，可能还需要真机复现。

验证：`./gradlew :app:testDebugUnitTest`。诊断单元测试覆盖异常链、原生信号、
内存回收、普通模组错误、旧会话及其他进程日志隔离。真机还需验证快速启动崩溃、
原生崩溃、闪退后再次启动和日志导出。
