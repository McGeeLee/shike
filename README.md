<div align="center">

# 食刻 · Shike

**拍一张，记一餐。用视觉模型把食物照片变成可追踪的热量与营养记录。**

![Node.js](https://img.shields.io/badge/Node.js-22%2B-339933?logo=nodedotjs&logoColor=white)
![Express](https://img.shields.io/badge/Express-4.x-111111?logo=express)
![Capacitor](https://img.shields.io/badge/Capacitor-Android-119EFF?logo=capacitor&logoColor=white)
![Tests](https://img.shields.io/badge/tests-node:test-78B88A)

<img src="docs/shike-home.png" width="380" alt="食刻首页：每日热量、三大营养素与饮食记录">

</div>

食刻是一个为日常使用而做的食物记录工具。上传或拍摄食物照片，应用会识别食物、估算份量、热量和三大营养素，并把结果留在当天的记录中。

> [!IMPORTANT]
> 这是辅助记录工具，不是医疗或专业营养建议。识别结果会受到拍摄角度、遮挡和模型能力影响。

## 为什么做它

手动搜索食物、称重、录入数字很容易让记录习惯半途而废。食刻把主要流程压缩成一次拍照，同时保留目标设置、每日汇总和历史补记能力。

## 主要能力

| 能力 | 说明 |
| --- | --- |
| 图片识别 | 拍照或上传图片，识别食物与大致份量 |
| 营养估算 | 返回热量、蛋白质、碳水和脂肪 |
| 每日追踪 | 展示目标进度、剩余额度与营养素汇总 |
| 多模型支持 | Claude、OpenAI、通义千问、智谱 GLM、DeepSeek 及自定义 OpenAI 兼容服务 |
| 本地优先 | 饮食记录保存在浏览器 `localStorage`，不建立远端用户数据库 |
| Web + Android | Express Web 应用与 Capacitor Android 外壳共用界面 |

## 工作流程

```text
拍照 / 上传
    ↓
浏览器压缩图片（长边 1280px）
    ↓
POST /api/analyze 或 Android 原生直连
    ↓
按设置选择视觉模型
    ↓
校验并归一化结构化结果
    ↓
写入当天的本地记录
```

Claude 路径使用 JSON Schema 结构化输出；其他 OpenAI 兼容模型通过提示词约束、容错解析与结果归一化，最终向前端返回一致的数据结构。

## 快速开始

需要 Node.js 22 或更新版本。

```bash
git clone https://github.com/McGeeLee/shike.git
cd shike
npm ci
cp .env.example .env
npm start
```

打开 <http://localhost:3000>。API Key 有两种配置方式：

1. 在页面右上角的设置中选择服务商并填写 Key；
2. 在服务端 `.env` 中提供默认 Key。

页面设置中的 Key 会优先于服务端默认值。不要把真实密钥提交到 Git。

## Android

```bash
npx cap sync android
```

随后用 Android Studio 打开 `android/`，选择设备并运行。Android 应用没有内置 Node 服务，会从设备直接请求所选模型服务商；Key 仅保存在该设备的 WebView 本地存储中。

## 项目结构

```text
shike/
├── public/index.html       # Web 界面、本地记录与 Android 直连逻辑
├── server.js               # Express API、输入校验与多模型适配
├── test/server.test.js     # 解析、校验与 HTTP API 测试
├── android/                # Capacitor Android 工程
├── capacitor.config.json   # 移动端配置
├── AGENT_SPEC.md           # 模型输入输出与职责边界
└── .env.example            # 环境变量示例
```

## 验证

```bash
npm run check
```

检查包括 JavaScript 语法、模型结果归一化、Base URL 与图片请求校验、配置接口和错误响应。GitHub Actions 会在推送和 Pull Request 上运行同一组检查。

## 安全与隐私

- 饮食记录按日期保存在当前浏览器中；清除站点数据会同时清除记录。
- 食物照片只会发送到你选择的模型服务商进行分析。
- 页面会转义用户备注与模型返回文本，避免内容被当作 HTML 执行。
- 服务端限制图片格式、大小、备注长度和自定义 Base URL，并为上游请求设置超时。
- API Key 不应写进前端源码、截图、日志或 Git 提交。

## 后续方向

- 用可维护的营养数据表替代部分模型数值估算；
- 增加固定图片回归集，比较不同模型的稳定性；
- 提供可选的数据导出、备份与记录编辑能力。
