import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { describe, it } from "node:test";

import {
  PROVIDERS,
  analyzeFood,
  effectiveModel,
  normalizeBaseUrl,
  normalizeResult,
  parseJsonLoose,
} from "../public/analyze.js";

const SMALL_JPEG = "aGVsbG8=";
const MODEL_RESULT = {
  is_food: true,
  foods: [{ name: "米饭", portion: "一小碗", calories: 180, protein_g: 4, carbs_g: 40, fat_g: 0.5 }],
  total_calories: 180,
  confidence: "high",
  notes: "按常见小碗估算",
};

describe("Android app bundle", () => {
  it("loads split JavaScript and contains no server API branch", () => {
    const html = readFileSync(new URL("../public/index.html", import.meta.url), "utf8");
    const app = readFileSync(new URL("../public/app.js", import.meta.url), "utf8");
    const analyzer = readFileSync(new URL("../public/analyze.js", import.meta.url), "utf8");
    assert.match(html, /type="module" src="\.\/app\.js"/);
    assert.doesNotMatch(`${html}${app}${analyzer}`, /\/api\/analyze|\/api\/config|IS_APP|LOCAL_PROVIDERS/);
  });

  it("keeps only image-capable providers and a custom endpoint", () => {
    assert.deepEqual(PROVIDERS.map((provider) => provider.id), ["claude", "openai", "qwen", "zhipu", "custom"]);
  });
});

describe("model response normalization", () => {
  it("extracts JSON from a fenced model response", () => {
    assert.deepEqual(parseJsonLoose('```json\n{"is_food":true}\n```'), { is_food: true });
  });

  it("returns safe nutrition values and derives a missing total", () => {
    assert.deepEqual(normalizeResult({
      is_food: true,
      foods: [{ name: "饭", calories: 120, protein_g: "5.5", carbs_g: null, fat_g: -1 }],
      total_calories: 0,
      confidence: "unknown",
    }), {
      is_food: true,
      foods: [{ name: "饭", portion: "", calories: 120, protein_g: 5.5, carbs_g: 0, fat_g: 0 }],
      total_calories: 120,
      confidence: "medium",
      notes: "",
    });
  });
});

describe("provider configuration", () => {
  it("accepts only clean HTTPS custom endpoints", () => {
    assert.equal(normalizeBaseUrl("https://api.example.com/v1/"), "https://api.example.com/v1");
    assert.throws(() => normalizeBaseUrl("http://api.example.com/v1"), /HTTPS/);
    assert.throws(() => normalizeBaseUrl("https://user:pass@example.com/v1"), /账号/);
    assert.throws(() => normalizeBaseUrl("https://api.example.com/v1?token=x"), /查询参数/);
  });

  it("falls back to the provider default model", () => {
    assert.equal(effectiveModel({ provider: "openai", model: "missing" }), "gpt-5.1");
    assert.equal(effectiveModel({ provider: "custom", customModel: " vision-model " }), "vision-model");
  });
});

describe("direct model calls", () => {
  it("builds an OpenAI-compatible vision request", async () => {
    let request;
    const result = await analyzeFood({
      settings: { provider: "openai", model: "gpt-4o" },
      apiKey: "test-key",
      imageBase64: SMALL_JPEG,
      note: "一小碗",
    }, {
      fetchImpl: async (url, options) => {
        request = { url, options };
        return { ok: true, status: 200, json: async () => ({ choices: [{ message: { content: JSON.stringify(MODEL_RESULT) } }] }) };
      },
    });

    assert.equal(request.url, "https://api.openai.com/v1/chat/completions");
    assert.equal(request.options.headers.Authorization, "Bearer test-key");
    const body = JSON.parse(request.options.body);
    assert.equal(body.model, "gpt-4o");
    assert.match(body.messages[1].content[0].image_url.url, /^data:image\/jpeg;base64,/);
    assert.equal(result.total_calories, 180);
  });

  it("builds a Claude structured-output request", async () => {
    let request;
    const result = await analyzeFood({
      settings: { provider: "claude", model: "claude-sonnet-4-6" },
      apiKey: "test-key",
      imageBase64: SMALL_JPEG,
    }, {
      fetchImpl: async (url, options) => {
        request = { url, options };
        return { ok: true, status: 200, json: async () => ({ content: [{ type: "text", text: JSON.stringify(MODEL_RESULT) }] }) };
      },
    });

    assert.equal(request.url, "https://api.anthropic.com/v1/messages");
    assert.equal(request.options.headers["x-api-key"], "test-key");
    assert.equal(JSON.parse(request.options.body).output_config.format.type, "json_schema");
    assert.equal(result.foods[0].name, "米饭");
  });

  it("rejects incomplete settings before sending a request", async () => {
    await assert.rejects(
      analyzeFood({ settings: { provider: "openai" }, apiKey: "", imageBase64: SMALL_JPEG }),
      /API Key/,
    );
    await assert.rejects(
      analyzeFood({ settings: { provider: "custom", customModel: "vision" }, apiKey: "key", imageBase64: SMALL_JPEG }),
      /有效 URL/,
    );
  });

  it("times out even when the native request does not honor AbortSignal", async () => {
    await assert.rejects(
      analyzeFood({
        settings: { provider: "openai", model: "gpt-4o" },
        apiKey: "test-key",
        imageBase64: SMALL_JPEG,
      }, {
        fetchImpl: () => new Promise(() => {}),
        timeoutMs: 5,
      }),
      /响应超时/,
    );
  });
});
