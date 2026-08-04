import "dotenv/config";
import express from "express";
import Anthropic from "@anthropic-ai/sdk";
import path from "path";
import os from "os";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const serverFile = fileURLToPath(import.meta.url);

const app = express();
app.use(express.json({ limit: "16mb" }));
app.use(express.static(path.join(__dirname, "public")));

const ALLOWED_IMAGE_TYPES = new Set(["image/jpeg", "image/png", "image/webp", "image/gif"]);
const MAX_IMAGE_BYTES = 8 * 1024 * 1024;
const MAX_DESCRIPTION_LENGTH = 500;
const PROVIDER_TIMEOUT_MS = 120_000;

// ---------- 服务商配置 ----------
// claude 走 Anthropic 官方 SDK；其余走 OpenAI 兼容的 /chat/completions 接口
const PROVIDERS = {
  claude: {
    name: "Claude (Anthropic)",
    envKey: "ANTHROPIC_API_KEY",
    models: [
      { id: "claude-opus-4-8", label: "Opus 4.8（最准）" },
      { id: "claude-sonnet-4-6", label: "Sonnet 4.6（均衡）" },
      { id: "claude-haiku-4-5", label: "Haiku 4.5（最省）" },
    ],
  },
  openai: {
    name: "OpenAI",
    envKey: "OPENAI_API_KEY",
    baseUrl: "https://api.openai.com/v1",
    models: [
      { id: "gpt-5.1", label: "GPT-5.1" },
      { id: "gpt-4o", label: "GPT-4o" },
      { id: "gpt-4o-mini", label: "GPT-4o mini（最省）" },
    ],
  },
  qwen: {
    name: "通义千问（阿里云）",
    envKey: "DASHSCOPE_API_KEY",
    baseUrl: "https://dashscope.aliyuncs.com/compatible-mode/v1",
    models: [
      { id: "qwen-vl-max", label: "Qwen-VL-Max（最准）" },
      { id: "qwen-vl-plus", label: "Qwen-VL-Plus（更省）" },
    ],
 },
 zhipu: {
   name: "智谱 GLM",
   envKey: "ZHIPU_API_KEY",
   baseUrl: "https://open.bigmodel.cn/api/paas/v4",
   models: [
     { id: "glm-4v-plus", label: "GLM-4V-Plus" },
     { id: "glm-4v", label: "GLM-4V" },
   ],
 },
  deepseek: {
    name: "DeepSeek",
    envKey: "DEEPSEEK_API_KEY",
    baseUrl: "https://api.deepseek.com",
    models: [
      { id: "deepseek-v4-pro", label: "DeepSeek V4 Pro（最准）" },
      { id: "deepseek-v4-flash", label: "DeepSeek V4 Flash（更省）" },
    ],
  },
 custom: {
   name: "自定义（OpenAI 兼容）",
   envKey: "CUSTOM_API_KEY",
   models: [],
 },
};

const SYSTEM_PROMPT =
  "你是一位专业营养师。根据食物照片识别其中的食物种类，估算每种食物的份量、热量（千卡）和三大营养素。" +
  "估算时参考常见餐具尺寸（碗、盘、杯）判断份量。如果照片里没有食物，将 is_food 设为 false 并将 foods 留空。" +
  "如果用户提供了补充说明（如价格、单价、总量、容器大小、品牌），请优先据此推算份量并在 notes 里说明推算过程；" +
  "例如『花14元，单价1.42元/两』可推算重量≈14÷1.42≈9.86两≈493克（1两=50克）。" +
  "宁可给出合理区间的中间值，也不要拒绝估算。所有文字用简体中文。";

// Claude 结构化输出 schema
const FOOD_ANALYSIS_SCHEMA = {
  type: "object",
  properties: {
    is_food: { type: "boolean", description: "照片中是否包含可识别的食物" },
    foods: {
      type: "array",
      items: {
        type: "object",
        properties: {
          name: { type: "string", description: "食物名称（中文）" },
          portion: { type: "string", description: "估计份量，如 '约150克' 或 '1碗'" },
          calories: { type: "integer", description: "估算热量（千卡）" },
          protein_g: { type: "number", description: "蛋白质（克）" },
          carbs_g: { type: "number", description: "碳水化合物（克）" },
          fat_g: { type: "number", description: "脂肪（克）" },
        },
        required: ["name", "portion", "calories", "protein_g", "carbs_g", "fat_g"],
        additionalProperties: false,
      },
    },
    total_calories: { type: "integer", description: "总热量估算（千卡）" },
    confidence: { type: "string", enum: ["low", "medium", "high"], description: "估算置信度" },
    notes: { type: "string", description: "简短说明，如份量不确定的原因" },
  },
  required: ["is_food", "foods", "total_calories", "confidence", "notes"],
  additionalProperties: false,
};

// OpenAI 兼容服务商没有统一的 schema 约束，用提示词 + 宽松解析
const JSON_SHAPE_HINT = JSON.stringify({
  is_food: true,
  foods: [{ name: "食物名", portion: "约150克", calories: 200, protein_g: 10, carbs_g: 20, fat_g: 8 }],
  total_calories: 200,
  confidence: "medium",
  notes: "说明",
});

// ---------- 各服务商调用 ----------
async function analyzeWithClaude(apiKey, model, image, mediaType, description) {
  const client = new Anthropic({ apiKey });
  const userText = "请识别这张照片中的食物并估算热量。" + (description ? "\n用户补充说明：" + description : "");
  const response = await client.messages.create({
    model,
    max_tokens: 2048,
    thinking: { type: "adaptive" },
    output_config: {
      format: { type: "json_schema", schema: FOOD_ANALYSIS_SCHEMA },
    },
    system: SYSTEM_PROMPT,
    messages: [
      {
        role: "user",
        content: [
          { type: "image", source: { type: "base64", media_type: mediaType, data: image } },
          { type: "text", text: userText },
        ],
      },
    ],
  });
  const textBlock = response.content.find((b) => b.type === "text");
  if (!textBlock) throw new Error("模型未返回结果，请重试");
  return JSON.parse(textBlock.text);
}

async function analyzeWithOpenAICompat(baseUrl, apiKey, model, image, mediaType, description) {
  const userText =
    "请识别这张照片中的食物并估算热量。" +
    (description ? "\n用户补充说明：" + description : "") +
    "\n只输出 JSON。";
  const resp = await fetch(`${baseUrl.replace(/\/$/, "")}/chat/completions`, {
    method: "POST",
    signal: AbortSignal.timeout(PROVIDER_TIMEOUT_MS),
    headers: {
      Authorization: `Bearer ${apiKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      model,
      messages: [
        {
          role: "system",
          content:
            SYSTEM_PROMPT +
            "\n你必须只输出一个 JSON 对象，不要输出任何其他文字、解释或 markdown 代码块。JSON 结构示例：" +
            JSON_SHAPE_HINT,
        },
        {
          role: "user",
          content: [
            { type: "image_url", image_url: { url: `data:${mediaType};base64,${image}` } },
            { type: "text", text: userText },
          ],
        },
      ],
    }),
  });

  if (!resp.ok) {
    const body = await resp.text().catch(() => "");
    if (resp.status === 401 || resp.status === 403) throw new Error("API key 无效或无权限");
    if (resp.status === 404) throw new Error("模型不存在或接口地址错误");
    if (resp.status === 429) throw new Error("请求过于频繁，请稍后再试");
    console.error(`Provider error ${resp.status}:`, body.slice(0, 500));
    throw new Error(`服务商返回错误 (${resp.status})`);
  }

  const data = await resp.json();
  const text = data.choices?.[0]?.message?.content ?? "";
  return parseJsonLoose(text);
}

// 容错解析：剥掉代码块围栏，截取首尾大括号之间的内容
function parseJsonLoose(text) {
  if (typeof text !== "string") throw new Error("模型返回的不是有效文本");
  const cleaned = text.replace(/```(?:json)?/g, "").trim();
  const start = cleaned.indexOf("{");
  const end = cleaned.lastIndexOf("}");
  if (start === -1 || end === -1) throw new Error("模型返回的不是有效 JSON");
  return JSON.parse(cleaned.slice(start, end + 1));
}

// 统一结果形状，避免个别模型漏字段导致前端出错
function normalizeResult(r) {
  const source = r && typeof r === "object" && !Array.isArray(r) ? r : {};
  const num = (v) => {
    const parsed = Number(v);
    return Number.isFinite(parsed) ? Math.max(0, parsed) : 0;
  };
  const foods = Array.isArray(source.foods)
    ? source.foods.map((food) => {
        const f = food && typeof food === "object" ? food : {};
        return {
        name: String(f.name || "未知食物"),
        portion: String(f.portion || ""),
        calories: Math.round(num(f.calories)),
        protein_g: num(f.protein_g),
        carbs_g: num(f.carbs_g),
        fat_g: num(f.fat_g),
        };
      })
    : [];
  return {
    is_food: Boolean(source.is_food) && foods.length > 0,
    foods,
    total_calories: Math.round(num(source.total_calories) || foods.reduce((s, f) => s + f.calories, 0)),
    confidence: ["low", "medium", "high"].includes(source.confidence) ? source.confidence : "medium",
    notes: String(source.notes || ""),
  };
}

function validateCustomBaseUrl(value) {
  let url;
  try {
    url = new URL(value);
  } catch {
    throw new Error("接口地址不是有效 URL");
  }
  if (!["http:", "https:"].includes(url.protocol)) {
    throw new Error("接口地址只支持 HTTP 或 HTTPS");
  }
  if (url.username || url.password) {
    throw new Error("接口地址不能包含用户名或密码");
  }
  if (url.search || url.hash) {
    throw new Error("接口地址不能包含查询参数或锚点");
  }
  return url.toString().replace(/\/$/, "");
}

function validateAnalyzeInput(body) {
  if (!body || typeof body !== "object" || Array.isArray(body)) {
    throw new Error("请求内容格式错误");
  }

  const image = typeof body.image === "string" ? body.image.trim() : "";
  if (!image) throw new Error("缺少图片数据");
  if (image.length % 4 !== 0 || !/^[A-Za-z0-9+/]+={0,2}$/.test(image)) {
    throw new Error("图片数据格式错误");
  }
  if (Buffer.byteLength(image, "base64") > MAX_IMAGE_BYTES) {
    throw new Error("图片过大，请压缩到 8 MB 以内");
  }

  const mediaType = typeof body.mediaType === "string" && body.mediaType ? body.mediaType : "image/jpeg";
  if (!ALLOWED_IMAGE_TYPES.has(mediaType)) throw new Error("不支持的图片格式");

  const description = typeof body.description === "string" ? body.description.trim() : "";
  if (description.length > MAX_DESCRIPTION_LENGTH) {
    throw new Error(`补充说明不能超过 ${MAX_DESCRIPTION_LENGTH} 个字符`);
  }

  const provider = typeof body.provider === "string" ? body.provider.trim() : "claude";
  const model = typeof body.model === "string" ? body.model.trim() : "";
  const apiKey = typeof body.apiKey === "string" ? body.apiKey.trim() : "";
  const baseUrl = typeof body.baseUrl === "string" ? body.baseUrl.trim() : "";
  if (provider.length > 50 || model.length > 200 || apiKey.length > 4096 || baseUrl.length > 2048) {
    throw new Error("请求参数过长");
  }

  return { image, mediaType, provider, model, apiKey, baseUrl, description };
}

// ---------- 路由 ----------
// 前端拉取可用服务商列表（不返回任何 key 内容，只返回是否已在服务器配置）
app.get("/api/config", (req, res) => {
  res.json({
    providers: Object.entries(PROVIDERS).map(([id, p]) => ({
      id,
      name: p.name,
      models: p.models,
      hasEnvKey: Boolean(process.env[p.envKey]),
    })),
  });
});

app.post("/api/analyze", async (req, res) => {
  try {
    let input;
    try {
      input = validateAnalyzeInput(req.body);
    } catch (err) {
      return res.status(400).json({ error: err.message });
    }
    const { image, mediaType, provider, model, apiKey, baseUrl, description: note } = input;

    const meta = PROVIDERS[provider];
    if (!meta) return res.status(400).json({ error: "未知的服务商" });

    const key = (apiKey || "").trim() || process.env[meta.envKey];
    if (!key) return res.status(400).json({ error: `未配置 ${meta.name} 的 API key，请在设置中填写` });

    const usedModel = (model || "").trim() || meta.models[0]?.id;
    if (!usedModel) return res.status(400).json({ error: "请在设置中填写模型名称" });

    let result;
    if (provider === "claude") {
      result = await analyzeWithClaude(key, usedModel, image, mediaType, note);
    } else {
      let url = meta.baseUrl;
      if (provider === "custom") {
        if (!baseUrl) return res.status(400).json({ error: "请在设置中填写接口地址 (Base URL)" });
        try {
          url = validateCustomBaseUrl(baseUrl);
        } catch (err) {
          return res.status(400).json({ error: err.message });
        }
      }
      if (!url) return res.status(400).json({ error: "请在设置中填写接口地址 (Base URL)" });
      result = await analyzeWithOpenAICompat(url, key, usedModel, image, mediaType, note);
    }
    res.json(normalizeResult(result));
  } catch (err) {
    if (err instanceof Anthropic.AuthenticationError) {
      return res.status(401).json({ error: "API key 无效，请在设置中检查" });
    }
    if (err instanceof Anthropic.RateLimitError) {
      return res.status(429).json({ error: "请求过于频繁，请稍后再试" });
    }
    if (err instanceof Anthropic.NotFoundError) {
      return res.status(404).json({ error: "模型不存在，请在设置中检查模型名称" });
    }
    if (err?.name === "TimeoutError" || err?.name === "AbortError") {
      return res.status(504).json({ error: "模型响应超时，请稍后重试" });
    }
    console.error(err);
    res.status(500).json({ error: err instanceof SyntaxError ? "模型返回格式有误，请重试" : err.message || "识别失败，请重试" });
  }
});

app.use((err, req, res, next) => {
  if (err?.type === "entity.too.large") {
    return res.status(413).json({ error: "请求过大，请压缩图片后重试" });
  }
  if (err instanceof SyntaxError && "body" in err) {
    return res.status(400).json({ error: "请求 JSON 格式错误" });
  }
  return next(err);
});

// 提供安卓安装包下载（手机连同一 Wi-Fi，浏览器访问 /app.apk）
app.get("/app.apk", (req, res) => {
  res.download(
    path.join(__dirname, "android/app/build/outputs/apk/debug/app-debug.apk"),
    "shike.apk",
    (err) => { if (err && !res.headersSent) res.status(404).send("APK 尚未编译"); }
  );
});

function startServer(port = process.env.PORT || 3000) {
  return app.listen(port, () => {
    console.log(`食刻已启动: http://localhost:${port}`);
    const ips = Object.values(os.networkInterfaces())
      .flat()
      .filter((i) => i && i.family === "IPv4" && !i.internal)
      .map((i) => i.address);
    if (ips.length) {
      console.log(`手机访问（同一 Wi-Fi）: http://${ips[0]}:${port}`);
      console.log(`APK 下载地址: http://${ips[0]}:${port}/app.apk`);
    }
  });
}

if (process.argv[1] && path.resolve(process.argv[1]) === path.resolve(serverFile)) {
  startServer();
}

export { app, normalizeResult, parseJsonLoose, startServer, validateAnalyzeInput, validateCustomBaseUrl };
