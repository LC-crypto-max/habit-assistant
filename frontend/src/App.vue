<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from "vue";

const userId = ref("me");
const shareText = ref("");
const query = ref("");
const consent = ref(false);
const busy = ref(false);
const refreshing = ref(false);
const backendOnline = ref(false);
const systemStatus = ref(null);
const task = ref(null);
const snapshot = ref(null);
const errorMessage = ref("");
const noticeMessage = ref("");
let pollTimer = null;

const sensitiveKeys = new Set([
  "access_token", "auth", "authorization", "code", "cookie", "key", "password",
  "session", "signature", "token", "xsec_token", "xsec_source", "share_source",
  "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
  "source", "from", "feature", "si", "fbclid", "gclid"
]);

const publicUrl = computed(() => extractXiaohongshuUrl(shareText.value));
const browserUrl = computed(() => extractXiaohongshuBrowserUrl(shareText.value));
const canSubmit = computed(() => Boolean(publicUrl.value && consent.value && !busy.value));
const readiness = computed(() => snapshot.value?.readiness ?? {});
const visits = computed(() => snapshot.value?.visits ?? []);
const interests = computed(() => snapshot.value?.profile?.topInterests ?? []);
const recommendations = computed(() => snapshot.value?.recommendations?.recommendations ?? []);
const componentFailures = computed(() =>
  (snapshot.value?.components ?? []).filter((component) => component.status !== "UP")
);

const steps = computed(() => {
  const failed = task.value?.status === "FAILED";
  const running = task.value?.status === "RUNNING";
  return [
    {
      label: "访问已入队",
      detail: task.value ? shortTaskId(task.value.taskId) : "等待提交公开链接",
      done: Boolean(task.value),
      active: busy.value && !task.value,
      failed: false
    },
    {
      label: "Agent Reach",
      detail: readiness.value.agentReachLive ? "OpenCLI 已读取公开内容" : running ? "正在读取页面" : "等待 Worker",
      done: Boolean(readiness.value.agentReachLive),
      active: running && !readiness.value.agentReachLive,
      failed
    },
    {
      label: "Codex AI",
      detail: readiness.value.aiAnalyzed ? "语义分析成功" : running ? "将在页面读取后执行" : "等待公开内容",
      done: Boolean(readiness.value.aiAnalyzed),
      active: running && readiness.value.agentReachLive,
      failed
    },
    {
      label: "兴趣画像",
      detail: readiness.value.profileReady ? `${interests.value.length} 个主要兴趣` : "等待高质量信号",
      done: Boolean(readiness.value.profileReady),
      active: task.value?.status === "COMPLETED" && !readiness.value.profileReady,
      failed: false
    },
    {
      label: "今日推荐",
      detail: readiness.value.recommendationsReady ? `${recommendations.value.length} 条结果` : "等待画像生成",
      done: Boolean(readiness.value.recommendationsReady),
      active: task.value?.status === "COMPLETED" && !readiness.value.recommendationsReady,
      failed: false
    }
  ];
});

function extractXiaohongshuUrl(input) {
  const match = String(input || "").match(/https?:\/\/[^\s<>"',，。；！）》】]+/i);
  if (!match) return "";
  const candidate = match[0].replace(/[.,;:!?\)\]\}，。；：！？）】》」』’"]+$/u, "");
  try {
    const url = new URL(candidate);
    const host = url.hostname.toLowerCase();
    const validHost = host === "xiaohongshu.com" || host.endsWith(".xiaohongshu.com")
      || host === "xhslink.com" || host.endsWith(".xhslink.com");
    if (!validHost || !["http:", "https:"].includes(url.protocol)) return "";
    url.protocol = "https:";
    url.username = "";
    url.password = "";
    url.hash = "";
    for (const key of [...url.searchParams.keys()]) {
      if (sensitiveKeys.has(key.toLowerCase())) url.searchParams.delete(key);
    }
    return url.toString();
  } catch {
    return "";
  }
}

function extractXiaohongshuBrowserUrl(input) {
  const match = String(input || "").match(/https?:\/\/[^\s<>"',，。；！）》】]+/i);
  if (!match) return "";
  const candidate = match[0].replace(/[.,;:!?\)\]\}，。；：！？）】》」』’"]+$/u, "");
  try {
    const url = new URL(candidate);
    const host = url.hostname.toLowerCase();
    const validHost = host === "xiaohongshu.com" || host.endsWith(".xiaohongshu.com")
      || host === "xhslink.com" || host.endsWith(".xhslink.com");
    if (!validHost || !["http:", "https:"].includes(url.protocol)) return "";
    url.protocol = "https:";
    url.username = "";
    url.password = "";
    url.hash = "";
    return url.toString();
  } catch {
    return "";
  }
}

function extractPublicTitle(input) {
  const text = String(input || "");
  const bracketed = text.match(/【([^】]{4,240})】/u)?.[1] || text.split(/https?:\/\//i)[0];
  return bracketed
    .replace(/^\s*\d+\s*/u, "")
    .replace(/\s*[|｜]\s*小红书.*$/iu, "")
    .replace(/\s+/gu, " ")
    .trim()
    .slice(0, 120);
}

async function api(path, options = {}, timeoutMs = 12000) {
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(path, {
      credentials: "same-origin",
      headers: { "Content-Type": "application/json", ...(options.headers || {}) },
      ...options,
      signal: controller.signal
    });
    const contentType = response.headers.get("content-type") || "";
    const payload = contentType.toLowerCase().includes("json") ? await response.json() : null;
    if (!response.ok) {
      throw new Error(payload?.message || `请求失败（HTTP ${response.status}）`);
    }
    return payload;
  } catch (error) {
    if (error.name === "AbortError") throw new Error("请求超时，请确认后端和 MySQL 正常运行");
    throw error;
  } finally {
    window.clearTimeout(timeout);
  }
}

async function loadDashboard({ quiet = false } = {}) {
  if (!quiet) refreshing.value = true;
  const resolvedUser = userId.value.trim() || "me";
  const [healthResult, systemResult, snapshotResult] = await Promise.allSettled([
    api("/actuator/health", {}, 5000),
    api("/api/v1/demo-system/status", {}, 5000),
    api(`/api/v1/demo-snapshots/xiaohongshu?userId=${encodeURIComponent(resolvedUser)}`, {}, 15000)
  ]);
  if (systemResult.status === "fulfilled") systemStatus.value = systemResult.value;
  backendOnline.value = healthResult.status === "fulfilled"
    && healthResult.value?.status === "UP"
    && systemResult.status === "fulfilled"
    && systemResult.value?.ready;
  if (snapshotResult.status === "fulfilled") {
    snapshot.value = snapshotResult.value;
    if (snapshotResult.value?.degraded) {
      noticeMessage.value = "部分数据暂不可用，页面已进入安全降级模式；可查看具体模块状态。";
    }
  } else if (!quiet) {
    errorMessage.value = friendlyError(snapshotResult.reason);
  }
  refreshing.value = false;
}

async function runDemo() {
  errorMessage.value = "";
  noticeMessage.value = "";
  if (!publicUrl.value) {
    errorMessage.value = "没有识别到有效的小红书公开链接，请粘贴完整分享文案或 URL。";
    return;
  }
  if (!consent.value) {
    errorMessage.value = "请先确认本次授权范围。";
    return;
  }

  busy.value = true;
  task.value = null;
  try {
    // The signed share URL stays inside Chrome. It is never included in an API
    // request, backend task, process argument, Codex prompt, database, or log.
    if (browserUrl.value) {
      const noteWindow = window.open(browserUrl.value, "habit-assistant-xhs-note");
      if (noteWindow) {
        try { noteWindow.opener = null; noteWindow.focus(); } catch { /* cross-origin window */ }
      }
    }
    task.value = await api("/api/agent/queries", {
      method: "POST",
      body: JSON.stringify({
        userId: userId.value.trim() || "me",
        adapter: "agent-reach",
        platform: "xiaohongshu",
        intent: "read-public-note",
        url: publicUrl.value,
        query: query.value.trim() || extractPublicTitle(shareText.value)
      })
    });
    await api("/api/agent/worker/start-once", {
      method: "POST",
      body: JSON.stringify({
        dryRun: false,
        limit: 5,
        allowAuthenticatedBrowser: true,
        agentReachMode: "live",
        confirmedByUser: true,
        taskId: task.value.taskId
      })
    });
    noticeMessage.value = "授权已确认。签名链接仅在 Chrome 打开；后端与 Codex 只接收脱敏 URL 和 OpenCLI 提取的公开内容。";
    await pollTask(task.value.taskId);
  } catch (error) {
    errorMessage.value = friendlyError(error);
  } finally {
    busy.value = false;
  }
}

async function pollTask(taskId) {
  const deadline = Date.now() + 180000;
  while (Date.now() < deadline) {
    await wait(1600);
    task.value = await api(`/api/agent/queries/${encodeURIComponent(taskId)}`, {}, 6000);
    if (task.value.status === "FAILED") {
      throw new Error(task.value.errorMessage || "本地分析任务失败，请查看 PowerShell 中的阶段日志");
    }
    if (task.value.status === "COMPLETED") {
      try {
        await api(`/api/recommendations/rebuild?userId=${encodeURIComponent(userId.value.trim() || "me")}`, {
          method: "POST",
          body: "{}"
        }, 30000);
      } catch (error) {
        noticeMessage.value = `AI 分析已保存，但推荐刷新暂时失败：${friendlyError(error)}`;
      }
      await loadDashboard({ quiet: true });
      consent.value = false;
      noticeMessage.value = readiness.value.ready
        ? "完整演示链路已完成：公开访问、页面读取、AI 分析、画像和推荐均已生成。"
        : noticeMessage.value || "AI 分析已保存，画像与推荐正在根据有效证据更新。";
      return;
    }
  }
  throw new Error("分析仍在运行。页面会保留任务编号，你可以稍后点击“刷新结果”。");
}

async function rebuildRecommendations() {
  refreshing.value = true;
  errorMessage.value = "";
  try {
    await api(`/api/recommendations/rebuild?userId=${encodeURIComponent(userId.value.trim() || "me")}`, {
      method: "POST",
      body: "{}"
    }, 30000);
    await loadDashboard({ quiet: true });
    noticeMessage.value = "已使用最新画像重新生成今日推荐。";
  } catch (error) {
    errorMessage.value = friendlyError(error);
  } finally {
    refreshing.value = false;
  }
}

function friendlyError(error) {
  const text = String(error?.message || error || "未知错误");
  const mappings = [
    ["PUBLIC_URL_INVALID_OR_PLATFORM_MISMATCH", "链接不是有效的小红书公开地址。"],
    ["BROWSER_AUTHORIZATION_CONFIRMATION_REQUIRED", "需要在页面确认浏览器会话授权。"],
    ["LOCAL_WORKER", "无法启动本地 Worker，请确认项目从 Windows 本机运行。"],
    ["Agent Reach could not read the matching", "Agent Reach 未能读取刚打开的目标笔记。请允许 localhost 弹出窗口，保持目标笔记在 Chrome 中可见，然后重新提交任务。"],
    ["Codex analysis did not complete", "Codex 分析未完成，请确认 Codex CLI 已登录。"],
    ["Failed to fetch", "无法连接后端，请确认 MySQL 与 Spring Boot 服务正在运行。"]
  ];
  return mappings.find(([needle]) => text.includes(needle))?.[1] || text.replace(/^HTTP \d+:\s*/i, "");
}

function wait(ms) {
  return new Promise((resolve) => { pollTimer = window.setTimeout(resolve, ms); });
}

function shortTaskId(value) {
  if (!value) return "";
  return `${value.slice(0, 12)}…${value.slice(-5)}`;
}

function formatTime(value) {
  if (!value) return "时间未知";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit"
  }).format(date);
}

function stepClass(step) {
  if (step.failed) return "is-failed";
  if (step.done) return "is-done";
  if (step.active) return "is-active";
  return "is-waiting";
}

onMounted(() => loadDashboard());
onBeforeUnmount(() => { if (pollTimer) window.clearTimeout(pollTimer); });
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <a class="brand" href="#top" aria-label="返回页面顶部">
        <span class="brand-mark">H</span>
        <span><b>Habit Assistant</b><small>本地兴趣分析台</small></span>
      </a>
      <div class="service-state" :class="{ online: backendOnline }" role="status">
        <span class="status-dot"></span>
        {{ backendOnline ? `${systemStatus?.database || "数据库"} 演示服务在线` : "正在连接本地服务" }}
      </div>
    </header>

    <main id="top">
      <section class="hero">
        <div>
          <p class="eyebrow">PRIVATE BY DESIGN · DEMO CONSOLE</p>
          <h1>把一次公开访问，<br /><span>变成可解释的兴趣推荐。</span></h1>
          <p class="hero-copy">粘贴你本人访问的小红书公开笔记。Agent Reach 读取公开页面，Codex 提炼兴趣语义，本地数据库保存脱敏结果。</p>
        </div>
        <div class="privacy-seal" aria-label="隐私边界说明">
          <span>本地优先</span>
          <strong>0</strong>
          <small>账号密码 / Cookie / 私信被保存</small>
        </div>
      </section>

      <div class="alert error" v-if="errorMessage" role="alert">
        <span>!</span><p>{{ errorMessage }}</p><button type="button" @click="errorMessage = ''" aria-label="关闭错误">×</button>
      </div>
      <div class="alert notice" v-if="noticeMessage" aria-live="polite">
        <span>i</span><p>{{ noticeMessage }}</p><button type="button" @click="noticeMessage = ''" aria-label="关闭提示">×</button>
      </div>

      <section class="workbench">
        <article class="panel authorization-panel">
          <div class="panel-heading">
            <div><p class="kicker">01 · 授权采集</p><h2>提交公开笔记</h2></div>
            <span class="platform-badge">小红书</span>
          </div>

          <label for="user-id">演示用户</label>
          <input id="user-id" v-model.trim="userId" autocomplete="off" maxlength="120" placeholder="me" />

          <label for="share-text">分享文案或公开链接</label>
          <textarea id="share-text" v-model="shareText" rows="5" maxlength="2000"
            placeholder="例如：复制小红书中的分享文案，链接可以夹在文字中…"></textarea>
          <div class="url-preview" :class="{ valid: publicUrl }">
            <span>{{ publicUrl ? "✓" : "↗" }}</span>
            <div><small>{{ publicUrl ? "已识别并脱敏" : "等待识别公开 URL" }}</small><code>{{ publicUrl || "支持 xhslink.com 与 xiaohongshu.com" }}</code></div>
          </div>

          <label for="query">补充关注点 <span class="optional">可选</span></label>
          <input id="query" v-model="query" maxlength="512" placeholder="例如：这篇笔记主要反映了什么兴趣？" />

          <label class="consent-box" :class="{ checked: consent }">
            <input v-model="consent" type="checkbox" />
            <span class="checkmark">{{ consent ? "✓" : "" }}</span>
            <span><b>我确认这是本人访问的公开内容</b><small>仅本次复用当前浏览器登录读取公开页面；不读取或保存 Cookie、Token、Session、私信和账号密码。</small></span>
          </label>

          <button class="primary-action" type="button" :disabled="!canSubmit" @click="runDemo">
            <span v-if="busy" class="spinner"></span>
            {{ busy ? "正在执行分析链路…" : "授权并开始 AI 分析" }}
          </button>
          <p class="action-note">授权后会打开 PowerShell 展示实时阶段日志，无需再次输入 y。</p>
        </article>

        <article class="panel progress-panel">
          <div class="panel-heading">
            <div><p class="kicker">02 · 实时进度</p><h2>证据链状态</h2></div>
            <button class="ghost-button" type="button" :disabled="refreshing" @click="loadDashboard()">{{ refreshing ? "刷新中" : "刷新结果" }}</button>
          </div>
          <ol class="pipeline-list">
            <li v-for="(step, index) in steps" :key="step.label" :class="stepClass(step)">
              <span class="step-index">{{ step.done ? "✓" : index + 1 }}</span>
              <div><b>{{ step.label }}</b><small>{{ step.detail }}</small></div>
              <span class="step-state">{{ step.failed ? "失败" : step.done ? "完成" : step.active ? "进行中" : "待处理" }}</span>
            </li>
          </ol>
          <div class="task-strip" v-if="task">
            <span>当前任务</span><code>{{ task.taskId }}</code><b :class="`task-${task.status?.toLowerCase()}`">{{ task.status }}</b>
          </div>
          <div class="degraded-box" v-if="componentFailures.length">
            <b>安全降级已启用</b>
            <p v-for="component in componentFailures" :key="component.component">{{ component.message }}</p>
          </div>
          <div class="empty-progress" v-else-if="!task && !visits.length">
            <span>↗</span><p>提交第一条公开笔记后，这里会逐步点亮完整分析链路。</p>
          </div>
        </article>
      </section>

      <section class="results-header">
        <div><p class="eyebrow">ANALYSIS OUTPUT</p><h2>AI 分析结果</h2></div>
        <button class="secondary-action" type="button" :disabled="refreshing" @click="rebuildRecommendations">使用最新画像重算推荐</button>
      </section>

      <section class="result-grid">
        <article class="panel profile-card">
          <div class="card-title"><span>兴趣画像</span><small>{{ snapshot?.profile?.profileVersion || "等待证据" }}</small></div>
          <p class="profile-summary">{{ snapshot?.profile?.summary || "完成一次高质量公开页面分析后，Codex 生成的兴趣摘要会显示在这里。" }}</p>
          <div class="interest-list" v-if="interests.length">
            <div v-for="interest in interests.slice(0, 6)" :key="interest.name" class="interest-row">
              <span>{{ interest.name }}</span><div><i :style="{ width: `${Math.min(100, Math.max(8, interest.score * 10))}%` }"></i></div><b>{{ interest.confidence }}</b>
            </div>
          </div>
          <div class="empty-card" v-else>暂无稳定兴趣标签</div>
        </article>

        <article class="panel evidence-card">
          <div class="card-title"><span>最近公开访问</span><small>{{ visits.length }} 条</small></div>
          <div class="visit-list" v-if="visits.length">
            <article v-for="visit in visits.slice(0, 5)" :key="visit.id" class="visit-item">
              <div class="visit-meta"><span>{{ formatTime(visit.occurredAt) }}</span><b>{{ visit.llmStatus || "未分析" }}</b></div>
              <h3>{{ visit.title || "未命名公开笔记" }}</h3>
              <p>{{ visit.summary || "暂无内容摘要" }}</p>
              <div class="tag-row"><span v-for="tag in (visit.tags || []).slice(0, 5)" :key="tag">{{ tag }}</span></div>
              <a v-if="visit.url" :href="visit.url" target="_blank" rel="noreferrer">查看公开来源 ↗</a>
            </article>
          </div>
          <div class="empty-card" v-else>尚未保存可验证的小红书公开访问</div>
        </article>

        <article class="panel recommendations-card">
          <div class="card-title"><span>今日推荐</span><small>{{ recommendations.length }} 条</small></div>
          <div class="recommendation-list" v-if="recommendations.length">
            <article v-for="(item, index) in recommendations.slice(0, 6)" :key="item.id || index">
              <span class="recommendation-number">0{{ index + 1 }}</span>
              <div><small>{{ item.content?.platform || "兴趣推荐" }}</small><h3>{{ item.content?.title || "未命名推荐" }}</h3><p>{{ item.reason }}</p></div>
              <a v-if="item.content?.url" :href="item.content.url" target="_blank" rel="noreferrer" aria-label="打开推荐">↗</a>
            </article>
          </div>
          <div class="empty-card" v-else>画像形成后将生成可点击的今日推荐</div>
        </article>
      </section>
    </main>

    <footer><span>Habit Assistant · Local-first Demo</span><span>公开数据最小化采集 · 默认脱敏 · {{ systemStatus?.database || "本地数据库" }} 存储</span></footer>
  </div>
</template>
