# 环信 Android SDK 测试工具

独立测试项目，Java + Android Views。共享初始化与登录，具体测试各自独立成页。Android 多 bodies 接口已接通，APK 编译通过；用户已确认双 Android 模拟器、不同账号的文本和文件多 body 收发正常，跨端及其他边界验证仍保留。

## 源码依赖与构建

三个独立仓库保持同级：

```text
zuoyu_native/
  emclient-linux/
  emclient-android/
  easemob_test_tools_on_android/
```

Gradle 引用兄弟 Android SDK 模块，通过 `externalNativeBuild.ndkBuild` 调用 `emclient-android/hyphenatechatsdk/ndk/jni/Android.mk`，再包含 `emclient-linux/Android.mk`，自动编译当前 Core/JNI 源码。native 构建和产物打包由 Android Gradle Plugin 管理，不替换 SDK 仓库的已有库。`jniLibs` 只使用本项目 `build/cipher-libs` 中复制的 cipherdb 依赖，aosl 使用 SDK 声明的依赖；不从 SDK `libs` 或旧 `build/native/libs` 打包 `libhyphenate.so`。

Android Studio 直接打开本项目并 Sync 即可加载 Java 和 C++ 源码构建模型。demo 已启用上述 `externalNativeBuild`，无需手动打开 SDK 中的同名配置；若已打开也不会重复引入 native 库。修改 Core/JNI 后正常 Build/Run 即会参与增量编译。真机 Native/LLDB 断点尚未验证，Core Android.mk 的现有优化参数保持不变。

需要 JDK 17、Android SDK 35、NDK 27.2.12479018。通过 `ANDROID_HOME` 或本机 `local.properties` 配置 Android SDK。

在本项目目录运行：

```sh
./gradlew :app:assembleDebug
```

默认包含 `arm64-v8a`、`x86_64`；只编译真机可使用 `-PtestAbis=arm64-v8a`。产物为 `app/build/outputs/apk/debug/app-debug.apk`。此命令使用当前工作区源码；新增 JNI 后不能使用旧 native 库替代。

## 操作

1. 进入「初始化与登录」，输入 AppKey、用户名、用户 Token。可选配置默认为 SDK 默认值，初始化后修改需重启进程。Token 不写入 demo 日志或持久化。
2. 登录后进入「多 bodies 消息」，填写目标及会话类型。群聊/聊天室要求已加入对应目标。
3. 逐段添加文本、通过系统文件选择器多选文件/图片，或粘贴设备可读路径。外部文件流式复制到 App 独立导入目录，每项均可复制路径、删除、上下调整。
4. 点击发送，一份列表对应一条消息。页面不限制为固定两个 body，也不自行拆分或截断。
5. 另一端使用相同 AppKey、不同账号登录。应用级 listener 记录完整消息，停留在登录页也能接收；进入测试页可选中消息进行整条或指定原始 bodyIndex 的资源下载。
6. 查看逐 body 进度、逐项下载终态和整次请求结果；保存/按 ID 读取，并重启后登录同一账号再次读取以核对持久化。

日志同步到界面和 Logcat 的 `SDKDemo` tag；固定高度区域内滚动。复制路径得到的是 Android 设备路径，不是 Mac 路径。子页面通过顶部导航栏返回上一页，不退出登录，也不注销应用级消息监听。

## AndroidX / support 切换的影响

本 demo 使用 `android.useAndroidX=true`，依赖当前 SDK Gradle 声明的 AndroidX 库。

SDK 的 `replace_with_androidx.sh` / `replace_with_support.sh` 调用 `scripts/replace_androidx.py`，只改 SDK `hyphenatechatsdk/src` 下 Java 的 import，不修改本 demo，也不调整 SDK 的 Gradle 依赖。切换到 support 会间接影响本 demo 的源码编译；`android.useAndroidX=true` 或 Jetifier 不能自动替换本地 Java import。

构建入口执行只读 `checkSdkAndroidX`。发现 `android.support.*` import 会明确报错，避免继续编译出大量缺类错误；不会自动运行切换脚本改写 SDK 工作区。需要恢复 AndroidX 时，在 `emclient-android` 目录执行并 review 差异：

```sh
python3 scripts/replace_androidx.py -s "$PWD" -x
```

无需运行旧 `switch_to_local_gradle.sh`；本 demo 已自行声明本地模块依赖。

## 验证状态

2026-10-19：当前 Android SDK Java + 新增 Core/JNI 桥接 + APK 构建通过，包含 arm64-v8a/x86_64。16:02 使用 `externalNativeBuild` 重新编译 Core/JNI 并打包成功，每个 ABI 仅一份 `libhyphenate.so`；APK 与本次源码产物的 Build ID、与剥离后产物的 SHA-256 一致。AndroidX 只读检查通过；顶部导航栏调整后再次构建通过。

用户手测已确认：两个 Android 模拟器分别登录两个用户，文本和文件多 body 消息发送、接收均正常。此结果来自用户反馈，未单独采集或核验本次设备日志。

尚未明确确认的场景：

- 单文本、单文件回归；双分片文件（是否分片以 SDK 日志为准）。
- 文本+文件及交换顺序、图片混合、同名文件；核对接收顺序、内容和独立路径。
- 整条/指定 body 下载，检查原始下标、逐项结果及唯一请求终态；第二附件失败后继续、重试。
- 保存并重启 App，以同一账号按 ID 读取完整列表，再检查文件内容与状态。
- Android↔iOS；保持登录页时仍可通过常驻 listener 收到消息。

编译和静态检查不替代上述剩余运行验证。当前测试页不请求系统通知权限，不验证后台通知。用户已授权按仓库本地提交当前里程碑，不推送。
