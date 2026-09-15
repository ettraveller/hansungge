'use client';

import { useId, useMemo, useState } from 'react';

import type { PriceHistoryPoint } from '@/types/price';
import styles from './PriceChart.module.css';

interface Props {
  points: PriceHistoryPoint[];
  /** 접근성 설명. "순금 24K 내가 살 때 최근 1개월 추이" 처럼 무엇의 그래프인지 적는다 */
  label: string;
  /**
   * 그래프가 덮는 구간(ISO-8601). 주면 가로축이 이 구간에 맞춰지고, 마지막 고시값이
   * {@code until} 까지 이어진다. 주지 않으면 첫 고시부터 마지막 고시까지다.
   */
  since?: string;
  until?: string;
}

/** 그리기 영역. 축 글자가 들어갈 자리를 먼저 빼 둔다 — 안 그러면 뷰박스 밖으로 잘린다. */
interface Box {
  w: number;
  h: number;
  pad: { top: number; right: number; bottom: number; left: number };
  /** 좁은 화면용. 눈금 글자를 솎고, 오른쪽 현재값 표시를 뺀다 */
  compact: boolean;
}

/** 오른쪽 여백은 현재값 표시가 들어갈 자리다. */
const DESKTOP: Box = {
  w: 900,
  h: 320,
  pad: { top: 22, right: 84, bottom: 34, left: 70 },
  compact: false,
};

/**
 * 휴대폰용 뷰박스.
 *
 * <p>가로를 900 → 400 으로 줄인 것이 요점이다. 아래 {@code PriceChart} 설명 참고.
 * 세로 비율은 조금 높다 — 폭이 좁아진 만큼 눌러 놓으면 등락이 안 보인다.
 */
const MOBILE: Box = {
  w: 400,
  h: 290,
  pad: { top: 20, right: 14, bottom: 30, left: 56 },
  compact: true,
};

const HOUR = 60 * 60 * 1000;
const DAY = 24 * HOUR;

/**
 * 한국 표준시 오프셋. 서머타임이 없어 고정값으로 충분하다.
 *
 * <p>날짜 경계(자정·월초)를 이 값으로 긋는다. 서버와 브라우저의 시간대가 달라도 같은 눈금이
 * 나와야 서버가 그린 그림과 브라우저가 이어받은 그림이 어긋나지 않는다.
 */
const KST_OFFSET = 9 * HOUR;

const WEEKDAY = ['일', '월', '화', '수', '목', '금', '토'];

/** 고시 지점에 점을 찍는 한도. 1년치처럼 고시가 많으면 점이 선을 덮는다. */
const MARKER_LIMIT = 48;

/** 한 번의 고시가 유지된 구간의 시작. */
interface Step {
  /** 그래프 위의 시작 시각. 기간 전에 고시된 값이면 기간 시작으로 당겨 놓는다 */
  t: number;
  /** 원/g */
  v: number;
  /** 원/돈 — 서버가 준 값 그대로다 */
  don: number;
  /** 실제 고시 시각 */
  at: number;
  /** 기간이 시작하기 전에 고시돼 기간 첫머리에 적용 중이던 값인가 */
  carried: boolean;
}

interface Series {
  t0: number;
  tEnd: number;
  steps: Step[];
}

/**
 * 시세 추이 그래프.
 *
 * <h2>계단 모양으로 그린다</h2>
 * 고시한 값은 <b>다음 고시까지 그대로</b> 적용된다. 두 고시를 사선으로 이으면 그 사이에 값이
 * 조금씩 오른 것처럼 보이는데, 실제로는 한 값으로 팔다가 고시 순간에 바뀐다. 그래서 수평으로
 * 유지하다 고시 시점에 세로로 꺾는다.
 *
 * <h2>눈금은 기간에 맞춘다</h2>
 * 1일은 몇 시에 바뀌었는지, 1주는 무슨 요일에 바뀌었는지가 궁금하다. 양 끝 날짜만 적어서는
 * 둘 다 답하지 못한다. 기간에 따라 시각·날짜·주·반달·달 단위로 세로 눈금을 긋고, 1주에서는
 * 하루씩 번갈아 옅게 칠해 날짜 칸을 셀 수 있게 한다.
 *
 * <h2>짚으면 그 순간의 값이 나온다</h2>
 * 마우스를 올리거나 손가락으로 누르면 그 시점에 적용 중이던 고시의 시각과 1g·1돈 값을
 * 보여준다. 그래서 클라이언트 컴포넌트다. 클라이언트 컴포넌트도 서버에서 한 번 그려져
 * <b>초기 HTML 에 SVG 가 실린다</b> — 자바스크립트가 꺼져 있어도 그림은 있고, 짚기만 안 된다.
 *
 * <h2>뷰박스를 둘 그린다</h2>
 * SVG 는 뷰박스째로 줄어든다. 가로 900 짜리 그림이 휴대폰에서 340px 로 들어가면 축 글자도
 * 0.38배가 되어 읽히지 않는다. CSS 로 글자만 키우는 것은 같은 배율에 걸려 소용이 없다.
 * 그래서 데스크톱용과 휴대폰용을 둘 다 그려 두고 CSS 로 하나만 보인다. 화면 폭을 재서 고르면
 * 서버가 그린 그림과 브라우저의 첫 그림이 달라져 깜빡인다.
 *
 * <p>값은 서버가 준 것을 그리기만 한다. 1돈 값도 곱하지 않고 받은 값을 쓴다 (원칙 P1).
 */
export function PriceChart({ points, label, since, until }: Props) {
  const series = useMemo(() => buildSeries(points, since, until), [points, since, until]);

  if (series == null) {
    return <p className={styles.empty}>이 기간에 고시된 시세가 없습니다.</p>;
  }

  // 구간이 없으면 점 하나로는 선을 그릴 수 없다. 구간이 있으면 그 값이 지금까지
  // 이어지는 수평선이 되므로 그린다.
  if (points.length === 1 && (since == null || until == null)) {
    return <p className={styles.empty}>이 기간의 고시가 1건뿐이라 추이를 그릴 수 없습니다.</p>;
  }

  return (
    <>
      <Plot series={series} label={label} box={DESKTOP} className={styles.wide} />
      <Plot series={series} label={label} box={MOBILE} className={styles.narrow} />
    </>
  );
}

/** 한 벌의 그림. 뷰박스 치수만 다르고 그리는 규칙은 같다. */
function Plot({
  series,
  label,
  box,
  className,
}: {
  series: Series;
  label: string;
  box: Box;
  className: string;
}) {
  const { w: W, h: H, pad: PAD, compact } = box;
  const { t0, tEnd, steps } = series;

  const [hover, setHover] = useState<{ x: number; index: number } | null>(null);
  // 같은 화면에 그림이 둘이라 그라데이션 id 가 겹치면 한쪽이 다른 쪽 것을 칠한다.
  const gradientId = `price-area-${useId().replace(/[^a-zA-Z0-9]/g, '')}`;

  const plotW = W - PAD.left - PAD.right;
  const plotH = H - PAD.top - PAD.bottom;
  const plotRight = PAD.left + plotW;
  const baseline = PAD.top + plotH;

  const values = steps.map((s) => s.v);
  const lo = Math.min(...values);
  const hi = Math.max(...values);
  // 위아래로 폭의 15% 를 비워 최고·최저 표시가 들어갈 자리를 둔다.
  // 값이 전부 같으면 폭이 0 이라 나눗셈이 깨지므로 그때만 값의 1% 를 폭으로 삼는다.
  const room = (hi - lo) * 0.15 || Math.max(hi * 0.01, 1);
  const min = lo - room;
  const max = hi + room;

  const tSpan = tEnd - t0 || 1;
  const x = (t: number) => PAD.left + ((t - t0) / tSpan) * plotW;
  const y = (v: number) => PAD.top + plotH - ((v - min) / (max - min)) * plotH;

  // 계단: 수평으로 유지하다 고시 시점에 세로로 꺾는다. 마지막 값은 구간 끝까지 늘인다.
  let line = `M${f(x(steps[0].t))} ${f(y(steps[0].v))}`;
  for (let i = 1; i < steps.length; i += 1) {
    line += ` H${f(x(steps[i].t))} V${f(y(steps[i].v))}`;
  }
  line += ` H${f(x(tEnd))}`;
  const area = `${line} V${f(baseline)} H${f(x(steps[0].t))} Z`;

  const yTicks = niceTicks(min, max, compact ? 4 : 5);
  const { ticks: xTicks, dailyBands } = timeTicks(t0, tEnd, compact);
  // 눈금선은 다 긋고 글자만 솎는다. 좁은 화면에서 글자가 겹치면 아무것도 읽히지 않는다.
  const labeled = xTicks.filter((tick) => tick.label !== '');
  const labelEvery = Math.max(1, Math.ceil(labeled.length / (compact ? 4 : 12)));
  const shownLabels = new Set(labeled.filter((_, i) => i % labelEvery === 0).map((tick) => tick.t));
  const bands = dailyBands ? alternateDays(t0, tEnd) : [];

  const last = steps[steps.length - 1];
  const markers = steps.filter((s) => !s.carried);
  const showMarkers = markers.length <= MARKER_LIMIT;

  // 최고·최저는 그 값이 가장 오래 유지된 구간의 가운데에 적는다. 같은 값을 여러 번 고시하면
  // 첫 번째 짧은 구간에 붙어, 정작 길게 유지된 구간과 떨어져 보인다.
  const longestWith = (value: number) => {
    let best = 0;
    let bestLength = -1;
    steps.forEach((s, i) => {
      const length = (steps[i + 1]?.t ?? tEnd) - s.t;
      if (s.v === value && length > bestLength) {
        best = i;
        bestLength = length;
      }
    });
    return best;
  };
  const hiIndex = longestWith(hi);
  const loIndex = longestWith(lo);
  const middleOf = (i: number) => x((steps[i].t + (steps[i + 1]?.t ?? tEnd)) / 2);

  const edgeAnchor = (px: number): 'start' | 'middle' | 'end' =>
    px < PAD.left + 36 ? 'start' : px > plotRight - 36 ? 'end' : 'middle';

  const onPoint = (e: React.PointerEvent<SVGRectElement>) => {
    const rect = e.currentTarget.ownerSVGElement?.getBoundingClientRect();
    if (!rect || rect.width === 0) return;
    const px = Math.min(Math.max(((e.clientX - rect.left) / rect.width) * W, PAD.left), plotRight);
    const t = t0 + ((px - PAD.left) / plotW) * tSpan;
    let index = 0;
    for (let i = 0; i < steps.length; i += 1) {
      if (steps[i].t <= t) index = i;
      else break;
    }
    setHover({ x: px, index });
  };

  const hovered = hover == null ? null : steps[hover.index];

  return (
    <svg
      className={`${styles.chart} ${className}`}
      viewBox={`0 0 ${W} ${H}`}
      role="img"
      aria-label={label}
    >
      <defs>
        <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" className={styles.areaTop} />
          <stop offset="100%" className={styles.areaBottom} />
        </linearGradient>
      </defs>

      {bands.map((b) => (
        <rect
          key={b.from}
          className={styles.band}
          x={x(b.from)}
          y={PAD.top}
          width={Math.max(0, x(b.to) - x(b.from))}
          height={plotH}
        />
      ))}

      {yTicks.map((v) => (
        <g key={v}>
          <line className={styles.grid} x1={PAD.left} y1={y(v)} x2={plotRight} y2={y(v)} />
          <text className={styles.axis} x={PAD.left - 10} y={y(v) + 4} textAnchor="end">
            {won(v)}
          </text>
        </g>
      ))}

      {xTicks.map((tick) => (
        <g key={`${tick.grid ? 'line' : 'label'}-${tick.t}`}>
          {tick.grid && (
            <line
              className={tick.major ? styles.gridXMajor : styles.gridX}
              x1={x(tick.t)}
              y1={PAD.top}
              x2={x(tick.t)}
              y2={baseline}
            />
          )}
          {shownLabels.has(tick.t) && (
            <text
              className={tick.major ? styles.axisMajor : styles.axis}
              x={x(tick.t)}
              y={baseline + 20}
              textAnchor={edgeAnchor(x(tick.t))}
            >
              {tick.label}
            </text>
          )}
        </g>
      ))}

      <path className={styles.area} d={area} fill={`url(#${gradientId})`} />
      <line className={styles.guide} x1={PAD.left} y1={y(last.v)} x2={plotRight} y2={y(last.v)} />
      <path className={styles.line} d={line} />

      {showMarkers &&
        markers.map((s) => (
          <circle
            key={s.at}
            className={styles.marker}
            cx={x(s.t)}
            cy={y(s.v)}
            r={compact ? 2.5 : 3}
          />
        ))}

      {hi > lo && (
        <>
          <text
            className={styles.extreme}
            x={middleOf(hiIndex)}
            y={y(hi) - 10}
            textAnchor={edgeAnchor(middleOf(hiIndex))}
          >
            최고 {won(hi)}
          </text>
          <text
            className={styles.extreme}
            x={middleOf(loIndex)}
            y={y(lo) + 20}
            textAnchor={edgeAnchor(middleOf(loIndex))}
          >
            최저 {won(lo)}
          </text>
        </>
      )}

      {/* 지금 값. 이 그림에서 가장 먼저 찾는 점이라 맥박을 준다 */}
      <circle className={styles.pulse} cx={x(tEnd)} cy={y(last.v)} r={9} />
      <circle className={styles.dot} cx={x(tEnd)} cy={y(last.v)} r={5} />
      {!compact && (
        <g transform={`translate(${plotRight + 8} ${f(y(last.v) - 12)})`}>
          <rect className={styles.pill} width={PAD.right - 12} height={24} rx={6} />
          <text className={styles.pillText} x={(PAD.right - 12) / 2} y={16} textAnchor="middle">
            {won(last.v)}
          </text>
        </g>
      )}

      {hover && hovered && (
        <Tooltip
          px={hover.x}
          py={y(hovered.v)}
          step={hovered}
          top={PAD.top}
          bottom={baseline}
          left={PAD.left}
          right={plotRight}
          compact={compact}
        />
      )}

      {/* 짚는 자리. 그림보다 위에 둬야 선과 점이 이벤트를 가로채지 않는다.
          세로 스크롤은 막지 않는다 — 휴대폰에서 그래프를 지나 내려가다 멈추면 안 된다 */}
      <rect
        className={styles.overlay}
        x={PAD.left}
        y={PAD.top}
        width={plotW}
        height={plotH}
        onPointerMove={onPoint}
        onPointerDown={onPoint}
        onPointerLeave={() => setHover(null)}
      />
    </svg>
  );
}

/** 짚은 순간의 값. 그 시점에 적용 중이던 고시를 보여준다. */
function Tooltip({
  px,
  py,
  step,
  top,
  bottom,
  left,
  right,
  compact,
}: {
  px: number;
  py: number;
  step: Step;
  top: number;
  bottom: number;
  left: number;
  right: number;
  compact: boolean;
}) {
  const width = compact ? 178 : 200;
  const height = 64;

  // 오른쪽에 자리가 없으면 왼쪽으로 뒤집는다. 그래도 넘치면 그림 안쪽에 붙인다.
  let tx = px + 14;
  if (tx + width > right) tx = px - 14 - width;
  tx = Math.max(left, Math.min(tx, right - width));
  const ty = top + 6;

  return (
    <g className={styles.hover} pointerEvents="none">
      <line className={styles.hoverLine} x1={px} y1={top} x2={px} y2={bottom} />
      <circle className={styles.hoverDot} cx={px} cy={py} r={4.5} />
      <g transform={`translate(${f(tx)} ${ty})`}>
        <rect className={styles.tip} width={width} height={height} rx={8} />
        <text className={styles.tipTime} x={12} y={20}>
          {formatQuotedAt(step.at)} 고시{step.carried ? ' · 기간 전' : ''}
        </text>
        <text className={styles.tipValue} x={12} y={40}>
          {won(step.v)}
          <tspan className={styles.tipUnit}> 원/g</tspan>
        </text>
        <text className={styles.tipSub} x={12} y={56}>
          1돈 {won(step.don)}원
        </text>
      </g>
    </g>
  );
}

// =====================================================================
// 계산 — 그리기에 필요한 좌표와 눈금만 만든다. 금액은 받은 값을 그대로 쓴다
// =====================================================================

function buildSeries(points: PriceHistoryPoint[], since?: string, until?: string): Series | null {
  if (points.length === 0) return null;

  const firstAt = Date.parse(points[0].effectiveFrom);
  const lastAt = Date.parse(points[points.length - 1].effectiveFrom);

  // 가로축 구간. 구간이 주어지면 그것을, 아니면 첫 고시~마지막 고시를 쓴다.
  // 기간 안에 고시가 하나도 없고 기간 전 값만 있어도 시작은 기간 시작이다 — 옛 고시 시각으로
  // 당기면 "1일" 그래프가 며칠짜리 그림이 된다. 기간 전 값은 아래에서 시작점으로 당겨 놓는다.
  const t0 = since != null ? Date.parse(since) : firstAt;
  const tEnd = until != null ? Math.max(Date.parse(until), lastAt) : lastAt;

  const steps = points.map((p) => {
    const at = Date.parse(p.effectiveFrom);
    return { t: Math.max(at, t0), v: p.pricePerGram, don: p.pricePerDon, at, carried: at < t0 };
  });
  return { t0, tEnd, steps };
}

/** 세로축 눈금 — 1·2·2.5·5 배수의 보기 좋은 값으로 떨어뜨린다. */
function niceTicks(min: number, max: number, count: number): number[] {
  const raw = (max - min) / (count - 1);
  if (!(raw > 0)) return [min];
  const magnitude = 10 ** Math.floor(Math.log10(raw));
  const step = [1, 2, 2.5, 5, 10].map((m) => m * magnitude).find((s) => s >= raw) ?? 10 * magnitude;
  const ticks: number[] = [];
  for (let v = Math.ceil(min / step) * step; v <= max + step * 1e-9; v += step) {
    ticks.push(Math.round(v * 100) / 100);
  }
  return ticks;
}

interface Tick {
  t: number;
  /** 빈 문자열이면 글자 없이 선만 긋는다 */
  label: string;
  /** 진하게 긋고 굵게 적는 눈금 — 자정(1일), 월요일(1주), 월초(3개월), 연초(1년) */
  major: boolean;
  /** 세로선을 긋는가. 1주 그래프의 날짜 글자는 선이 아니라 그날 칸의 가운데에 놓인다 */
  grid: boolean;
}

/**
 * 가로축 눈금. 기간이 짧을수록 촘촘한 단위를 쓴다.
 *
 * <pre>
 * ~2일     3시간(좁은 화면 6시간)   "15시", 자정은 "9/15"
 * ~10일    하루                    선은 자정, 글자는 그날 칸 가운데 "9/15(월)" · 하루씩 번갈아 칠함
 * ~45일    주(월요일)              "9/15"
 * ~120일   반달(1일·15일)          "9/1"
 * 그 이상  달                      "9월", 1월은 "26년 1월"
 * </pre>
 */
function timeTicks(t0: number, tEnd: number, compact: boolean): { ticks: Tick[]; dailyBands: boolean } {
  const span = tEnd - t0;
  const ticks: Tick[] = [];

  if (span <= 2 * DAY) {
    const stepMs = (compact ? 6 : 3) * HOUR;
    for (let t = Math.ceil((t0 + KST_OFFSET) / stepMs) * stepMs - KST_OFFSET; t <= tEnd; t += stepMs) {
      const d = kst(t);
      const midnight = d.getUTCHours() === 0;
      ticks.push({ t, label: midnight ? monthDay(d) : `${d.getUTCHours()}시`, major: midnight, grid: true });
    }
    return { ticks, dailyBands: false };
  }

  if (span <= 10 * DAY) {
    for (let t = nextKstMidnight(t0); t <= tEnd; t += DAY) {
      ticks.push({ t, label: '', major: kst(t).getUTCDay() === 1, grid: true });
    }
    // 날짜는 그날 칸의 가운데에 적는다. 자정 선 위에 적으면 글자가 두 날의 경계에 걸려
    // 띠 하나가 어느 날인지 읽기 어렵다. 기간 양 끝에 반쯤 걸친 날은 칸이 6시간은 돼야 적는다.
    for (let day = nextKstMidnight(t0) - DAY; day < tEnd; day += DAY) {
      const from = Math.max(day, t0);
      const to = Math.min(day + DAY, tEnd);
      if (to - from < 6 * HOUR) continue;
      const d = kst(day);
      ticks.push({
        t: (from + to) / 2,
        label: compact ? monthDay(d) : `${monthDay(d)}(${WEEKDAY[d.getUTCDay()]})`,
        major: d.getUTCDay() === 1,
        grid: false,
      });
    }
    return { ticks, dailyBands: true };
  }

  if (span <= 45 * DAY) {
    let t = nextKstMidnight(t0);
    t += ((8 - kst(t).getUTCDay()) % 7) * DAY;
    for (; t <= tEnd; t += 7 * DAY) {
      ticks.push({ t, label: monthDay(kst(t)), major: false, grid: true });
    }
    return { ticks, dailyBands: false };
  }

  const halfMonth = span <= 120 * DAY;
  const start = kst(t0);
  let year = start.getUTCFullYear();
  let month = start.getUTCMonth();
  for (let guard = 0; guard < 36; guard += 1) {
    for (const day of halfMonth ? [1, 15] : [1]) {
      const t = Date.UTC(year, month, day) - KST_OFFSET;
      if (t >= t0 && t <= tEnd) {
        const label = halfMonth
          ? `${month + 1}/${day}`
          : month === 0
            ? `${String(year).slice(2)}년 1월`
            : `${month + 1}월`;
        ticks.push({ t, label, major: halfMonth ? day === 1 : month === 0, grid: true });
      }
    }
    month += 1;
    if (month === 12) {
      month = 0;
      year += 1;
    }
    if (Date.UTC(year, month, 1) - KST_OFFSET > tEnd) break;
  }
  return { ticks, dailyBands: false };
}

/** 하루씩 번갈아 칠할 구간. 기간 첫날부터 홀수 번째 날만 칠한다. */
function alternateDays(t0: number, tEnd: number): { from: number; to: number }[] {
  const bands: { from: number; to: number }[] = [];
  let index = 0;
  for (let day = nextKstMidnight(t0) - DAY; day < tEnd; day += DAY, index += 1) {
    if (index % 2 === 1) {
      bands.push({ from: Math.max(day, t0), to: Math.min(day + DAY, tEnd) });
    }
  }
  return bands;
}

/** t 이후(같으면 t)의 첫 한국 자정. */
function nextKstMidnight(t: number): number {
  return Math.ceil((t + KST_OFFSET) / DAY) * DAY - KST_OFFSET;
}

/** 한국 시각의 연·월·일·시를 getUTC* 로 읽을 수 있게 옮긴 Date. */
function kst(t: number): Date {
  return new Date(t + KST_OFFSET);
}

function monthDay(d: Date): string {
  return `${d.getUTCMonth() + 1}/${d.getUTCDate()}`;
}

/** "9월 15일(월) 14:30" — 한국 시각 */
function formatQuotedAt(t: number): string {
  const d = kst(t);
  const hh = String(d.getUTCHours()).padStart(2, '0');
  const mm = String(d.getUTCMinutes()).padStart(2, '0');
  return `${d.getUTCMonth() + 1}월 ${d.getUTCDate()}일(${WEEKDAY[d.getUTCDay()]}) ${hh}:${mm}`;
}

function won(v: number): string {
  return Math.round(v).toLocaleString('ko-KR');
}

/** 경로 좌표는 소수 첫째 자리까지만 — 서버와 브라우저의 부동소수 표기 차이로 그림이 달라지지 않게 */
function f(n: number): string {
  return n.toFixed(1);
}
