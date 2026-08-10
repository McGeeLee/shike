import {
  PROVIDERS,
  analyzeFood,
  effectiveModel,
  listAvailableModels,
  normalizeBaseUrl,
  normalizeResult,
  providerMeta,
  safeNumber,
} from "./analyze.js";

const $ = (id) => document.getElementById(id);
const SETTINGS_KEY = "eat-settings";
const KEYS_KEY = "eat-keys";
const GOAL_KEY = "eat-goal";
const CONFIDENCE_LABEL = { low: "置信度较低", medium: "置信度中等", high: "置信度较高" };
const state = {
  viewDate: new Date(),
  pendingResult: null,
  pendingThumb: null,
  pendingNote: "",
  lastFocusedElement: null,
  analysisRequest: 0,
  imageRequest: 0,
  sheetHistoryActive: false,
  deletedEntry: null,
  undoTimer: null,
};

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, (character) => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    "\"": "&quot;",
    "'": "&#39;",
  })[character]);
}

function safeImageDataUrl(value) {
  return typeof value === "string" && /^data:image\/jpeg;base64,[A-Za-z0-9+/]+={0,2}$/.test(value) ? value : "";
}

function localDateString(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function logKey(date = state.viewDate) {
  return `eat-log-${localDateString(date)}`;
}

function readJson(key, fallback) {
  try {
    const value = JSON.parse(localStorage.getItem(key));
    return value ?? fallback;
  } catch {
    return fallback;
  }
}

function getSettings() {
  const saved = readJson(SETTINGS_KEY, {});
  const provider = providerMeta(saved.provider) ? saved.provider : PROVIDERS[0].id;
  return {
    provider,
    model: String(saved.model || ""),
    customBaseUrl: String(saved.customBaseUrl || ""),
    customModel: String(saved.customModel || ""),
  };
}

function getKeys() {
  const keys = readJson(KEYS_KEY, {});
  return keys && typeof keys === "object" && !Array.isArray(keys) ? keys : {};
}

function getGoal() {
  const value = Number.parseInt(localStorage.getItem(GOAL_KEY), 10);
  return value > 0 ? Math.min(100_000, value) : 2000;
}

function getLog() {
  const log = readJson(logKey(), []);
  return Array.isArray(log) ? log : [];
}

function saveLog(log) {
  localStorage.setItem(logKey(), JSON.stringify(log));
}

function isToday(date) {
  return localDateString(date) === localDateString(new Date());
}

function renderDateBar() {
  const today = new Date();
  const todaySelected = isToday(state.viewDate);
  const yesterday = localDateString(state.viewDate) === localDateString(new Date(today.getFullYear(), today.getMonth(), today.getDate() - 1));
  const label = state.viewDate.toLocaleDateString("zh-CN", { month: "long", day: "numeric", weekday: "short" });
  $("dateLabel").textContent = todaySelected ? `今天 · ${label}` : yesterday ? `昨天 · ${label}` : label;
  $("dateLabel").dateTime = localDateString(state.viewDate);
  $("nextDay").disabled = todaySelected;
  $("todayBtn").hidden = todaySelected;
  $("logTitle").textContent = todaySelected ? "今日记录" : "当日记录";
  $("captureLabel").textContent = todaySelected ? "拍照记录这一餐" : "补记这一天的一餐";
}

function renderModelLabel() {
  const settings = getSettings();
  const provider = providerMeta(settings.provider);
  const model = effectiveModel(settings);
  $("modelLabel").textContent = provider ? `识别引擎：${provider.name} · ${model || "未设置模型"}` : "";
}

function render() {
  renderDateBar();
  renderModelLabel();
  const log = getLog();
  const goal = getGoal();
  const total = Math.round(log.reduce((sum, entry) => sum + safeNumber(entry.calories), 0));
  const protein = log.reduce((sum, entry) => sum + safeNumber(entry.protein), 0);
  const carbs = log.reduce((sum, entry) => sum + safeNumber(entry.carbs), 0);
  const fat = log.reduce((sum, entry) => sum + safeNumber(entry.fat), 0);

  $("totalKcal").textContent = total;
  $("goalKcal").textContent = goal;
  $("mProtein").textContent = `${protein.toFixed(0)}g`;
  $("mCarbs").textContent = `${carbs.toFixed(0)}g`;
  $("mFat").textContent = `${fat.toFixed(0)}g`;
  const remaining = goal - total;
  $("remainLabel").textContent = remaining >= 0 ? `还可摄入 ${remaining} 千卡` : `已超出 ${-remaining} 千卡`;
  $("barFill").style.width = `${Math.min(100, total / goal * 100)}%`;
  $("barFill").classList.toggle("over", total > goal);
  $("calorieProgress").setAttribute("aria-valuemax", String(goal));
  $("calorieProgress").setAttribute("aria-valuenow", String(total));
  $("calorieProgress").setAttribute("aria-valuetext", `${total} / ${goal} 千卡`);

  const list = $("entryList");
  if (!log.length) {
    list.innerHTML = `<div class="empty" role="listitem">${isToday(state.viewDate) ? "还没有记录，拍张照开始吧" : "这一天没有记录"}</div>`;
    return;
  }
  list.innerHTML = log.map((entry, index) => {
    const thumbnail = safeImageDataUrl(entry.thumb);
    return `<div class="entry" role="listitem">
      ${thumbnail ? `<img src="${thumbnail}" alt="${escapeHtml(entry.name)}的缩略图" loading="lazy" decoding="async">` : ""}
      <div class="detail">
        <b>${escapeHtml(entry.name)}</b>
        <span>${escapeHtml(entry.time)}${entry.note ? ` · ${escapeHtml(entry.note)}` : ""}</span>
      </div>
      <span class="kcal">${Math.round(safeNumber(entry.calories))} 千卡</span>
      <button class="del" type="button" data-index="${index}" aria-label="删除${escapeHtml(entry.name)}">✕</button>
    </div>`;
  }).join("");
}

function resetPending() {
  state.pendingResult = null;
  state.pendingThumb = null;
  state.pendingNote = "";
}

function setModalBackgroundInert(inert) {
  document.querySelectorAll("body > header, body > .datebar, body > main").forEach((element) => {
    element.inert = inert;
  });
}

function showSheet(html) {
  const overlay = $("overlay");
  const wasOpen = overlay.classList.contains("show");
  if (!wasOpen) {
    state.lastFocusedElement = document.activeElement;
    try {
      history.pushState({ shikeSheet: true }, "");
      state.sheetHistoryActive = true;
    } catch {
      state.sheetHistoryActive = false;
    }
    document.body.classList.add("modal-open");
    setModalBackgroundInert(true);
  }
  $("sheet").innerHTML = html;
  overlay.classList.add("show");
  overlay.setAttribute("aria-hidden", "false");
  requestAnimationFrame(() => $("sheet").focus());
}

function hideSheet({ fromHistory = false } = {}) {
  const overlay = $("overlay");
  const shouldPopHistory = state.sheetHistoryActive && !fromHistory;
  state.sheetHistoryActive = false;
  state.analysisRequest += 1;
  state.imageRequest += 1;
  overlay.classList.remove("show");
  overlay.setAttribute("aria-hidden", "true");
  $("sheet").innerHTML = "";
  document.body.classList.remove("modal-open");
  setModalBackgroundInert(false);
  resetPending();
  state.lastFocusedElement?.focus?.();
  state.lastFocusedElement = null;
  if (shouldPopHistory) history.back();
}

function hideToast() {
  clearTimeout(state.undoTimer);
  state.undoTimer = null;
  $("toast").classList.remove("show");
  $("toast").hidden = true;
}

function offerDeleteUndo(entry, index, key) {
  hideToast();
  state.deletedEntry = { entry, index, key };
  $("toastMessage").textContent = `已删除“${entry.name || "这条记录"}”`;
  $("toast").hidden = false;
  $("toast").classList.add("show");
  state.undoTimer = setTimeout(() => {
    state.deletedEntry = null;
    hideToast();
  }, 5000);
}

function showSettings() {
  const settings = getSettings();
  const keys = getKeys();
  const discoveredModels = new Map();
  const selectedModels = new Map([[settings.provider, effectiveModel(settings)]]);
  let activeProviderId = settings.provider;
  let discoveryRequest = 0;
  const providerOptions = PROVIDERS.map((provider) =>
    `<option value="${escapeHtml(provider.id)}" ${provider.id === settings.provider ? "selected" : ""}>${escapeHtml(provider.name)}</option>`
  ).join("");

  showSheet(`<h3>设置</h3>
    <div class="field"><label for="setProvider">服务商</label><select id="setProvider">${providerOptions}</select></div>
    <div class="field" id="modelField"><label for="setModel">模型</label><select id="setModel"></select></div>
    <div class="field is-hidden" id="customModelField">
      <label for="setCustomModel">可用模型</label>
      <select id="setCustomModel"></select>
      <div class="hint">模型直接从接口的 /v1/models 读取，不需要手填或猜测。</div>
    </div>
    <div class="field is-hidden" id="baseUrlField">
      <label for="setBaseUrl">接口地址（OpenAI 兼容 Base URL）</label>
      <input id="setBaseUrl" value="${escapeHtml(settings.customBaseUrl)}" maxlength="2048" inputmode="url" placeholder="https://api.example.com/v1">
    </div>
    <div class="field">
      <label for="setKey">API Key</label>
      <input id="setKey" type="password" autocomplete="off">
      <div class="hint">API Key 仅保存在此 App 的本地存储中，识别时直接发送给所选服务商。</div>
    </div>
    <div class="model-tools">
      <button type="button" class="model-tool" id="fetchModelsBtn">自动获取模型</button>
      <button type="button" class="model-tool primary" id="testConnectionBtn">测试连接</button>
    </div>
    <div class="connection-status" id="connectionStatus" role="status" aria-live="polite"></div>
    <div class="field">
      <label for="setGoal">每日热量目标（千卡）</label>
      <input id="setGoal" type="number" value="${getGoal()}" min="1" max="100000" inputmode="numeric">
    </div>
    <div class="settings-error" id="settingsError" role="alert"></div>
    <div class="sheet-actions">
      <button type="button" class="btn-cancel js-close">取消</button>
      <button type="button" class="btn-save" id="saveSettingsBtn">保存</button>
    </div>`);

  const providerSelect = $("setProvider");
  const modelSelectFor = (providerId) => providerId === "custom" ? $("setCustomModel") : $("setModel");
  const rememberSelection = () => {
    const select = modelSelectFor(activeProviderId);
    if (select?.value) selectedModels.set(activeProviderId, select.value);
  };
  const setConnectionStatus = (message = "", kind = "") => {
    const status = $("connectionStatus");
    status.textContent = message;
    status.className = `connection-status${kind ? ` ${kind}` : ""}`;
  };
  const renderModelOptions = (provider) => {
    const select = modelSelectFor(provider.id);
    const discovered = discoveredModels.get(provider.id);
    const savedModel = provider.id === settings.provider ? effectiveModel(settings) : "";
    const preferred = selectedModels.get(provider.id) || savedModel;
    const staticLabels = new Map(provider.models.map((model) => [model.id, model.label]));
    const candidates = discovered || (provider.id === "custom"
      ? (savedModel ? [savedModel] : [])
      : provider.models.map((model) => model.id));

    if (!candidates.length) {
      select.innerHTML = '<option value="">请先自动获取模型</option>';
      select.disabled = true;
      return;
    }

    select.disabled = false;
    select.innerHTML = candidates.map((modelId) => {
      const label = staticLabels.get(modelId) || modelId;
      return `<option value="${escapeHtml(modelId)}" ${modelId === preferred ? "selected" : ""}>${escapeHtml(label)}</option>`;
    }).join("");
    selectedModels.set(provider.id, select.value);
  };
  const rebuild = () => {
    const provider = providerMeta(providerSelect.value);
    if (!provider) return;
    const custom = provider.id === "custom";
    $("modelField").classList.toggle("is-hidden", custom);
    $("customModelField").classList.toggle("is-hidden", !custom);
    $("baseUrlField").classList.toggle("is-hidden", !custom);
    renderModelOptions(provider);
    $("setKey").value = keys[provider.id] || "";
    $("settingsError").textContent = "";
    setConnectionStatus();
  };
  providerSelect.addEventListener("change", () => {
    rememberSelection();
    activeProviderId = providerSelect.value;
    discoveryRequest += 1;
    rebuild();
  });
  rebuild();

  const discoverModels = async (connectionTest = false) => {
    const providerId = providerSelect.value;
    const provider = providerMeta(providerId);
    if (!provider) return;
    const requestId = ++discoveryRequest;
    const fetchButton = $("fetchModelsBtn");
    const testButton = $("testConnectionBtn");
    fetchButton.disabled = true;
    testButton.disabled = true;
    setConnectionStatus(connectionTest ? "正在测试连接…" : "正在读取可用模型…", "loading");

    try {
      const models = await listAvailableModels({
        settings: {
          provider: providerId,
          customBaseUrl: $("setBaseUrl").value.trim(),
        },
        apiKey: $("setKey").value.trim(),
      });
      if (requestId !== discoveryRequest || providerSelect.value !== providerId) return;
      rememberSelection();
      discoveredModels.set(providerId, models);
      renderModelOptions(provider);
      setConnectionStatus(
        `${connectionTest ? "连接成功" : "获取成功"}，发现 ${models.length} 个模型。请选择支持图片输入的模型。`,
        "success",
      );
    } catch (error) {
      if (requestId !== discoveryRequest || providerSelect.value !== providerId) return;
      setConnectionStatus(error.message || "无法获取模型，请检查接口设置", "error");
    } finally {
      if (requestId === discoveryRequest && providerSelect.value === providerId) {
        fetchButton.disabled = false;
        testButton.disabled = false;
      }
    }
  };
  $("fetchModelsBtn").addEventListener("click", () => discoverModels(false));
  $("testConnectionBtn").addEventListener("click", () => discoverModels(true));

  $("saveSettingsBtn").addEventListener("click", () => {
    try {
      const providerId = providerSelect.value;
      const customModel = $("setCustomModel").value.trim();
      const customBaseUrl = $("setBaseUrl").value.trim();
      if (providerId === "custom") {
        if (!customModel) throw new Error("请先自动获取并选择一个可用模型");
        normalizeBaseUrl(customBaseUrl);
      }
      const goal = Number.parseInt($("setGoal").value, 10);
      if (!Number.isInteger(goal) || goal < 1 || goal > 100_000) throw new Error("每日目标需在 1 到 100000 千卡之间");
      const nextSettings = {
        provider: providerId,
        model: providerId === "custom" ? "" : $("setModel").value,
        customModel,
        customBaseUrl,
      };
      localStorage.setItem(SETTINGS_KEY, JSON.stringify(nextSettings));
      const nextKeys = getKeys();
      const key = $("setKey").value.trim();
      if (key) nextKeys[providerId] = key;
      else delete nextKeys[providerId];
      localStorage.setItem(KEYS_KEY, JSON.stringify(nextKeys));
      localStorage.setItem(GOAL_KEY, String(goal));
      hideSheet();
      render();
    } catch (error) {
      $("settingsError").textContent = error.message || "设置保存失败";
    }
  });
}

function compressImage(file) {
  return new Promise((resolve, reject) => {
    const image = new Image();
    const objectUrl = URL.createObjectURL(file);
    image.onload = () => {
      try {
        const makeImage = (maxEdge, quality) => {
          const scale = Math.min(1, maxEdge / Math.max(image.width, image.height));
          const canvas = document.createElement("canvas");
          canvas.width = Math.max(1, Math.round(image.width * scale));
          canvas.height = Math.max(1, Math.round(image.height * scale));
          const context = canvas.getContext("2d");
          if (!context) throw new Error("当前设备无法处理这张图片");
          context.drawImage(image, 0, 0, canvas.width, canvas.height);
          return canvas.toDataURL("image/jpeg", quality);
        };
        resolve({ dataUrl: makeImage(1280, .85), thumb: makeImage(120, .7) });
      } catch (error) {
        reject(error);
      } finally {
        URL.revokeObjectURL(objectUrl);
      }
    };
    image.onerror = () => {
      URL.revokeObjectURL(objectUrl);
      reject(new Error("无法读取这张图片，请换一张重试"));
    };
    image.src = objectUrl;
  });
}

function showCapturePreview(dataUrl) {
  const image = safeImageDataUrl(dataUrl);
  if (!image) {
    showError("图片数据无效，请重新选择照片");
    return;
  }
  showSheet(`<img src="${image}" alt="待识别的食物照片">
    <div class="field">
      <label for="noteInput">补充说明（可选）</label>
      <textarea id="noteInput" maxlength="500">${escapeHtml(state.pendingNote)}</textarea>
      <div class="hint">补充份量、价格或品牌等信息，可以提高估算准确度。</div>
    </div>
    <div class="sheet-actions">
      <button type="button" class="btn-cancel js-close">取消</button>
      <button type="button" class="btn-save" id="startAnalyzeBtn">开始识别</button>
    </div>`);
  $("startAnalyzeBtn").addEventListener("click", () => runAnalyze(image, $("noteInput").value.trim()));
}

function showError(message, dataUrl = "") {
  const image = safeImageDataUrl(dataUrl);
  showSheet(`${image ? `<img src="${image}" alt="食物照片">` : ""}
    <div class="error-msg">${escapeHtml(message)}</div>
    <div class="sheet-actions"><button type="button" class="btn-cancel js-close">关闭</button></div>`);
}

async function runAnalyze(dataUrl, note) {
  const requestId = ++state.analysisRequest;
  state.pendingNote = note.slice(0, 500);
  showSheet(`<img src="${dataUrl}" alt="食物照片">
    <div class="loading" role="status" aria-live="polite"><div class="spinner"></div>正在识别食物…</div>`);
  try {
    const settings = getSettings();
    const result = await analyzeFood({
      settings,
      apiKey: getKeys()[settings.provider],
      imageBase64: dataUrl.split(",")[1],
      note: state.pendingNote,
    });
    if (requestId !== state.analysisRequest) return;
    showResult(dataUrl, result);
  } catch (error) {
    if (requestId !== state.analysisRequest) return;
    showSheet(`<img src="${dataUrl}" alt="食物照片">
      <div class="error-msg">${escapeHtml(error.message || "识别失败，请重试")}</div>
      <div class="sheet-actions">
        <button type="button" class="btn-cancel js-close">关闭</button>
        <button type="button" class="btn-save" id="retryBtn">返回修改</button>
      </div>`);
    $("retryBtn").addEventListener("click", () => showCapturePreview(dataUrl));
  }
}

function showResult(dataUrl, value) {
  const result = normalizeResult(value);
  if (!result.is_food || !result.foods.length) {
    showError("照片中没有识别到食物", dataUrl);
    return;
  }
  state.pendingResult = result;
  const rows = result.foods.map((food) => `<div class="food-row">
    <div class="info"><b>${escapeHtml(food.name)}</b><span>${escapeHtml(food.portion)} · 蛋白 ${food.protein_g}g · 碳水 ${food.carbs_g}g · 脂肪 ${food.fat_g}g</span></div>
    <span class="kcal">${food.calories} 千卡</span>
  </div>`).join("");
  showSheet(`<img src="${dataUrl}" alt="食物照片">
    ${state.pendingNote ? `<div class="confidence">你的说明：${escapeHtml(state.pendingNote)}</div>` : ""}
    ${rows}
    <div class="result-total"><span>合计</span><span>${result.total_calories} 千卡</span></div>
    <div class="confidence">${CONFIDENCE_LABEL[result.confidence]}${result.notes ? ` · ${escapeHtml(result.notes)}` : ""}</div>
    <div class="sheet-actions">
      <button type="button" class="btn-cancel js-close">取消</button>
      <button type="button" class="btn-save" id="saveEntryBtn">${isToday(state.viewDate) ? "记入今日" : "记入当日"}</button>
    </div>`);
  $("saveEntryBtn").addEventListener("click", saveEntry);
}

function saveEntry() {
  if (!state.pendingResult) return;
  try {
    const result = state.pendingResult;
    const log = getLog();
    log.push({
      name: result.foods.map((food) => food.name).join("、"),
      calories: result.total_calories,
      protein: result.foods.reduce((sum, food) => sum + food.protein_g, 0),
      carbs: result.foods.reduce((sum, food) => sum + food.carbs_g, 0),
      fat: result.foods.reduce((sum, food) => sum + food.fat_g, 0),
      time: new Date().toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit" }),
      note: state.pendingNote,
      thumb: state.pendingThumb,
    });
    saveLog(log);
    hideSheet();
    render();
  } catch {
    showError("本地存储空间不足，请删除一些旧记录后重试");
  }
}

function shiftDay(days) {
  const next = new Date(state.viewDate.getFullYear(), state.viewDate.getMonth(), state.viewDate.getDate() + days);
  if (localDateString(next) > localDateString(new Date())) return;
  state.viewDate = next;
  render();
}

$("settingsBtn").addEventListener("click", showSettings);
$("prevDay").addEventListener("click", () => shiftDay(-1));
$("nextDay").addEventListener("click", () => shiftDay(1));
$("todayBtn").addEventListener("click", () => { state.viewDate = new Date(); render(); });
$("captureBtn").addEventListener("click", () => $("fileInput").click());
$("fileInput").addEventListener("change", async (event) => {
  const file = event.target.files?.[0];
  event.target.value = "";
  if (!file) return;
  let requestId = 0;
  try {
    if (!file.type.startsWith("image/")) throw new Error("请选择图片文件");
    if (file.size > 25 * 1024 * 1024) throw new Error("原图不能超过 25 MB");
    requestId = ++state.imageRequest;
    showSheet(`<div class="loading" role="status" aria-live="polite"><div class="spinner"></div>正在优化照片…</div>`);
    const { dataUrl, thumb } = await compressImage(file);
    if (requestId !== state.imageRequest) return;
    state.pendingThumb = thumb;
    showCapturePreview(dataUrl);
  } catch (error) {
    if (requestId && requestId !== state.imageRequest) return;
    showError(error.message || "图片读取失败");
  }
});
$("entryList").addEventListener("click", (event) => {
  const button = event.target.closest(".del");
  if (!button) return;
  const index = Number.parseInt(button.dataset.index, 10);
  const log = getLog();
  if (!Number.isInteger(index) || index < 0 || index >= log.length) return;
  const [entry] = log.splice(index, 1);
  const key = logKey();
  saveLog(log);
  render();
  offerDeleteUndo(entry, index, key);
});
$("overlay").addEventListener("click", (event) => {
  if (event.target === $("overlay") || event.target.closest(".js-close")) hideSheet();
});
$("editGoal").addEventListener("click", showSettings);
document.addEventListener("keydown", (event) => {
  if (!$("overlay").classList.contains("show")) return;
  if (event.key === "Escape") {
    hideSheet();
    return;
  }
  if (event.key !== "Tab") return;
  const focusable = [...$("sheet").querySelectorAll("button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [href], [tabindex]:not([tabindex='-1'])")]
    .filter((element) => !element.hidden && element.offsetParent !== null);
  if (!focusable.length) {
    event.preventDefault();
    $("sheet").focus();
    return;
  }
  const first = focusable[0];
  const last = focusable[focusable.length - 1];
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first.focus();
  }
});
window.addEventListener("popstate", () => {
  if ($("overlay").classList.contains("show")) hideSheet({ fromHistory: true });
});
$("undoDeleteBtn").addEventListener("click", () => {
  const deletion = state.deletedEntry;
  if (!deletion) return;
  const log = readJson(deletion.key, []);
  if (Array.isArray(log)) {
    log.splice(Math.min(deletion.index, log.length), 0, deletion.entry);
    localStorage.setItem(deletion.key, JSON.stringify(log));
  }
  state.deletedEntry = null;
  hideToast();
  if (deletion.key === logKey()) render();
});

render();
