# FCMFix ColorOS 12 📱

[![License: GPL-3.0](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%2012%20%7C%20ColorOS%2012.1-brightgreen.svg)](#系统兼容性与环境要求)
[![Framework](https://img.shields.io/badge/LSPosed-API%20100%2B-orange.svg)](#系统兼容性与环境要求)
[![Version](https://img.shields.io/badge/Version-13.2.3%20(86)-purple.svg)](https://github.com/yqdszhdap1/fcmfix-coloros12/releases)
[![Build Status](https://img.shields.io/github/actions/workflow/status/yqdszhdap1/fcmfix-coloros12/android.yml?branch=master&label=Build)](https://github.com/yqdszhdap1/fcmfix-coloros12/actions)

修复 **ColorOS 12 / 12.1（Android 12，国行）** 拦截 Google FCM 推送与后台唤醒的 LSPosed 模块，需要 **Root + LSPosed**。

应用划掉后台或被系统清理后收不到推送、要等亮屏或手动点开才收到？根本原因在于 ColorOS 底层的 **Hans 墓碑机制**、**Athena 电池调度** 和 **广播代理（Proxy Broadcast）** 拦截了 Google Play 服务（GMS）向目标应用派发的唤醒广播。

本项目基于上游 [kooritea/fcmfix](https://github.com/kooritea/fcmfix) 与 [ByteJuno/fcmfix-coloros](https://github.com/ByteJuno/fcmfix-coloros)，针对 **ColorOS 12 / 12.1 固件底层架构（以 OnePlus 7 Pro GM1910 为实机验证基准）** 进行了源码级专配与优化，移除了高版本专属无效类，带来零报警、完全放行的纯净推送体验。

---

## 📋 模块特性与专配优化

| 维度 | 上游/高版本版（ColorOS 16/17） | 本专配版（ColorOS 12 / 12.1） |
| :--- | :--- | :--- |
| **广播代理放行** | 调用高版本 `shouldProxy`，ColorOS 12.1 报错失配 | **精准适配 `shouldProxyLocked`**，支持 5 参数实参解析与精准放行 |
| **Hans 墓碑解冻** | 调用高版本 `unfreezeIfNeed`，低版本无此方法 | **优先反射 `OplusHansManager.getInstance().unFreezeForwl` 与 `unproxyWakeLock`**，提供真实 20 秒安全投递窗口 |
| **Hook 兼容性** | 包含 12+ 项高版本专属系统类，在 12.1 上报大量感叹号 | **剔除不适用的高版本 Hook 类**，彻底消除系统框架与电池管理失配报错 |
| **GMS 自检报警** | 将 GMS 核心当作普通应用核验，提示通知异常 | **豁免 GMS 虚假通知告警**，自检状态显示清爽的“一切正常” |
| **界面交互** | 应用栏带烦人的红色数字与红点角标 | **彻底移除底部导航栏应用 Tab 角标**，保留纯净图标体验 |
| **应用分身支持** | 机制模糊 | **全局 `system_server` 广播放行**，主空间勾选即可覆盖应用分身（User 999） |

---

## 🛠️ 系统兼容性与环境要求

- **操作系统**：ColorOS 12 / 12.1（Android 12 / API 31）国行版本
- **真机实测标杆**：一加 7 Pro（OnePlus 7 Pro / GM1910，ColorOS 12.1 H.40 固件）
- **Root 方案**：KernelSU / KernelSU-Next / APatch / Magisk
- **Xposed 框架**：LSPosed（Zygisk 版或原生版，API 100+）
- **核心组件**：正常连接 FCM 的 Google Play 服务（GMS）

---

## 🚀 安装与配置教程

### 1. 安装模块
从 [Releases](https://github.com/yqdszhdap1/fcmfix-coloros12/releases) 下载最新版本的 APK 文件（或通过 GitHub Actions Artifact 获取最新构建版本）并安装。

### 2. 配置 LSPosed 作用域
打开 **LSPosed 管理器** -> 模块 -> 进入 **FCMFix ColorOS**：
1. 打开模块开关；
2. **作用域勾选**：
   - ✅ **系统框架**（`android`）
   - ✅ **电池**（`com.oplus.battery`）
3. ⚠️ **注意**：切勿将微信、Telegram 等第三方目标应用勾入 LSPosed 作用域，模块对应用本身无注入需求。

### 3. 重启手机
由于修改了 `system_server` 核心进程与电池服务 Hook 逻辑，**必须完整重启一次手机**，使 Hook 注入生效。

### 4. 勾选放行应用
1. 重启后打开 **FCMFix** 应用；
2. 首页状态应显示：**“一切正常”**（系统框架 21 项生效，电池 3 项生效）；
3. 切换至 **【应用】** 页面，在“包含 FCM”列表中勾选需要后台及时收消息的应用（如 Telegram、Gmail、微信等）；
4. 确认目标应用在系统设置中的**“允许通知”**权限已正常开启。

> 💡 **关于微信与应用分身（双开）**：
> - 微信自带 GMS FCM 注册通道，划掉后台后可借由 FCM 唤醒；
> - 主空间勾选后，广播放行在系统框架层对包名生效，分身空间（User 999）同样自动享有唤醒与解冻放行，分身空间无需额外安装或配置。

---

## 🔍 它能解决什么 / 不能解决什么

### ✅ 能解决
- 应用被划掉后台、系统清理杀掉、或处于 `stopped=true` 状态时，FCM 推送仍能强行唤醒并弹出通知；
- 推送唤醒后，自动为应用解除 Hans 墓碑冻结，给予 20 秒完整联网收信窗口；
- 阻止 ColorOS 电池服务在熄屏或 Doze 阶段切断 GMS 联网连接；
- 消除系统框架 Hook 失配告警与虚假通知红点。

### ❌ 不能解决
- **本机 GMS 连不上 Google 服务器**：国内网络环境下 `mtalk.google.com` 5228 端口被阻断需自行配置分流规则；
- 目标应用本身未接入 FCM、或服务器端未发送 Push；
- 用户在系统设置中彻底禁用了目标应用的通知权限。

---

## 📦 项目结构

```text
fcmfix-coloros12/
├── app/                  # 📱 核心 Android 模块工程 (Kotlin / Java)
│   ├── src/main/java/    # Hook 逻辑、ColorOS 12.1 专配分发与 Compose UI
│   └── build.gradle      # 模块构建与签名配置
├── docs/                 # 📖 技术文档与分析报告
│   └── releases/         # 版本发布说明 (13.2.x)
├── scripts/              # 🛠️ 运维与诊断工具
└── .github/workflows/    # ⚙️ GitHub Actions 自动化构建流水线
```

---

## ⚖️ 开源许可与致谢

本项目遵循 **[GNU General Public License v3.0](LICENSE)** (GPL-3.0) 开源协议。

### 致谢
- [kooritea/fcmfix](https://github.com/kooritea/fcmfix)：原始 FCMFix 架构与 Hook 体系设计者；
- [ByteJuno/fcmfix-coloros](https://github.com/ByteJuno/fcmfix-coloros)：ColorOS 专版原工程与 Material 3 界面基础；
- [LSPosed](https://github.com/LSPosed/LSPosed)：现代 Android Xposed 框架运行基石。
