# 青课

广州软件学院课表，也适用于仲恺、理工，以及自己填正方 / 强智 / 联奕地址。

默认学校是广软。学号密码只在「我的」里登录，不会写进仓库。

## 学校

- 广州软件学院：正方 V-9
- 仲恺农业工程学院：强智
- 广州理工学院：联奕门户
- 自定义教务：开发者选项里选类型、填 origin，正方可覆盖路径

学校没公布的作息、空教室、验证码规则，青课不会编。自定义「有节次时间」打开后按正方常见 16 节提醒，不是官方表。

## 更新

当前版本 **1.3.1**（versionCode 20）。

- GitHub Release：应用里检查 `LazyBonesLZY/qingke` 最新版，再跳到 Release / APK
- 网盘：浏览器打开 [https://storage.lazzyy.cn/@s/KB](https://storage.lazzyy.cn/@s/KB)

## 构建

JDK 17。签名用本地 `keystore.properties` 和 `*.jks`，这两项已忽略。

```bash
./gradlew :androidApp:assembleRelease
```
