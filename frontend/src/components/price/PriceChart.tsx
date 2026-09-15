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
}

const DESKTOP: Box = { w: 900, h: 300, pad: { top: 16, right: 16, bottom: 30, left: 74 } };

/**
 * 휴대폰용 뷰박스.
 *
 * <p>가로를 900 → 400 으로 줄인 것이 요점이다. 아래 {@code PriceChart} 설명 참고.
 * 세로 비율은 조금 높다 — 폭이 좁아진 만큼 눌러 놓으면 등락이 안 보인다.
 */
const MOBILE: Box = { w: 400, h: 260, pad: { top: 14, right: 12, bottom: 26, left: 58 } };

/** 가로 눈금 개수. 3줄이면 최저·중간·최고가 되어 읽는 데 충분하다. */
const GRID = 3;

/** 이보다 짧은 구간이면 축에 시각까지 적는다. 날짜만 쓰면 양 끝이 같은 글자가 된다. */
const SHOW_TIME_WITHIN_MS = 2 * 24 * 60 * 60 * 1000;

/**
 * 시세 추이 그래프.
 *
 * <h2>서버 컴포넌트다</h2>
 * {@code 'use client'} 가 없다. SVG 를 서버에서 그려 <b>초기 HTML 에 그래프가 실린다</b> —
 * 자바스크립트가 꺼져 있어도, 검색엔진이 읽을 때도 그림이 있다. 차트 라이브러리를
 * 쓰지 않는 이유이기도 하다: 선 하나와 눈금 세 줄에 200KB 짜리 의존성을 더할 이유가 없다.
 *
 * <h2>{@link Sparkline} 과 나누는 이유</h2>
 * 스파크라인은 <b>축도 눈금도 없는</b> 그림이다. 시세 카드 안에서 "오르는 중인가"만
 * 답한다. 이 그래프는 값을 읽는 용도라 축과 날짜가 있어야 한다. 하나로 합쳐 옵션으로
 * 가르면 두 용도 모두 어중간해진다.
 *
 * <h2>고시 시점마다 한 점이다</h2>
 * 일별 종가가 아니다. 하루에 두 번 고시하면 그날 점이 둘이고, 고시가 없던 날은 점이
 * 없다. 그래서 x 축을 <b>시간에 비례</b>해 놓는다 — 점을 같은 간격으로 늘어놓으면
 * 고시가 잦았던 구간이 실제보다 넓게 보인다.
 *
 * <h2>구간이 주어지면 마지막 값을 지금까지 잇는다</h2>
 * 고시한 값은 다음 고시까지 그대로 적용된다. 점만 잇고 끝내면 "1일" 그래프는 아침 고시
 * 한 번 뒤로 오른쪽이 비어 버리는데, 실제로는 그 값으로 지금도 팔고 있다. 그래서 마지막
 * 값을 {@code until} 까지 수평으로 늘인다. 기간 시작 전에 고시돼 기간 첫머리에 적용 중이던
 * 값(이력 API 가 맨 앞에 붙여 준다)은 가로축 왼쪽 끝에 놓는다.
 *
 * <h2>뷰박스를 둘 그린다</h2>
 * SVG 는 뷰박스째로 줄어든다. 가로 900 짜리 그림이 휴대폰에서 340px 로 들어가면
 * <b>축 글자도 0.38배</b>가 되어 13px 글자가 5px 로 찍힌다. CSS 로 글자만 키우는 것은
 * 같은 배율에 걸리므로 해결이 되지 않는다 — 뷰박스 자체가 좁아야 한다.
 *
 * <p>그래서 데스크톱용과 휴대폰용을 <b>둘 다 그려 놓고 CSS 로 하나만 보인다.</b>
 * 자바스크립트로 화면 폭을 재서 고르는 방법도 있지만, 그러면 이 컴포넌트가 클라이언트가
 * 되어 위에 적은 것(초기 HTML 에 그래프가 실린다)을 통째로 잃는다. 두 벌의 경로 문자열은
 * 합쳐 3KB 남짓이라 그 값을 치를 만하다.
 */
export function PriceChart({ points, label, since, until }: Props) {
  if (points.length === 0) {
    return <p className={styles.empty}>이 기간에 고시된 시세가 없습니다.</p>;
  }

  // 구간이 없으면 점 하나로는 선을 그릴 수 없다. 구간이 있으면 그 값이 지금까지
  // 이어지는 수평선이 되므로 그린다.
  if (points.length === 1 && (since == null || until == null)) {
    return <p className={styles.empty}>이 기간의 고시가 1건뿐이라 추이를 그릴 수 없습니다.</p>;
  }

  return (
    <>
      <Plot points={points} label={label} since={since} until={until} box={DESKTOP} className={styles.wide} />
      <Plot points={points} label={label} since={since} until={until} box={MOBILE} className={styles.narrow} />
    </>
  );
}

/** 한 벌의 그림. 뷰박스 치수만 다르고 그리는 규칙은 같다. */
function Plot({
  points,
  label,
  since,
  until,
  box,
  className,
}: Props & { box: Box; className: string }) {
  const { w: W, h: H, pad: PAD } = box;

  const firstAt = Date.parse(points[0].effectiveFrom);
  const lastAt = Date.parse(points[points.length - 1].effectiveFrom);

  // 가로축 구간. 구간이 주어지면 그것을, 아니면 첫 고시~마지막 고시를 쓴다.
  const t0 = since != null ? Math.min(Date.parse(since), lastAt) : firstAt;
  const tEnd = until != null ? Math.max(Date.parse(until), lastAt) : lastAt;

  // 기간 시작 전에 고시된 값은 왼쪽 끝에 놓고, 마지막 값은 구간 끝까지 늘인다.
  const series = points.map((p) => ({ t: Math.max(Date.parse(p.effectiveFrom), t0), v: p.pricePerGram }));
  const last = series[series.length - 1];
  if (tEnd > last.t) {
    series.push({ t: tEnd, v: last.v });
  }

  const values = series.map((s) => s.v);
  const lo = Math.min(...values);
  const hi = Math.max(...values);
  // 위아래가 딱 붙으면 선이 테두리에 닿는다. 폭의 8% 를 여백으로 둔다.
  // 값이 전부 같으면 폭이 0 이라 나눗셈이 깨지므로 그때만 임의 폭을 준다.
  const span = hi - lo || Math.max(hi * 0.02, 1);
  const min = lo - span * 0.08;
  const max = hi + span * 0.08;

  const tSpan = tEnd - t0 || 1;

  const plotW = W - PAD.left - PAD.right;
  const plotH = H - PAD.top - PAD.bottom;

  const x = (t: number) => PAD.left + ((t - t0) / tSpan) * plotW;
  const y = (v: number) => PAD.top + plotH - ((v - min) / (max - min)) * plotH;

  const line = series
    .map((s, i) => `${i === 0 ? 'M' : 'L'}${x(s.t).toFixed(1)} ${y(s.v).toFixed(1)}`)
    .join(' ');

  const baseline = PAD.top + plotH;
  const end = series[series.length - 1];
  const area = `${line} L${x(end.t).toFixed(1)} ${baseline} L${x(series[0].t).toFixed(1)} ${baseline} Z`;

  // 눈금은 실제 값 범위 안에서만 잡는다. 라벨이 가리키는 높이에 선이 지나가야 한다.
  // 값이 전부 같으면 세 눈금이 같은 값이 되므로 키는 순번으로 잡는다.
  const ticks = Array.from({ length: GRID }, (_, i) => lo + ((hi - lo) / (GRID - 1)) * i);

  const withTime = tEnd - t0 <= SHOW_TIME_WITHIN_MS;

  return (
    <svg
      className={`${styles.chart} ${className}`}
      viewBox={`0 0 ${W} ${H}`}
      role="img"
      aria-label={label}
    >
      {ticks.map((v, i) => (
        <g key={i}>
          <line className={styles.grid} x1={PAD.left} y1={y(v)} x2={W - PAD.right} y2={y(v)} />
          <text className={styles.axis} x={PAD.left - 10} y={y(v) + 4} textAnchor="end">
            {formatAxis(v)}
          </text>
        </g>
      ))}

      <path className={styles.area} d={area} />
      <path className={styles.line} d={line} />

      {/* 끝점만 찍는다. 전 지점에 점을 찍으면 선보다 점이 먼저 보인다 */}
      <circle className={styles.dot} cx={x(end.t)} cy={y(end.v)} r={5} />

      <text className={styles.axis} x={PAD.left} y={H - 8}>
        {formatTick(t0, withTime)}
      </text>
      <text className={styles.axis} x={W - PAD.right} y={H - 8} textAnchor="end">
        {formatTick(tEnd, withTime)}
      </text>
    </svg>
  );
}

/** 축 라벨 — 천 단위로 줄인다. 225,333 을 그대로 쓰면 왼쪽 여백을 다 잡아먹는다. */
function formatAxis(perGram: number): string {
  if (perGram >= 10000) return `${Math.round(perGram / 1000).toLocaleString('ko-KR')}천`;
  return Math.round(perGram).toLocaleString('ko-KR');
}

/**
 * 축의 날짜(와 짧은 구간이면 시각).
 *
 * <p>{@code Intl} 에 시간대를 못 박는다. 서버의 시간대가 한국이 아니면 하루 경계가
 * 어긋난 날짜가 찍힌다.
 */
function formatTick(ms: number, withTime: boolean): string {
  if (Number.isNaN(ms)) return '';
  return new Intl.DateTimeFormat('ko-KR', {
    month: '2-digit',
    day: '2-digit',
    ...(withTime ? { hour: '2-digit', minute: '2-digit', hour12: false } : {}),
    timeZone: 'Asia/Seoul',
  }).format(new Date(ms));
}
