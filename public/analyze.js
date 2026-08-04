export const PROVIDERS = Object.freeze([
  {
    id: "claude",
    name: "Claude (Anthropic)",
    models: [
      { id: "claude-opus-4-8", label: "Opus 4.8（最准）" },
      { id: "claude-sonnet-4-6", label: "Sonnet 4.6（均衡）" },
      { id: "claude-haiku-4-5", label: "Haiku 4.5（最省）" },
    ],
  },
  {
    id: "openai",
    name: "OpenAI",
    baseUrl: "https://api.openai.com/v1",
    models: [
      { id: "gpt-5.1", label: "GPT-5.1" },
      { id: "gpt-4o", label: "GPT-4o" },
      { id: "gpt-4o-mini", label: "GPT-4o mini（最省）" },
    ],
  },
  {
    id: "qwen",
    name: "通义千问（阿里云）",
    baseUrl: "https://dashscope.aliyuncs.com/compatible-mode/v1",
    models: [
      { id: "qwen-vl-max", label: "Qwen-VL-Max（最准）" },
      { id: "qwen-vl-plus", label: "Qwen-VL-Plus（更省）" },
    ],
  },
  {
    id: "zhipu",
    name: "智谱 GLM",
    baseUrl: "https://open.bigmodel.cn/api/paas/v4",
    models: [
      { id: "glm-4v-plus", label: "GLM-4V-Plus" },
      { id: "glm-4v", label: "GLM-4V" },
    ],
  },
  { id: "custom", name: "自定义（OpenAI 兼容）", models: [] },
]);

export const SYSTEM_PROMPT =
  "你是一位专业营养师。根据食物照片识别其中的食物种类，估算每种食物的份量、热量（千卡）和三大营养素。" +
  "估算时参考常见餐具尺寸判断份量。如果照片里没有食物，将 is_food 设为 false 并将 foods 留空。" +
  "如果用户提供了份量、价格、单价、容器大小或品牌等补充说明，请优先据此推算份量并在 notes 里说明。" +
  "宁可给出合理区间的中间值，也不要拒绝估算。所有文字使用简体中文。";

export const FOOD_SCHEMA = {
  type: "object",
  properties: {
    is_food: { type: "boolean" },
    foods: {
      type: "array",
      items: {
        type: "object",
        properties: {
          name: { type: "string" },
          portion: { type: "string" },
          calories: { type: "integer" },
          protein_g: { type: "number" },
          carbs_g: { type: "number" },
          fat_g: { type: "number" },
        },
        required: ["name", "portion", "calories", "protein_g", "carbs_g", "fat_g"],
        additionalProperties: false,
      },
    },
    total_calories: { type: "integer" },
    confidence: { type: "string", enum: ["low", "medium", "high"] },
    notes: { type: "string" },
  },
  required: ["is_food", "foods", "total_calories", "confidence", "notes"],
  additionalProperties: false,
};

const JSON_SHAPE_HINT = JSON.stringify({
  is_food: true,
  foods: [{
    name: "食物名",
    portion: "约150克",
    calories: 200,
    protein_g: 10,
    carbs_g: 20,
    fat_g: 8,
  }],
  total_calories: 200,
  confidence: "medium",
  notes: "估算说明",
});

const BASE64_PATTERN = /^[A-Za-z0-9+/]+={0,2}$/;
const MAX_IMAGE_BYTES = 8 * 1024 * 1024;

export function safeNumber(value) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? Math.max(0, parsed) : 0;
}

export function parseJsonLoose(text) {
  if (typeof text !== "string") throw new Error("模型返回的不是有效文本");
  const cleaned = text.replace(/```(?:json)?/gi, "").trim();
  const start = cleaned.indexOf("{");
  const end = cleaned.lastIndexOf("}");
  if (start < 0 || end < start) throw new Error("模型没有返回有效 JSON");
  try {
    return JSON.parse(cleaned.slice(start, end + 1));
  } catch {
    throw new Error("模型返回的 JSON 格式不正确，请重试");
  }
}

export function normalizeResult(result) {
  const source = result && typeof result === "object" && !Array.isArray(result) ? result : {};
  const foods = Array.isArray(source.foods)
    ? source.foods.map((item) => {
      const food = item && typeof item === "object" ? item : {};
      return {
        name: String(food.name || "未知食物").slice(0, 100),
        portion: String(food.portion || "").slice(0, 100),
        calories: Math.round(safeNumber(food.calories)),
        protein_g: safeNumber(food.protein_g),
        carbs_g: safeNumber(food.carbs_g),
        fat_g: safeNumber(food.fat_g),
      };
    }).slice(0, 30)
    : [];
  const foodCalories = foods.reduce((sum, food) => sum + food.calories, 0);

  return {
    is_food: Boolean(source.is_food) && foods.length > 0,
    foods,
    total_calories: Math.round(safeNumber(source.total_calories) || foodCalories),
    confidence: ["low", "medium", "high"].includes(source.confidence) ? source.confidence : "medium",
    notes: String(source.notes || "").slice(0, 1000),
  };
}

export function normalizeBaseUrl(value) {
  let url;
  try {
    url = new URL(String(value || "").trim());
  } catch {
    throw new Error("接口地址不是有效 URL");
  }
  if (url.protocol !== "https:") throw new Error("接口地址必须使用 HTTPS");
  if (url.username || url.password || url.search || url.hash) {
    throw new Error("接口地址不能包含账号、查询参数或锚点");
  }
  return url.toString().replace(/\/$/, "");
}

export function providerMeta(id) {
  return PROVIDERS.find((provider) => provider.id === id);
}

export function effectiveModel(settings) {
  if (settings?.provider === "custom") return String(settings.customModel || "").trim();
  const provider = providerMeta(settings?.provider);
  if (!provider) return "";
  const requested = String(settings.model || "").trim().slice(0, 200);
  return requested || provider.models[0]?.id || "";
}

function validateImage(base64) {
  if (typeof base64 !== "string" || !BASE64_PATTERN.test(base64)) {
    throw new Error("图片数据无效，请重新选择照片");
  }
  const padding = base64.endsWith("==") ? 2 : base64.endsWith("=") ? 1 : 0;
  const bytes = Math.floor(base64.length * 3 / 4) - padding;
  if (bytes > MAX_IMAGE_BYTES) throw new Error("压缩后的图片不能超过 8 MB");
}

function extractMessageText(content) {
  if (typeof content === "string") return content;
  if (!Array.isArray(content)) return "";
  return content.map((part) => {
    if (typeof part === "string") return part;
    return typeof part?.text === "string" ? part.text : "";
  }).join("");
}

async function providerError(response) {
  let detail = "";
  try {
    const payload = await response.json();
    detail = String(payload?.error?.message || payload?.message || payload?.error || "")
      .replace(/[\u0000-\u001f\u007f]/g, " ")
      .trim()
      .slice(0, 240);
  } catch {
    // Some gateways return HTML or an empty body for errors.
  }

  let message;
  if (response.status === 401 || response.status === 403) message = "API Key 无效或没有模型权限";
  else if (response.status === 404) message = "模型不存在或接口地址错误";
  else if (response.status === 429) message = "请求过于频繁，请稍后再试";
  else if (response.status >= 500) message = "模型服务暂时不可用，请稍后再试";
  else message = `模型服务返回错误（${response.status}）`;
  return new Error(detail ? `${message}：${detail}` : message);
}

async function fetchWithTimeout(fetchImpl, url, options, timeoutMs) {
  const controller = new AbortController();
  let timedOut = false;
  let timer;
  const request = Promise.resolve().then(() => fetchImpl(url, { ...options, signal: controller.signal }));
  const timeout = new Promise((_, reject) => {
    timer = setTimeout(() => {
      timedOut = true;
      controller.abort();
      reject(new Error("模型响应超时，请稍后重试"));
    }, timeoutMs);
  });
  try {
    return await Promise.race([request, timeout]);
  } catch (error) {
    if (timedOut || error?.name === "AbortError") throw new Error("模型响应超时，请稍后重试");
    throw new Error("无法连接模型服务，请检查网络和接口设置");
  } finally {
    clearTimeout(timer);
  }
}

function normalizeModelIds(payload) {
  const source = Array.isArray(payload?.data)
    ? payload.data
    : Array.isArray(payload?.models)
      ? payload.models
      : [];
  const ids = source.map((item) => {
    const value = typeof item === "string" ? item : item?.id || item?.name;
    return String(value || "").replace(/[\u0000-\u001f\u007f]/g, "").trim().slice(0, 200);
  }).filter(Boolean);
  return [...new Set(ids)].sort((left, right) => left.localeCompare(right, "en", { numeric: true })).slice(0, 500);
}

export async function listAvailableModels(input, options = {}) {
  const fetchImpl = options.fetchImpl || globalThis.fetch;
  const timeoutMs = options.timeoutMs || 20_000;
  if (typeof fetchImpl !== "function") throw new Error("当前环境不支持网络请求");

  const settings = input?.settings || {};
  const provider = providerMeta(settings.provider);
  if (!provider) throw new Error("请选择有效的模型服务商");
  const apiKey = String(input?.apiKey || "").trim();
  if (!apiKey) throw new Error("请先填写 API Key");

  const isClaude = provider.id === "claude";
  const baseUrl = isClaude
    ? "https://api.anthropic.com/v1"
    : normalizeBaseUrl(provider.id === "custom" ? settings.customBaseUrl : provider.baseUrl);
  const headers = isClaude
    ? {
      "x-api-key": apiKey,
      "anthropic-version": "2023-06-01",
      "anthropic-dangerous-direct-browser-access": "true",
    }
    : { Authorization: `Bearer ${apiKey}` };

  const response = await fetchWithTimeout(fetchImpl, `${baseUrl}/models`, {
    method: "GET",
    headers,
  }, timeoutMs);
  if (!response.ok) throw await providerError(response);

  let payload;
  try {
    payload = await response.json();
  } catch {
    throw new Error("模型列表返回了无效数据");
  }
  const models = normalizeModelIds(payload);
  if (!models.length) throw new Error("连接成功，但该 API Key 没有返回可用模型");
  return models;
}

export async function analyzeFood(input, options = {}) {
  const fetchImpl = options.fetchImpl || globalThis.fetch;
  const timeoutMs = options.timeoutMs || 120_000;
  if (typeof fetchImpl !== "function") throw new Error("当前环境不支持网络请求");

  const settings = input?.settings || {};
  const provider = providerMeta(settings.provider);
  if (!provider) throw new Error("请选择有效的模型服务商");
  const apiKey = String(input?.apiKey || "").trim();
  if (!apiKey) throw new Error("请先在设置中填写 API Key");
  const model = effectiveModel(settings);
  if (!model) throw new Error("请先在设置中填写模型名称");
  const imageBase64 = input?.imageBase64;
  validateImage(imageBase64);
  const note = String(input?.note || "").trim().slice(0, 500);
  const userText = `请识别这张照片中的食物并估算热量。${note ? `\n用户补充说明：${note}` : ""}`;

  if (provider.id === "claude") {
    const response = await fetchWithTimeout(fetchImpl, "https://api.anthropic.com/v1/messages", {
      method: "POST",
      headers: {
        "x-api-key": apiKey,
        "anthropic-version": "2023-06-01",
        "anthropic-dangerous-direct-browser-access": "true",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        model,
        max_tokens: 2048,
        output_config: { format: { type: "json_schema", schema: FOOD_SCHEMA } },
        system: SYSTEM_PROMPT,
        messages: [{
          role: "user",
          content: [
            { type: "image", source: { type: "base64", media_type: "image/jpeg", data: imageBase64 } },
            { type: "text", text: userText },
          ],
        }],
      }),
    }, timeoutMs);
    if (!response.ok) throw await providerError(response);
    const data = await response.json();
    const text = data.content?.find((block) => block?.type === "text")?.text;
    if (!text) throw new Error("模型没有返回识别结果，请重试");
    return normalizeResult(parseJsonLoose(text));
  }

  const baseUrl = normalizeBaseUrl(provider.id === "custom" ? settings.customBaseUrl : provider.baseUrl);
  const response = await fetchWithTimeout(fetchImpl, `${baseUrl}/chat/completions`, {
    method: "POST",
    headers: { Authorization: `Bearer ${apiKey}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      model,
      messages: [
        {
          role: "system",
          content: `${SYSTEM_PROMPT}\n你必须只输出一个 JSON 对象，不要输出解释或 Markdown。结构示例：${JSON_SHAPE_HINT}`,
        },
        {
          role: "user",
          content: [
            { type: "image_url", image_url: { url: `data:image/jpeg;base64,${imageBase64}` } },
            { type: "text", text: `${userText}\n只输出 JSON。` },
          ],
        },
      ],
    }),
  }, timeoutMs);
  if (!response.ok) throw await providerError(response);
  const data = await response.json();
  const text = extractMessageText(data.choices?.[0]?.message?.content);
  if (!text) throw new Error("模型没有返回识别结果，请重试");
  return normalizeResult(parseJsonLoose(text));
}
