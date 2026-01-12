import { installDevConsoleFilters } from "./devConsoleFilters";

type Percentiles = {
  p50Ms: number | null;
  p95Ms: number | null;
};

type DevPerfSpan = {
  name: string;
  startMs: number;
  durationMs: number;
  details?: Record<string, unknown>;
};

export type RollingStatsSnapshot = {
  count: number;
  avgMs: number | null;
  minMs: number | null;
  maxMs: number | null;
} & Percentiles;

export class RollingStats {
  readonly name: string;
  readonly maxSamples: number;
  private readonly samples: number[] = [];
  private totalCount = 0;
  private totalMs = 0;
  private minMs = Number.POSITIVE_INFINITY;
  private maxMs = 0;

  constructor(name: string, maxSamples = 200) {
    this.name = name;
    this.maxSamples = Math.max(10, Math.floor(maxSamples));
  }

  add(ms: number): void {
    if (!Number.isFinite(ms)) return;
    const v = Math.max(0, ms);
    this.totalCount += 1;
    this.totalMs += v;
    this.minMs = Math.min(this.minMs, v);
    this.maxMs = Math.max(this.maxMs, v);

    this.samples.push(v);
    if (this.samples.length > this.maxSamples) this.samples.splice(0, this.samples.length - this.maxSamples);
  }

  get count(): number {
    return this.totalCount;
  }

  snapshot(): RollingStatsSnapshot {
    if (this.totalCount === 0) {
      return { count: 0, avgMs: null, minMs: null, maxMs: null, p50Ms: null, p95Ms: null };
    }
    const avgMs = this.totalMs / Math.max(1, this.totalCount);
    const { p50Ms, p95Ms } = percentileSnapshot(this.samples);
    return {
      count: this.totalCount,
      avgMs: round1(avgMs),
      minMs: Number.isFinite(this.minMs) ? round1(this.minMs) : null,
      maxMs: Number.isFinite(this.maxMs) ? round1(this.maxMs) : null,
      p50Ms,
      p95Ms,
    };
  }
}

type ReporterOptions = {
  label?: string;
  everyMs?: number;
  minNewSamples?: number;
  includeBreakdown?: boolean;
  maxBreakdown?: number;
};

declare global {
  interface Window {
    __ZHIXU_DEV_PERF__?: { enabled: true; startedAtMs: number; flags?: Record<string, true> };
    __ZHIXU_DEV_PERF_STATS__?: Map<string, RollingStats>;
    __ZHIXU_DEV_PERF_BREAKDOWNS__?: Map<string, Set<string>>;
    __ZHIXU_DEV_PERF_SPANS__?: DevPerfSpan[];
    __ZHIXU_DEV_PERF_MARKS__?: Array<{ name: string; tMs: number }>;
    __ZHIXU_DEV_PERF_VITALS__?: { lcpMs?: number; clsTotal?: number };
  }
}

const LOG_PREFIX = "[zhixu-perf]";
let cachedEnabled: boolean | null = null;

export function isDevPerfEnabled(): boolean {
  if (!import.meta.env.DEV) return false;
  if (cachedEnabled != null) return cachedEnabled;
  try {
    const v = localStorage.getItem("zhixu.devPerf");
    if (!v) return (cachedEnabled = true);
    const normalized = v.trim().toLowerCase();
    return (cachedEnabled = !(normalized === "0" || normalized === "false" || normalized === "off" || normalized === "no"));
  } catch (_) {
    return (cachedEnabled = true);
  }
}

function statsMap(): Map<string, RollingStats> {
  if (!window.__ZHIXU_DEV_PERF_STATS__) window.__ZHIXU_DEV_PERF_STATS__ = new Map();
  return window.__ZHIXU_DEV_PERF_STATS__;
}

function breakdownMap(): Map<string, Set<string>> {
  if (!window.__ZHIXU_DEV_PERF_BREAKDOWNS__) window.__ZHIXU_DEV_PERF_BREAKDOWNS__ = new Map();
  return window.__ZHIXU_DEV_PERF_BREAKDOWNS__;
}

function spansList(): DevPerfSpan[] {
  if (!window.__ZHIXU_DEV_PERF_SPANS__) window.__ZHIXU_DEV_PERF_SPANS__ = [];
  return window.__ZHIXU_DEV_PERF_SPANS__;
}

function marksList(): Array<{ name: string; tMs: number }> {
  if (!window.__ZHIXU_DEV_PERF_MARKS__) window.__ZHIXU_DEV_PERF_MARKS__ = [];
  return window.__ZHIXU_DEV_PERF_MARKS__;
}

function vitalsState(): { lcpMs?: number; clsTotal?: number } {
  if (!window.__ZHIXU_DEV_PERF_VITALS__) window.__ZHIXU_DEV_PERF_VITALS__ = {};
  return window.__ZHIXU_DEV_PERF_VITALS__;
}

export function getRollingStats(name: string, options?: { maxSamples?: number }): RollingStats {
  const map = statsMap();
  const existing = map.get(name);
  if (existing) return existing;
  const stats = new RollingStats(name, options?.maxSamples);
  map.set(name, stats);
  return stats;
}

export function devPerfMark(name: string): void {
  if (!isDevPerfEnabled()) return;
  try {
    const tMs = performance.now();
    performance.mark(name);
    const list = marksList();
    list.push({ name, tMs });
    const max = 400;
    if (list.length > max) list.splice(0, list.length - max);
  } catch (_) {}
}

function addSample(statName: string, ms: number): void {
  getRollingStats(statName).add(ms);
}

function registerBreakdown(parent: string, child: string): void {
  const map = breakdownMap();
  const existing = map.get(parent);
  if (existing) {
    existing.add(child);
    return;
  }
  map.set(parent, new Set([child]));
}

function pickBreakdownLabel(details?: Record<string, unknown>): string | null {
  if (!details) return null;
  const ctx = details.ctx;
  if (typeof ctx === "string") {
    const v = ctx.trim();
    if (v && v.length <= 40) return v;
  }
  const mode = details.mode;
  if (typeof mode === "string") {
    const v = mode.trim();
    if (v && v.length <= 40) return v;
  }
  return null;
}

export function recordDurationMs(statName: string, ms: number, details?: Record<string, unknown>): void {
  if (!isDevPerfEnabled()) return;
  addSample(statName, ms);

  const breakdown = pickBreakdownLabel(details);
  if (breakdown) {
    const child = `${statName}:${breakdown}`;
    addSample(child, ms);
    registerBreakdown(statName, child);
  }

  // Avoid console spam: only log individual outliers.
  if (ms >= 80) {
    try {
      console.debug(`${LOG_PREFIX} slow ${statName} ${round1(ms)}ms`, details ?? {});
    } catch (_) {}
  }
}

export function recordSpanMs(statName: string, startMs: number, durationMs: number, details?: Record<string, unknown>): void {
  if (!isDevPerfEnabled()) return;
  recordDurationMs(statName, durationMs, details);

  const span: DevPerfSpan = { name: statName, startMs, durationMs, details };
  const list = spansList();
  list.push(span);
  const max = 300;
  if (list.length > max) list.splice(0, list.length - max);
}

export async function withDevPerfSpan<T>(statName: string, fn: () => Promise<T>, details?: Record<string, unknown>): Promise<T> {
  if (!isDevPerfEnabled()) return fn();
  const startMs = performance.now();
  try {
    return await fn();
  } finally {
    recordSpanMs(statName, startMs, performance.now() - startMs, details);
  }
}

export function createRafLatencyTracker(
  statName: string,
  options?: {
    label?: string;
  },
): (details?: Record<string, unknown>) => void {
  let pending = false;
  let lastStartMs = 0;

  return (details?: Record<string, unknown>) => {
    if (!isDevPerfEnabled()) return;
    lastStartMs = performance.now();
    if (pending) return;
    pending = true;
    requestAnimationFrame(() => {
      pending = false;
      const dt = performance.now() - lastStartMs;
      recordDurationMs(statName, dt, options?.label ? { ...(details ?? {}), label: options.label } : details);
    });
  };
}

export function startStatsReporter(statName: string, options?: ReporterOptions): () => void {
  if (!isDevPerfEnabled()) return () => {};
  const everyMs = options?.everyMs ?? 5000;
  const minNewSamples = options?.minNewSamples ?? 1;
  const label = options?.label ?? statName;
  const includeBreakdown = options?.includeBreakdown;
  const maxBreakdown = options?.maxBreakdown ?? 6;

  let lastCount = getRollingStats(statName).count;
  const id = window.setInterval(() => {
    const stats = getRollingStats(statName);
    const delta = stats.count - lastCount;
    if (delta < minNewSamples) return;
    lastCount = stats.count;
    try {
      const hasBreakdown = (breakdownMap().get(statName)?.size ?? 0) > 0;
      const shouldIncludeBreakdown = includeBreakdown ?? hasBreakdown;
      if (!shouldIncludeBreakdown) {
        console.debug(`${LOG_PREFIX} ${label}`, stats.snapshot());
        return;
      }

      const children = [...(breakdownMap().get(statName) ?? new Set<string>())]
        .map((name) => ({ name, snap: getRollingStats(name).snapshot() }))
        .filter((x) => x.snap.count > 0)
        .sort((a, b) => (b.snap.count ?? 0) - (a.snap.count ?? 0))
        .slice(0, maxBreakdown);

      const rows = [
        { breakdown: "total", ...stats.snapshot() },
        ...children.map((c) => ({ breakdown: c.name.slice(`${statName}:`.length), ...c.snap })),
      ];

      console.debug(`${LOG_PREFIX} ${label}`);
      console.table(rows);
    } catch (_) {}
  }, everyMs);

  return () => window.clearInterval(id);
}

export function initDevPerfLogging(): void {
  if (!isDevPerfEnabled()) return;
  if (window.__ZHIXU_DEV_PERF__?.enabled) return;

  window.__ZHIXU_DEV_PERF__ = { enabled: true, startedAtMs: performance.now(), flags: {} };
  devPerfMark("zhixu:boot:init");

  installDevConsoleFilters();

  try {
    console.log(`${LOG_PREFIX} enabled (mode=${import.meta.env.MODE})`);
  } catch (_) {}

  installLongTaskObserver();
  installWebVitalsObservers();

  try {
    window.addEventListener(
      "load",
      () => {
        requestAnimationFrame(() => {
          requestAnimationFrame(() => logStartupSummary("window:load"));
        });
      },
      { once: true },
    );
  } catch (_) {}
}

export function runDevPerfOnce(key: string, fn: () => void): void {
  if (!isDevPerfEnabled()) return;
  const state = window.__ZHIXU_DEV_PERF__;
  if (!state) {
    fn();
    return;
  }
  if (!state.flags) state.flags = {};
  if (state.flags[key]) return;
  state.flags[key] = true;
  fn();
}

export function logStartupSummary(context?: string, extra?: Record<string, unknown>): void {
  if (!isDevPerfEnabled()) return;

  const nav = performance.getEntriesByType("navigation")[0] as PerformanceNavigationTiming | undefined;
  const paints = performance.getEntriesByType("paint");
  const paintTimes: Record<string, number> = {};
  for (const p of paints) paintTimes[p.name] = round1(p.startTime);

  const appMounted = lastMarkStartTime("zhixu:app:mounted");
  const appLayout = lastMarkStartTime("zhixu:app:layout");
  const reactRenderCall = lastMarkStartTime("zhixu:react:render-call");
  const shellVisible = lastMarkStartTime("shell:visible");
  const bootInit = lastMarkStartTime("zhixu:boot:init");
  const vitals = vitalsState();

  const base: Record<string, unknown> = {
    ...(context ? { context } : {}),
    ...(shellVisible != null ? { shellVisibleMs: round1(shellVisible) } : {}),
    ...(bootInit != null ? { bootInitMs: round1(bootInit) } : {}),
    ...(reactRenderCall != null ? { reactRenderCallMs: round1(reactRenderCall) } : {}),
    ...(appLayout != null ? { appLayoutMs: round1(appLayout) } : {}),
    ...(appMounted != null ? { appMountedMs: round1(appMounted) } : {}),
    ...(typeof vitals.lcpMs === "number" ? { lcpMs: round1(vitals.lcpMs) } : {}),
    ...(typeof vitals.clsTotal === "number" ? { clsTotal: round1(vitals.clsTotal) } : {}),
    ...paintTimes,
  };

  if (nav) {
    base.domInteractiveMs = round1(nav.domInteractive);
    base.domContentLoadedMs = round1(nav.domContentLoadedEventEnd);
    base.loadEventMs = round1(nav.loadEventEnd);
    base.transferSize = nav.transferSize;
  }

  try {
    console.groupCollapsed(`${LOG_PREFIX} startup${context ? ` (${context})` : ""}`);
    try {
      console.table(collectStartupTimelineRows());
    } catch (_) {}
    console.log(base);
    if (extra) console.debug(`${LOG_PREFIX} extra`, extra);
    console.groupEnd();
  } catch (_) {
    console.log(`${LOG_PREFIX} startup`, base);
  }
}

type StartupTimelineRow = {
  tMs: number;
  deltaMs: number | null;
  durMs: number | null;
  name: string;
  details?: string;
};

function collectStartupTimelineRows(): StartupTimelineRow[] {
  const nowMs = performance.now();
  const items: Array<{ tMs: number; durMs?: number; name: string; details?: string }> = [];

  items.push({ tMs: 0, name: "nav:navigationStart" });

  try {
    const nav = performance.getEntriesByType("navigation")[0] as PerformanceNavigationTiming | undefined;
    if (nav) {
      if (nav.responseEnd > 0) items.push({ tMs: nav.responseEnd, name: "nav:responseEnd" });
      if (nav.domInteractive > 0) items.push({ tMs: nav.domInteractive, name: "nav:domInteractive" });
      if (nav.domContentLoadedEventEnd > 0) items.push({ tMs: nav.domContentLoadedEventEnd, name: "nav:domContentLoaded" });
      if (nav.loadEventEnd > 0) items.push({ tMs: nav.loadEventEnd, name: "nav:loadEventEnd" });
    }
  } catch (_) {}

  try {
    const paints = performance.getEntriesByType("paint");
    for (const p of paints) {
      if (!p || p.startTime <= 0) continue;
      items.push({ tMs: p.startTime, name: `paint:${p.name}` });
    }
  } catch (_) {}

  try {
    const shellVisible = lastMarkStartTime("shell:visible");
    if (shellVisible != null && shellVisible > 0 && shellVisible <= nowMs) items.push({ tMs: shellVisible, name: "mark:shell:visible" });
  } catch (_) {}

  try {
    const lcpMs = vitalsState().lcpMs;
    if (typeof lcpMs === "number" && lcpMs > 0 && lcpMs <= nowMs) items.push({ tMs: lcpMs, name: "vitals:lcp" });
  } catch (_) {}

  try {
    for (const m of marksList()) {
      if (!m || !m.name) continue;
      if (!m.name.startsWith("zhixu:")) continue;
      if (m.tMs <= 0 || m.tMs > nowMs) continue;
      items.push({ tMs: m.tMs, name: `mark:${m.name}` });
    }
  } catch (_) {}

  try {
    const spans = spansList();
    for (const s of spans) {
      if (!s || s.startMs <= 0 || s.startMs > nowMs) continue;
      const details = summarizeDetails(s.details);
      items.push({ tMs: s.startMs, durMs: s.durationMs, name: `span:${s.name}`, ...(details ? { details } : {}) });
    }
  } catch (_) {}

  items.sort((a, b) => a.tMs - b.tMs);

  const out: StartupTimelineRow[] = [];
  let prev: number | null = null;
  for (const it of items) {
    const tMs = it.tMs;
    const deltaMs = prev == null ? null : tMs - prev;
    prev = tMs;
    out.push({
      tMs: round1(tMs),
      deltaMs: deltaMs == null ? null : round1(deltaMs),
      durMs: it.durMs == null ? null : round1(it.durMs),
      name: it.name,
      details: it.details,
    });
  }
  return out;
}

function summarizeDetails(details?: Record<string, unknown>): string | undefined {
  if (!details) return undefined;
  const parts: string[] = [];

  const ctx = details.ctx;
  if (typeof ctx === "string" && ctx.trim()) parts.push(`ctx=${ctx.trim()}`);

  const mode = details.mode;
  if (typeof mode === "string" && mode.trim()) parts.push(`mode=${mode.trim()}`);

  const dir = details.dir;
  if (typeof dir === "string" && dir.trim()) parts.push(`dir=${dir.trim()}`);

  const path = details.path;
  if (typeof path === "string" && path.trim()) parts.push(`path=${path.trim()}`);

  const chars = details.chars;
  if (typeof chars === "number" && Number.isFinite(chars)) parts.push(`chars=${chars}`);

  return parts.length ? parts.join(" ") : undefined;
}

function lastMarkStartTime(name: string): number | null {
  try {
    const entries = performance.getEntriesByName(name, "mark");
    if (!entries.length) return null;
    return entries[entries.length - 1]!.startTime;
  } catch (_) {
    return null;
  }
}

function installLongTaskObserver(): void {
  try {
    const obs = new PerformanceObserver((list) => {
      for (const e of list.getEntries()) {
        if (e.duration < 80) continue;
        try {
          console.debug(`${LOG_PREFIX} longtask ${round1(e.duration)}ms`, { startTimeMs: round1(e.startTime), name: e.name });
        } catch (_) {}
      }
    });
    (obs as unknown as { observe: (options: unknown) => void }).observe({ type: "longtask", buffered: true });
  } catch (_) {
    // Ignore if unsupported.
  }
}

function installWebVitalsObservers(): void {
  if (!isDevPerfEnabled()) return;
  // LCP & CLS are useful when profiling "startup" in renderer.
  // If unsupported, ignore silently.
  try {
    const lcp = new PerformanceObserver((list) => {
      const entries = list.getEntries();
      const last = entries[entries.length - 1];
      if (!last) return;
      if (last.startTime < 0) return;
      if (last.startTime > 0) {
        vitalsState().lcpMs = last.startTime;
        getRollingStats("startup:lcp").add(last.startTime);
      }
    });
    (lcp as unknown as { observe: (options: unknown) => void }).observe({ type: "largest-contentful-paint", buffered: true });
  } catch (_) {}

  try {
    const cls = new PerformanceObserver((list) => {
      for (const e of list.getEntries() as Array<{ value?: number; hadRecentInput?: boolean }>) {
        if (e.hadRecentInput) continue;
        if (typeof e.value === "number" && Number.isFinite(e.value)) {
          const vitals = vitalsState();
          vitals.clsTotal = (vitals.clsTotal ?? 0) + e.value;
          getRollingStats("startup:cls").add(e.value);
        }
      }
    });
    (cls as unknown as { observe: (options: unknown) => void }).observe({ type: "layout-shift", buffered: true });
  } catch (_) {}
}

function percentileSnapshot(values: number[]): Percentiles {
  if (!values.length) return { p50Ms: null, p95Ms: null };
  const sorted = [...values].sort((a, b) => a - b);
  return {
    p50Ms: round1(percentile(sorted, 0.5)),
    p95Ms: round1(percentile(sorted, 0.95)),
  };
}

function percentile(sorted: number[], p: number): number {
  if (!sorted.length) return 0;
  const clamped = Math.max(0, Math.min(1, p));
  const idx = clamped * (sorted.length - 1);
  const lo = Math.floor(idx);
  const hi = Math.ceil(idx);
  if (lo === hi) return sorted[lo]!;
  const w = idx - lo;
  return sorted[lo]! * (1 - w) + sorted[hi]! * w;
}

function round1(n: number): number {
  return Math.round(n * 10) / 10;
}
