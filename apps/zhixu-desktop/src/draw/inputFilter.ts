import type { DrawPoint } from "./types";

type OneEuroOptions = {
  minCutoff: number;
  beta: number;
  dCutoff: number;
};

function clamp01(v: number): number {
  if (!Number.isFinite(v)) return 0;
  return Math.min(1, Math.max(0, v));
}

function alphaForCutoff(cutoffHz: number, dtSec: number): number {
  const hz = Math.max(0.0001, cutoffHz);
  const dt = Math.max(0.000001, dtSec);
  const tau = 1 / (2 * Math.PI * hz);
  return 1 / (1 + tau / dt);
}

class LowPassFilter {
  private _value: number | null = null;

  reset(value: number) {
    this._value = value;
  }

  filter(value: number, alpha: number): number {
    const a = clamp01(alpha);
    const prev = this._value;
    if (prev == null) {
      this._value = value;
      return value;
    }
    const next = prev + (value - prev) * a;
    this._value = next;
    return next;
  }

  get value(): number | null {
    return this._value;
  }
}

export class OneEuroFilter1D {
  private readonly opts: OneEuroOptions;
  private readonly xFilter = new LowPassFilter();
  private readonly dxFilter = new LowPassFilter();
  private prevTimeSec: number | null = null;
  private prevRaw: number | null = null;

  constructor(opts: OneEuroOptions) {
    this.opts = opts;
  }

  reset(value: number, timeSec: number) {
    this.prevTimeSec = timeSec;
    this.prevRaw = value;
    this.xFilter.reset(value);
    this.dxFilter.reset(0);
  }

  filter(value: number, timeSec: number): number {
    if (this.prevTimeSec == null || this.prevRaw == null) {
      this.reset(value, timeSec);
      return value;
    }

    const dt = Math.max(0.000001, timeSec - this.prevTimeSec);
    const dx = (value - this.prevRaw) / dt;
    const edx = this.dxFilter.filter(dx, alphaForCutoff(this.opts.dCutoff, dt));
    const cutoff = Math.max(0.0001, this.opts.minCutoff + this.opts.beta * Math.abs(edx));
    const out = this.xFilter.filter(value, alphaForCutoff(cutoff, dt));

    this.prevTimeSec = timeSec;
    this.prevRaw = value;
    return out;
  }
}

export class OneEuroFilter2D {
  private readonly opts: OneEuroOptions;
  private readonly xFilter = new LowPassFilter();
  private readonly yFilter = new LowPassFilter();
  private readonly dxFilter = new LowPassFilter();
  private readonly dyFilter = new LowPassFilter();
  private prevTimeSec: number | null = null;
  private prevRaw: DrawPoint | null = null;

  constructor(opts: OneEuroOptions) {
    this.opts = opts;
  }

  reset(point: DrawPoint, timeSec: number) {
    this.prevTimeSec = timeSec;
    this.prevRaw = point;
    this.xFilter.reset(point[0]);
    this.yFilter.reset(point[1]);
    this.dxFilter.reset(0);
    this.dyFilter.reset(0);
  }

  filter(point: DrawPoint, timeSec: number): DrawPoint {
    if (this.prevTimeSec == null || this.prevRaw == null) {
      this.reset(point, timeSec);
      return point;
    }

    const dt = Math.max(0.000001, timeSec - this.prevTimeSec);
    const dx = (point[0] - this.prevRaw[0]) / dt;
    const dy = (point[1] - this.prevRaw[1]) / dt;

    const ad = alphaForCutoff(this.opts.dCutoff, dt);
    const edx = this.dxFilter.filter(dx, ad);
    const edy = this.dyFilter.filter(dy, ad);

    const speed = Math.hypot(edx, edy);
    const cutoff = Math.max(0.0001, this.opts.minCutoff + this.opts.beta * speed);
    const a = alphaForCutoff(cutoff, dt);

    const x = this.xFilter.filter(point[0], a);
    const y = this.yFilter.filter(point[1], a);

    this.prevTimeSec = timeSec;
    this.prevRaw = point;
    return [x, y];
  }
}

