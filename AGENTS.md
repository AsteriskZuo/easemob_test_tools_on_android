# Android SDK 测试工具

本项目是独立 Git 仓库，所有构建和 Git 操作在本目录执行。

- Java + Android Views；初始化和登录集中在单独页面，具体能力各有专用测试页面。
- 应用级共享会话注册接收监听；Activity 退出不注销 SDK 的常驻监听。
- 使用兄弟 `emclient-android` / `emclient-linux` 当前源码，新增 JNI 后必须重编 native 库。
- 凭据不进入代码、日志或 demo 持久化；大文件流式导入，避免同名覆盖。
- Agent 负责构建与静态检查，真实登录和跨端收发由用户手测；分开记录证据。
