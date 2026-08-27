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

/** 시세를 표시할 수 없을 때의 문구 (설계서 §12.4) */
export const PRICE_INQUIRY_LABEL = '시세문의';
