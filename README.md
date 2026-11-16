# 青课

广州软件学院课表，也适用于仲恺、理工、生态、中山学院，以及自己填正方 / 强智 / 联奕地址。

默认学校是广软。学号密码只在「我的」里登录，不会写进仓库。

## 学校

- 广州软件学院：默认统一身份认证（cas.gzus.edu.cn），可进正方、办事大厅、请假、宿舍水电和选课；也可改回正方直接登录。宿舍水电走一卡通的公开接口，不登录也能查
- 仲恺农业工程学院：强智
- 广州理工学院：联奕门户
- 广东生态工程职业学院：综合系统（AIC）出票，教务是强智，密码在综合系统那侧用 RSA 加密
- 电子科技大学中山学院：强智（jwgln.zsc.edu.cn），账号密码各做一次 Base64，登录页无验证码；作息按校方公布的时间表
- 自定义教务：开发者选项里选类型、填 origin，正方可覆盖路径

学校没公布的作息、空教室、验证码规则，青课不会编。自定义「有节次时间」打开后按正方常见 16 节提醒，不是官方表。

## 更新

当前开发版本 **1.8.2**（versionCode 88）。Android 8.0（API 26）及以上。广软调休默认从 qingke.lazzyy.cn 自动拉取；打开应用会检查更新，有新版本会提示。

- GitHub Release：应用里检查 `LazyBonesLZY/qingke` 最新版，再跳到 Release / APK
- 网盘：浏览器打开 [https://storage.lazzyy.cn/@s/KB](https://storage.lazzyy.cn/@s/KB)

1.8.1 整合课表翻页与提醒边界修复、默认折叠周末、选课账号隔离与分页、同步失败提示、长字段排版和可关闭的今日贴士；移除小爱导入。Live 通知按系统能力逐级回退到普通通知，HyperOS 私有岛仍需正式业务和签名审批。集成与验证记录见 [docs/integration-20261116.md](docs/integration-20261116.md)。

1.8.2 接入可配置的小米焦点通知/超级岛与 ColorOS 本地流体云协议，增加异步取消和过期保护。未配置或未获授权时保留系统上课通知。配置及验证边界见 [docs/LIVE_OEM_PRIVATE_ADAPTERS.md](docs/LIVE_OEM_PRIVATE_ADAPTERS.md)。

## 构建

JDK 17。签名用本地 `keystore.properties` 和 `*.jks`，这两项已忽略。

```bash
./gradlew :androidApp:assembleRelease
```
