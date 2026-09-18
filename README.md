# 青课

广州软件学院课表，也适用于仲恺、理工，以及自己填正方 / 强智 / 联奕地址。

默认学校是广软。学号密码只在「我的」里登录，不会写进仓库。

## 学校

- 广州软件学院：默认统一身份认证（cas.gzus.edu.cn），可进正方、办事大厅、请假、宿舍水电和选课；也可改回正方直接登录
- 仲恺农业工程学院：强智
- 广州理工学院：联奕门户
- 自定义教务：开发者选项里选类型、填 origin，正方可覆盖路径

学校没公布的作息、空教室、验证码规则，青课不会编。自定义「有节次时间」打开后按正方常见 16 节提醒，不是官方表。

## 更新

当前版本 **1.6.18**（versionCode 41）。Android 8.0（API 26）及以上。小组件和 Live 通知按首页课卡重排了外观。

- GitHub Release：应用里检查 `LazyBonesLZY/qingke` 最新版，再跳到 Release / APK
- 网盘：浏览器打开 [https://storage.lazzyy.cn/@s/KB](https://storage.lazzyy.cn/@s/KB)

## 构建

JDK 17。签名用本地 `keystore.properties` 和 `*.jks`，这两项已忽略。

```bash
./gradlew :androidApp:assembleRelease
```
