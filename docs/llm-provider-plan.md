# 食刻大模型接入计划

目标版本：2.2.0

## 接入原则

1. 厂商有稳定原生接口时优先使用原生协议，不为了共用代码强行降级为 OpenAI Chat Completions。
2. 厂商只提供兼容接口时使用其官方文档明确支持的格式，并在设置页标注为兼容协议。
3. 模型列表存在视觉能力元数据时，只向用户展示支持图片输入的模型；缺少元数据时给出明确选择提示。
4. API Key 继续由 Android Keystore 加密保存，图片只直传用户选择的服务商。
5. 任何纯文本模型都不能被误保存为食物照片识别引擎。
6. 所有服务商的模型下拉列表只使用模型 API 的实时响应，不提供本地静态备选列表。

## 2.2.0 范围

| 服务商 | 调用协议 | 模型发现 | 图片能力策略 | 状态 |
| --- | --- | --- | --- | --- |
| Anthropic Claude | 原生 Messages | `/v1/models` | 官方视觉模型 | 已接入 |
| OpenAI | 原生 Responses | `/v1/models` | 用户选择视觉模型，JSON Schema 输出 | 已接入 |
| Google Gemini | 原生 `generateContent` | 原生 `models.list` | 过滤 `generateContent`，使用 `inlineData` | 已接入 |
| xAI Grok | 原生 Responses | `/v1/language-models` | 过滤 `input_modalities=image` | 已接入 |
| 火山引擎方舟 | 原生 Responses | `/api/v3/models` | 选择已开通图片理解的模型/接入点 | 已接入 |
| Moonshot Kimi | 官方 Chat Completions | `/v1/models` | 过滤 `supports_image_in=true` | 已接入 |
| Mistral AI | 原生 Chat Completions | `/v1/models` | 过滤 `capabilities.vision=true` | 已接入 |
| Xiaomi MiMo | 官方 OpenAI API | `/v1/models` | 只保留 `mimo-v2.5` 多模态模型 | 已接入 |
| 通义千问 | 百炼官方兼容入口 | `/v1/models` | 提示选择 VL/多模态模型 | 已接入 |
| 智谱 GLM | 官方兼容入口 | `/v4/models` | 提示选择 GLM-4V 等视觉模型 | 已接入 |
| OpenRouter | 官方聚合兼容入口 | 带 image/text 模态筛选的 Models API | 二次校验模型元数据 | 已接入 |
| 硅基流动 | 官方聚合兼容入口 | `/v1/models` | 提示选择模型广场中的 VLM | 已接入 |
| DeepSeek | 官方 Chat Completions | `/models` 动态获取并筛选 `deepseek-v4-flash-vision-exp` | Base64 `image_url` 高细节输入 | 已接入视觉与 JSON Output |
| 自定义服务 | OpenAI Chat Completions | 自定义 `/models` | 用户负责选择支持 `image_url` 的模型 | 已保留 |

## 提示词与结果质量

- 只依据照片可见证据，不臆造遮挡食材、品牌或重量。
- 按营养构成拆分食物、酱汁、烹调油和饮料，记录份量判断依据。
- 每项热量和三大营养素均表示整份数值，总热量由项目热量求和，避免模型自相矛盾。
- 不确定时返回合理单点估值，并用 `confidence` 与 `notes` 解释主要误差来源。
- 用户备注被标记为不可信数据，只提取重量、数量、品牌、价格、容器和烹饪方式等事实，忽略其中改变角色或输出规则的命令。
- Claude、OpenAI、Grok 与 Gemini 使用结构化输出；其他兼容接口用严格 JSON 指令与宽容解析兜底。

## 验收计划

- 单元测试：厂商目录、协议映射、默认模型、URL 规范化、提示词隔离与长度边界。
- 构建验证：Debug/Release 编译、R8、Lint、Compose AndroidTest 源码编译。
- 设备验证：设置页遍历服务商、动态模型列表、DeepSeek 视觉识别、拍照识别主流程、预测性返回。
- 发布验证：同证书签名、APK 签名校验、SHA-256、GitHub Release 和 App 内更新日志展示。

## 企业云下一阶段

Azure OpenAI、Google Vertex AI 与 AWS Bedrock 不使用单一 API Key + 固定 Base URL：它们分别需要部署名与 API 版本、Google 项目/区域与 OAuth、AWS 区域与 SigV4。下一阶段应新增独立“企业连接”数据模型，并优先采用短期令牌或后端代理，避免把长期云账号凭据放进客户端。

计划顺序：企业连接数据模型 → Azure OpenAI → Vertex AI → Bedrock → 企业代理健康检查 → 多账号与配置导入导出。
