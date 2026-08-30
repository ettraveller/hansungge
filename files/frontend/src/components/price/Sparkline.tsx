import styles from './Sparkline.module.css';

interface Props {
  /** 오래된 순으로 정렬된 시세 값들 */
  values: number[];
  /** 그라디언트 id 는 문서 전체에서 유일해야 한다 — 같은 페이지에 여러 개가 뜨면 서로 덮어쓴다 */
  gradientId: string;
  /** 값이 부족해 선을 그릴 수 없을 때 대신 보여줄 문구 */
  emptyLabel?: string;
}

/** viewBox 좌표계. 실제 크기는 CSS 가 정한다 (preserveAspectRatio="none"). */
const VIEW_W = 640;
const VIEW_H = 76;
/** 위아래로 남기는 여백. 0 이면 최고·최저점이 테두리에 붙어 잘려 보인다. */
const PAD_Y = 8;

/**
 * 시세 추이 스파크라인.
 *
 * <p><b>축도 눈금도 없다.</b> 여기서 읽어야 할 것은 "얼마였나"가 아니라 "오르는 중인가"다.
 * 정확한 값은 바로 옆 시세표에 있고, 이 그림에 눈금을 넣으면 둘 다 어중간해진다.
 *
 * <p>값이 1개 이하이거나 전부 같으면 <b>가운데 수평선</b>을 그린다. 이때 정규화 식의
 * 분모가 0 이 되므로 나누지 않는다 — NaN 이 들어간 path 는 브라우저가 조용히 버려서
 * 그래프가 통째로 사라진다.
 */
export function Sparkline({ values, gradientId, emptyLabel = '추이 정보 없음' }: Props) {
  if (values.length === 0) {
    return <p className={styles.empty}>{emptyLabel}</p>;
  }

  const min = Math.min(...values);
  const max = Math.max(...values);
  const span = max - min;

  const points = values.map((value, i) => {
    const x = values.length === 1 ? VIEW_W : (i / (values.length - 1)) * VIEW_W;
    // 분모가 0 이면 정규화하지 않고 가운데에 둔다
    const ratio = span === 0 ? 0.5 : (value - min) / span;
    // SVG 는 y 가 아래로 커진다. 값이 클수록 위로 가야 하므로 뒤집는다
    const y = VIEW_H - PAD_Y - ratio * (VIEW_H - PAD_Y * 2);
    return { x, y };
  });

  const line =
    points.length === 1
      ? `M0,${points[0].y} L${VIEW_W},${points[0].y}`
      : points.map((p, i) => `${i === 0 ? 'M' : 'L'}${round(p.x)},${round(p.y)}`).join(' ');

  const area = `${line} L${VIEW_W},${VIEW_H} L0,${VIEW_H} Z`;
  const last = points[points.length - 1];

  return (
    <svg
      className={styles.chart}
      viewBox={`0 0 ${VIEW_W} ${VIEW_H}`}
      preserveAspectRatio="none"
      role="img"
      aria-label={`최근 시세 추이. ${values.length}개 지점.`}
    >
      <defs>
        <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" className={styles.fillTop} />
          <stop offset="100%" className={styles.fillBottom} />
        </linearGradient>
      </defs>
      <path d={area} fill={`url(#${gradientId})`} />
      <path d={line} className={styles.line} fill="none" />
      {/* 마지막 점만 찍는다. 전 지점에 점을 찍으면 선보다 점이 눈에 들어온다 */}
      <circle cx={round(last.x)} cy={round(last.y)} r={3.5} className={styles.dot} />
    </svg>
  );
}

/** 소수점을 2자리로 줄인다. path 문자열이 짧아져 초기 HTML 이 가벼워진다. */
function round(n: number): number {
  return Math.round(n * 100) / 100;
}
