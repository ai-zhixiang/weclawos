# 微侠桌面 OS (WeClawOS)

家用 AI 影音终端 — Alpine + Cage + Chromium 全屏 Kiosk

## 项目结构

```
weclawos/
├── docs/          # 文档
│   ├── 产品设计_v2.0_20260713.md
│   └── weclaw-desktop-demo.html
├── scripts/       # 构建脚本
│   ├── build-weclaw-v2.sh    # Alpine + Cage + Chromium kiosk
│   └── build-alpine-os.sh    # Alpine 基础映像
├── mobile/        # 手机端（WeClaw Android）
│   └── (参考 /home/ubuntu/weclaw/android)
└── README.md
```

## 产品定位

| 版本 | 底座 | 交互 | 状态 |
|------|------|------|------|
| 桌面版 | Alpine + Cage + Chromium kiosk | 语音 + 遥控器 | 脚本就绪，未发布 |
| TV版 | Android TV WebView | 语音 + 遥控器 | 待开发 |
| 手机版 | WeClaw Android APK | 语音 + 触屏 | 开发中 |

## 构建

```bash
cd scripts
bash build-weclaw-v2.sh
# 输出: weclaw-desktop-v2.iso
```
