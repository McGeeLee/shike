import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { after, before, describe, it } from "node:test";

import {
  app,
  normalizeResult,
  parseJsonLoose,
  validateAnalyzeInput,
  validateCustomBaseUrl,
} from "../server.js";

describe("browser bundle", () => {
  it("contains syntactically valid inline JavaScript", () => {
    const html = readFileSync(new URL("../public/index.html", import.meta.url), "utf8");
    const script = html.match(/<script>([\s\S]*?)<\/script>/)?.[1];

    assert.ok(script, "public/index.html should contain an inline script");
    assert.doesNotThrow(() => new Function(script));
  });
});

describe("model response parsing", () => {
  it("extracts JSON from a fenced model response", () => {
    assert.deepEqual(parseJsonLoose('```json\n{"is_food":true}\n```'), { is_food: true });
  });

  it("rejects non-text model responses", () => {
    assert.throws(() => parseJsonLoose(null), /有效文本/);
  });

  it("normalizes malformed and negative nutrition values", () => {
    assert.deepEqual(
      normalizeResult({
        is_food: true,
        foods: [{ name: "饭", calories: -3, protein_g: "5.5", carbs_g: null, fat_g: "bad" }],
        total_calories: -10,
        confidence: "unknown",
      }),
      {
        is_food: true,
        foods: [{ name: "饭", portion: "", calories: 0, protein_g: 5.5, carbs_g: 0, fat_g: 0 }],
        total_calories: 0,
        confidence: "medium",
        notes: "",
      },
    );
  });
});

describe("request validation", () => {
  it("accepts a small JPEG payload", () => {
    assert.deepEqual(
      validateAnalyzeInput({ image: "aGVsbG8=", description: "  一小碗  " }),
      {
        image: "aGVsbG8=",
        mediaType: "image/jpeg",
        provider: "claude",
        model: "",
        apiKey: "",
        baseUrl: "",
        description: "一小碗",
      },
    );
  });

  it("rejects invalid base64 and media types", () => {
    assert.throws(() => validateAnalyzeInput({ image: "not base64" }), /图片数据格式错误/);
    assert.throws(
      () => validateAnalyzeInput({ image: "aGVsbG8=", mediaType: "text/html" }),
      /不支持的图片格式/,
    );
  });

  it("accepts only clean HTTP(S) custom endpoints", () => {
    assert.equal(validateCustomBaseUrl("https://api.example.com/v1/"), "https://api.example.com/v1");
    assert.throws(() => validateCustomBaseUrl("file:///tmp/api"), /HTTP 或 HTTPS/);
    assert.throws(() => validateCustomBaseUrl("https://user:pass@example.com/v1"), /用户名或密码/);
    assert.throws(() => validateCustomBaseUrl("https://api.example.com/v1?token=x"), /查询参数或锚点/);
  });
});

describe("HTTP API", () => {
  let server;
  let baseUrl;

  before(async () => {
    server = await new Promise((resolve) => {
      const instance = app.listen(0, "127.0.0.1", () => resolve(instance));
    });
    const address = server.address();
    baseUrl = `http://127.0.0.1:${address.port}`;
  });

  after(async () => {
    await new Promise((resolve, reject) => server.close((error) => (error ? reject(error) : resolve())));
  });

  it("returns provider metadata without exposing keys", async () => {
    const response = await fetch(`${baseUrl}/api/config`);
    const body = await response.json();
    assert.equal(response.status, 200);
    assert.ok(body.providers.length >= 1);
    assert.equal(JSON.stringify(body).includes("API_KEY"), false);
  });

  it("returns JSON validation errors before contacting a provider", async () => {
    const response = await fetch(`${baseUrl}/api/analyze`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ image: "bad payload" }),
    });
    assert.equal(response.status, 400);
    assert.deepEqual(await response.json(), { error: "图片数据格式错误" });
  });
});
