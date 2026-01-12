import { installDevConsoleFilters } from "./devConsoleFilters";

type Percentiles = {
  p50Ms: number | null;
  p95Ms: number | null;
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
};

declare global {
  interface Window {
    __ZHIXU_DEV_PERF__?: { enabled: true; startedAtMs: number; flags?: Record<string, true> };
    __ZHIXU_DEV_PERF_STATS__?: Map<string, RollingStats>;
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
    performance.mark(name);
  } catch (_) {}
}

export function recordDurationMs(statName: string, ms: number, details?: Record<string, unknown>): void {
  if (!isDevPerfEnabled()) return;
  const stats = getRollingStats(statName);
  stats.add(ms);

  // Avoid console spam: only log individual outliers.
  if (ms >= 80) {
    try {
      console.debug(`${LOG_PREFIX} slow ${statName} ${round1(ms)}ms`, details ?? {});
    } catch (_) {}
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

  let lastCount = getRollingStats(statName).count;
  const id = window.setInterval(() => {
    const stats = getRollingStats(statName);
    const delta = stats.count - lastCount;
    if (delta < minNewSamples) return;
    lastCount = stats.count;
    try {
      console.debug(`${LOG_PREFIX} ${label}`, stats.snapshot());
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
        logStartupSummary("window:load");
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
  const bootInit = lastMarkStartTime("zhixu:boot:init");

  const base: Record<string, unknown> = {
    ...(context ? { context } : {}),
    ...(bootInit != null ? { bootInitMs: round1(bootInit) } : {}),
    ...(appMounted != null ? { appMountedMs: round1(appMounted) } : {}),
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
    console.log(base);
    if (extra) console.debug(`${LOG_PREFIX} extra`, extra);
    console.groupEnd();
  } catch (_) {
    console.log(`${LOG_PREFIX} startup`, base);
  }
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
      if (last.startTime > 0) getRollingStats("startup:lcp").add(last.startTime);
    });
    (lcp as unknown as { observe: (options: unknown) => void }).observe({ type: "largest-contentful-paint", buffered: true });
  } catch (_) {}

  try {
    const cls = new PerformanceObserver((list) => {
      for (const e of list.getEntries() as Array<{ value?: number; hadRecentInput?: boolean }>) {
        if (e.hadRecentInput) continue;
        if (typeof e.value === "number") getRollingStats("startup:cls").add(e.value);
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
