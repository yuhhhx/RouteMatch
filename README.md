# 顺路单助手 (RouteMatch)

面向外卖骑手的顺路单辅助工具。

监听美团/饿了么的新订单通知，结合 GPS 定位和本地顺路算法，帮助骑手判断订单是否顺路。

## 核心特性

- **通知监听**：通过 NotificationListenerService 监听新订单通知
- **地址解析**：离线分词 + 本地别名库 + 编辑距离模糊匹配（阈值 0.75）
- **顺路算法**：Haversine 距离计算 + 方向夹角判定，阈值可配置
- **语音播报**：Android 原生 TextToSpeech（离线）
- **悬浮窗**：仅显示顺路单数量，点击后需 PIN 验证才显示详情
- **数据加密**：所有地址字段 AES-GCM 加密，密钥存于 AndroidKeyStore
- **隐私安全**：纯本地运行，不上传任何数据，无需存储权限

## 安全合规

- 使用 NotificationListenerService（非 AccessibilityService）
- 无截屏/录屏/无障碍功能
- 数据库敏感字段 AES-GCM 加密
- 无存储权限要求
- 无网络上传功能
- 悬浮窗默认隐藏地址详情
- 单次定位，不持续跟踪
- 操作间隔随机抖动（±20%）
- 签名校验 + 环境检测（Xposed/Frida/模拟器检测）

---

## 📦 通过 GitHub Actions 自动编译 APK

### 步骤 1：上传代码到 GitHub

1. 在 GitHub 上创建新仓库
2. 将本项目的所有文件 push 到 `main` 分支

```bash
git init
git add .
git commit -m "Initial commit"
git remote add origin https://github.com/你的用户名/顺路单助手.git
git push -u origin main
```

### 步骤 2：触发自动编译

Push 到 main 分支后，GitHub Actions 会自动开始构建：

1. 打开你的 GitHub 仓库页面
2. 点击顶部 **Actions** 标签页
3. 你会看到名为 "Build APK" 的工作流正在运行
4. 等待工作流完成（约 5-10 分钟）

### 步骤 3：下载 APK

1. 构建完成后，点击 Actions 中对应的工作流条目
2. 在 "Artifacts" 部分找到 `app-debug`
3. 点击下载 ZIP 文件
4. 解压后得到 `app-debug.apk`

> **注意**：APK 保留 30 天，过期前请及时下载。

### 手动触发构建

1. 进入 GitHub 仓库 → Actions → **Build APK**
2. 点击 **Run workflow** → 选择 **main** 分支 → **Run**
3. 等待构建完成后下载

---

## 📱 手动开启权限

安装后首次打开应用会引导您开启各项权限。如果跳过或拒绝，可以手动开启：

### 通知读取权限

- **设置 → 通知和状态栏 → 通知访问权限**（各厂商路径略有不同）
- 找到"顺路单助手"并开启开关

### 悬浮窗权限

- **设置 → 应用管理 → 顺路单助手 → 其他权限 → 显示悬浮窗**
- 或：**设置 → 应用管理 → 顺路单助手 → 显示悬浮窗**

### 定位权限

- **设置 → 应用管理 → 顺路单助手 → 权限 → 位置信息**
- 选择"仅使用期间允许"

### 忽略电池优化

- **设置 → 应用管理 → 顺路单助手 → 电池 → 忽略优化**
- 或：**设置 → 电池 → 更多设置 → 忽略电池优化**

---

## 🔧 本地编译（备用方案）

如果你有 Android Studio：

### 前置条件

- Android Studio Hedgehog (2023.1.1) 或更新版本
- JDK 17

### 编译步骤

1. **克隆项目**
   ```bash
   git clone https://github.com/你的用户名/顺路单助手.git
   ```

2. **用 Android Studio 打开**
   - File → Open → 选择项目根目录
   - 等待 Gradle 同步完成

3. **直接编译**
   - Build → Build Bundle(s) / APK(s) → Build APK(s)
   - 或点击工具栏上的锤子图标

4. **获取 APK**
   - 编译完成后 APK 位于：`app/build/outputs/apk/debug/app-debug.apk`

### 无 Android Studio 编译（命令行）

```bash
# 确保已安装 JDK 17 和 Android SDK
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleDebug
# APK 位于 app/build/outputs/apk/debug/app-debug.apk
```

---

## 开源声明

本工具仅供学习研究使用。不包含任何自动抢单、模拟点击或违反平台规定的功能。

所有数据本地存储，不上传任何用户信息。
