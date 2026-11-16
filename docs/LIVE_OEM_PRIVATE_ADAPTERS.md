# 私有 Live 适配集成记录

本次代码版本为 **1.8.2（88）**，基于 `d88cc64` 集成
`qingke-private-live-adapters-20261116.zip`。压缩包仍按旧基线 `52a46eb` 编写，未直接运行其覆盖脚本。

输入 ZIP 的 SHA256：
`b110fef256ad65f4220628fb290c4ab03cbb9184b3d4cffb5218372452a6d157`。

当前只有源码和宿主验证结果，没有设备、ROM、已安装 APK、实际加载文件或现场 SHA。
本文不代表厂商真机验收，也未配置实际厂商业务标识。

## 配置

在用户级 `~/.gradle/gradle.properties` 或 CI 的 Gradle 参数中填写厂家确认的配置，勿使用示例标识发布：

```properties
qingkeHyperOsBusiness=YOUR_CONFIRMED_BUSINESS
qingkeOppoIntentName=YOUR_APPROVED_INTENT_NAME
qingkeOppoIntentVersion=YOUR_APPROVED_VERSION
qingkeOppoIntentDescription=青课课程实时进度
qingkeOppoServiceIds=YOUR_APPROVED_SERVICE_ID
qingkeOppoMilestoneUpcoming=YOUR_APPROVED_UPCOMING_CODE
qingkeOppoMilestoneOngoing=YOUR_APPROVED_ONGOING_CODE
qingkeOppoMilestoneEnded=YOUR_APPROVED_ENDED_CODE
```

小米业务标识默认空，不沿用压缩包内的 `education` 默认值。OPPO 支持逗号或分号分隔的多个服务 ID；
三个里程碑码都必须为非负整数。意图名、版本或服务 ID 缺失时，运行时禁用私有共享并生成空注册表。

Android 构建生成 `assets/intelligent_intent_config.json`，其中注册名称和版本与清单配置同源。
运行时配置通过字符串资源写入清单，避免单个纯数字服务 ID 被解析为整数。
资源目录使用 AGP Variant API 注册，任务依赖自动传递；已启用 AGP 9.2 默认关闭的 `resValues` 功能。

## 调用链与回退

应用、后台广播、前台服务 → `notifyLiveClass` → 既有通知资格与逐级回退 → Android 通知接受结果。

**小米**：已有后台权限查询器检查 `canShowFocus`，未知、过期或查询失败时不使用旧授权。
业务配置与授权满足、协议版本大于零时使用焦点通知；协议版本至少 3 且
`persist.sys.feature.island` 为真时增加岛模板。属性读取失败时使用焦点模板，不据品牌认定支持岛。
扩展参数构建或发布异常会重新构造原生候选，最后回退普通通知。原生与普通候选均不附带私有参数。
字段、能力查询和模板结构依据[小米开发指南](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2131)。

**OPPO / OnePlus / realme**：先发布当前 Android 候选，只有成功接受后才在专用线程提交私有替换。
读取系统意图开关与 Provider authority，调用 `queryFeature`，同时要求框架开启、用户允许共享、
指定意图受支持且版本匹配。缺失支持列表、畸形结果和接口异常均保留已经接受的 Android 通知。
随后通过 `shareIntent` 提交本地 TASK 进度数据，`serviceId` 使用字符串数组；创建、更新、结束分别使用
`actionStatus=0/1/2`。本地协议依据 [T/TAF 283—2025](https://www.taf.org.cn/StdDetail.aspx?stdType=TAF&uid=ae71dde6-7d33-460f-b8e4-37c58c7523bf)，
TASK 模板参考[厂商通道说明](https://help.aliyun.com/zh/document_detail/2997310.html)。云推送的对象格式不用于本地调用。

OPPO 返回接受结果后，只有仍属于当前课程和阶段，且通知权限、渠道和划掉状态仍允许时，才撤下普通通知。
`notifyLiveClass` 的返回值始终表示真实 Android 发布结果，排入工作线程不算发送成功。
全部 Android 候选失败时仍使用上一轮每课程最多两次重试。

## 生命周期修正

- 每次通知刷新先使旧请求失效，再发布新通知。课程切换、同课程刷新、阶段超时和主动取消均使迟到响应失效。
- Provider 返回后重新检查请求与权限，迟到成功只尝试结束旧私有任务，不撤新通知，也不重发旧通知。
- 私有工作线程从不负责重发普通通知，因此取消后不会通过失败回退复活提醒。
- 切换课程前先结束已有私有实体；结束失败时保留恢复记录并使用普通通知，避免遗忘旧任务后再创建新任务。
- 已接受的私有任务持久化，进程重建后的取消/换课仍能尝试结束；结束成功后才删除记录。
- 沿用应用的删除回调身份与课程边界调度。倒计时开关同时应用于私有文案。
- 小米超时时间按当前阶段剩余时长设置，去掉原包额外增加的 5 分钟；原生通知保留毫秒阶段超时。

Android 通知使用 `LIVE_ID`；OPPO 使用按课程稳定的 `identifier/entityId`，不是另一条 Android 通知 ID。
从普通通知切到私有卡片不是系统提供的原子操作，是否短暂双卡、是否能响应厂商卡片自身的划掉操作仍需真机验证。

## 验证记录

- Kotlin 2.4.20 直接编译：71 个共享/桌面源文件、79 个共享/Android 源文件通过。
- 直接 JUnit：80 项逻辑测试通过，包括新增的配置缺项、授权缺项、版本匹配、JSON 转义、
  取消、同课程新帧、课程阶段切换、过期窗口和倒计时关闭测试。
- JDK 21 实际执行 `:composeApp:desktopTest --offline`：**104 项通过，0 失败、0 错误、0 跳过**，含 24 项渲染与手势测试。
- 实际 Gradle 生成任务与 Android 资源处理：验证默认空配置、缺项配置、完整测试配置及特殊字符 JSON；
  测试配置仅用于本机构建检查，结束后恢复空注册表。
- `git diff --check`、XML 与版本名称/代码一致性检查通过。

本机 Gradle 命令需要已有 `gradle/native/wsl-localhost-connect.so` 处理 WSL 临时端口问题，
并覆盖 `org.gradle.java.home`。本次只运行测试、注册表和资源处理，没有打包或安装 APK。
最初资源配置失败暴露了旧 SourceSet Provider API 与默认关闭的 `resValues` 问题，均已修正后重跑。

源码输出 SHA256：

- `LiveOemAdapters.kt`：`d3fceac3b1a03e48e73c173ae4f5bb399dd418a90f15f19679c3a1b4ce7ce4df`
- `LiveOemPolicy.kt`：`1d6574829457449b55a9041527fe19ecf1aee6abdd3d6783a45ce5a310f1dd3b`

## 仍需验收

需要厂家确认的应用包名、实际签名、业务、服务和场景授权。普通通知权限不等于小米后台本地通知白名单，
本地意图调用也不提供进程完全死亡后的云推送。没有接入 MiPush/OPPO 云端凭据或服务端。

下一步分别在 HyperOS 焦点/岛设备、ColorOS 有/无意图支持的设备和 AOSP 上验证：实际权限撤销、
Provider 拒绝与阻塞、锁屏冻结、进程重建、课程切换、用户划掉、静态文案是否及时变化及结束后的卡片消失。
Provider 接受、能力返回和宿主测试都不能证明 SystemUI 实际展示。同步 Binder 调用若永久阻塞，
本地串行工作线程的清理仍需等待它返回；普通 Android 通知已先行发布。

回滚本次提交可恢复 `d88cc64` 的代码行为。若设备上已经存在私有实体，应先在应用中关闭提醒并确认系统卡片结束。
上一份 `integration-20261116.md` 是 1.8.1 的历史记录；其中“尚未接入 OPPO 私有通道”不再描述本次源码状态。
