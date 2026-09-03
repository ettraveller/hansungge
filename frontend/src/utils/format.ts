/**
 * 표시 포맷 유틸.
 *
 * 중요: 이 파일에는 <b>계산</b>이 없다. 오직 포맷만 한다.
 * 금액·중량 계산은 전부 백엔드(BigDecimal)의 몫이며, 프론트엔드가 더하거나 곱하는 순간
 * 부동소수점 오차와 "화면 금액 ≠ 결제 금액" 사고가 시작된다 (설계서 원칙 P1).
 */

const KRW = new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 0 });
const DECIMAL = new Intl.NumberFormat('ko-KR', {
  minimumFractionDigits: 0,
  maximumFractionDigits: 4,
});

/** 서버가 준 금액 문자열을 "837,500원" 으로 표시한다. */
export function formatKrw(amount: string | number | null | undefined): string {
  if (amount == null || amount === '') return '-';
  const n = typeof amount === 'string' ? Number(amount) : amount;
  if (!Number.isFinite(n)) return '-';
  return `${KRW.format(n)}원`;
}

/** 시세 표시: "210,000원/g" */
export function formatPricePerGram(price: string | number | null | undefined): string {
  if (price == null || price === '') return '-';
  const n = typeof price === 'string' ? Number(price) : price;
  if (!Number.isFinite(n)) return '-';
  return `${KRW.format(n)}원/g`;
}

/** 중량 표시: "3.75g" */
export function formatGram(gram: string | number | null | undefined): string {
  if (gram == null || gram === '') return '-';
  const n = typeof gram === 'string' ? Number(gram) : gram;
  if (!Number.isFinite(n)) return '-';
  return `${DECIMAL.format(n)}g`;
}

/** 보정액 표시: "+10,000원" / "-7,000원" */
export function formatAdjustment(amount: string | number | null | undefined): string {
  if (amount == null || amount === '') return '-';
  const n = typeof amount === 'string' ? Number(amount) : amount;
  if (!Number.isFinite(n)) return '-';
  const sign = n > 0 ? '+' : '';
  return `${sign}${KRW.format(n)}원`;
}

/**
 * 변동액 표시: "▼ 26,000" / "▲ 26,000" / "— 0"
 *
 * 화살표를 텍스트로 넣는 이유 — CSS {@code ::before} 로 붙이면 복사·스크린리더에서
 * 사라진다. 시세표에서 방향은 금액만큼 중요한 정보다.
 */
export function formatDelta(amount: number | null | undefined): string {
  if (amount == null || !Number.isFinite(amount)) return '-';
  if (amount === 0) return '— 0';
  return `${amount > 0 ? '▲' : '▼'} ${KRW.format(Math.abs(amount))}`;
}

/** 변동률 표시: "-3.00%" / "+1.25%" / "0.00%" */
export function formatPercent(percent: number | null | undefined): string {
  if (percent == null || !Number.isFinite(percent)) return '-';
  const sign = percent > 0 ? '+' : '';
  return `${sign}${percent.toFixed(2)}%`;
}

/** "▼ 26,000 (-3.00%)" — 시세표 변동 칸의 한 줄 표기 */
export function formatDeltaWithPercent(
  amount: number | null | undefined,
  percent: number | null | undefined,
): string {
  if (amount == null || percent == null) return '-';
  return `${formatDelta(amount)} (${formatPercent(percent)})`;
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '-';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '-';
  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: 'Asia/Seoul',
  }).format(d);
}

/**
 * 게시판 목록의 날짜: "2026-09-01"
 *
 * <p>레퍼런스는 올해 글을 {@code 09-01}, 오늘 글을 {@code 13:37} 로 줄여 쓴다. 그렇게
 * 하지 않는 이유는 <b>"오늘"이 렌더링 시점에 따라 달라지기 때문</b>이다. 서버에서 그린
 * 문자열과 브라우저에서 다시 계산한 문자열이 어긋나면 하이드레이션 경고가 나고, 자정
 * 근처에서는 실제로 값이 달라진다.
 *
 * <p>연도를 붙이는 것은 덤이다 — 목록에서 {@code 09-01} 만 보이면 올해 글인지 재작년
 * 글인지 알 수 없다.
 */
export function formatBoardDate(iso: string | null | undefined): string {
  if (!iso) return '-';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '-';
  // en-CA 로케일이 YYYY-MM-DD 를 준다. 수동 조립보다 시간대 처리가 안전하다
  return new Intl.DateTimeFormat('en-CA', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    timeZone: 'Asia/Seoul',
  }).format(d);
}

/** 시세를 표시할 수 없을 때의 문구 (설계서 §12.4) */
export const PRICE_INQUIRY_LABEL = '시세문의';
