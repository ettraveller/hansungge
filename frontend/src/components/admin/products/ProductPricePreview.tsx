'use client';

import { Alert } from '../Alert';
import { Button } from '../Button';
import { Panel } from '../Panel';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { useProductPricePreview } from '@/hooks/admin/useProducts';
import { formatAdjustment, formatDateTime, formatKrw, formatPricePerGram } from '@/utils/format';
import type { AdminPriceQuote } from '@/types/admin';
import { ApiError } from '@/types/api';
import styles from '../admin.module.css';

/**
 * 가격 미리보기.
 *
 * 지금 이 순간 고객에게 나갈 금액을 서버가 계산해서 돌려준 것이다 — 화면에서 다시
 * 더하거나 곱하지 않는다 (설계서 원칙 P1). 구성요소의 합은 소계와 <b>정확히</b> 같고,
 * 반올림은 마지막에 한 번만 적용된다.
 *
 * <p>시세가 고시된 적 없으면 금액이 아니라 "시세문의"가 나간다. 그것이 오류가 아니라
 * 안전장치라는 점을 화면에서 분명히 한다.
 */
export function ProductPricePreview({ productId }: { productId: number }) {
  const { data, isLoading, error, refetch, isFetching } = useProductPricePreview(productId);

  return (
    <Panel
      title="가격 미리보기"
      note="지금 시세로 계산한 실제 판매가다. 시세가 바뀌면 이 값도 바뀐다."
      actions={
        <Button small onClick={() => void refetch()} disabled={isFetching}>
          {isFetching ? '계산 중…' : '다시 계산'}
        </Button>
      }
    >
      {error && (
        <Alert tone="danger">
          {error instanceof ApiError ? error.message : '가격을 계산하지 못했습니다.'}
        </Alert>
      )}

      {isLoading ? (
        <p className={styles.loading}>계산 중…</p>
      ) : !data || data.length === 0 ? (
        <p className={styles.empty}>계산할 옵션이 없습니다.</p>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
          {data.map((quote) => (
            <QuoteBlock key={quote.optionId ?? 'default'} quote={quote} />
          ))}
        </div>
      )}
    </Panel>
  );
}

function QuoteBlock({ quote }: { quote: AdminPriceQuote }) {
  const unavailable = quote.status !== 'AVAILABLE' && quote.finalUnitPrice == null;

  return (
    <div>
      <div className={styles.head} style={{ marginBottom: 10 }}>
        <div>
          <span className={styles.strong}>
            옵션 {quote.optionId == null ? '(기본)' : `#${quote.optionId}`}
          </span>
          <p className={styles.hint}>
            기준 시세 {formatPricePerGram(quote.appliedMarketPrice)}
            {quote.priceQuotedAt && ` · 산출 ${formatDateTime(quote.priceQuotedAt)}`}
          </p>
        </div>
        <StatusBadge tone={quote.orderable ? 'success' : 'warning'}>
          {quote.orderable ? '주문 가능' : statusLabel(quote)}
        </StatusBadge>
      </div>

      {unavailable ? (
        <Alert tone="warning" title="지금은 금액이 나가지 않습니다">
          쇼핑몰에는 금액 대신 <strong>시세문의</strong>가 노출되고 주문이 막힌다.
          {quote.unavailableReason && (
            <>
              <br />
              {quote.unavailableReason}
            </>
          )}
        </Alert>
      ) : (
        // 다른 관리자 표와 달리 td 에 data-label 을 붙이지 않는다 — 일부러다.
        // 이 표는 목록이 아니라 계산 명세서다. 행 하나가 독립된 항목이 아니라
        // 소계·부가세·단수조정·합계로 이어지는 한 줄기 계산이고, 그 줄들은
        // colSpan 으로 금액 열에 맞춰 서 있다. 카드로 쪼개면 '무엇에서 무엇이
        // 나왔는지'가 사라진다. 좁은 화면에서는 예전처럼 가로로 스크롤한다.
        <div className={styles.tableWrap}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th>구성요소</th>
                <th>산출 방식</th>
                <th className={styles.num}>단가</th>
                <th className={styles.num}>중량</th>
                <th className={styles.num}>금액</th>
              </tr>
            </thead>
            <tbody>
              {quote.components.map((c) => (
                <tr key={`${c.sort}-${c.type}`}>
                  <td className={styles.strong}>{c.label}</td>
                  <td className={styles.muted}>{c.typeName}</td>
                  <td className={styles.num}>
                    {c.unitPricePerGram == null ? '-' : formatPricePerGram(c.unitPricePerGram)}
                  </td>
                  <td className={styles.num}>{c.weightG == null ? '-' : `${c.weightG}g`}</td>
                  <td className={styles.num}>{formatKrw(c.amount)}</td>
                </tr>
              ))}
              <tr>
                <td colSpan={4} className={styles.strong}>
                  소계
                </td>
                <td className={`${styles.num} ${styles.strong}`}>{formatKrw(quote.subtotal)}</td>
              </tr>
              {quote.vat != null && (
                <tr>
                  <td colSpan={4} className={styles.muted}>
                    부가세 ({quote.vatMode ?? '-'})
                    {quote.vatPolicyTemporary && ' · 임시 정책'}
                  </td>
                  <td className={styles.num}>{formatKrw(quote.vat)}</td>
                </tr>
              )}
              <tr>
                <td colSpan={4} className={styles.muted}>
                  반올림 ({quote.roundingPolicy ?? '-'})
                  {quote.roundingPolicyTemporary && ' · 임시 정책'}
                </td>
                <td className={styles.num}>{formatAdjustment(quote.roundingDelta)}</td>
              </tr>
              <tr>
                <td colSpan={4} className={styles.strong}>
                  최종 판매가
                </td>
                <td className={`${styles.num} ${styles.strong}`}>
                  {formatKrw(quote.finalUnitPrice)}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function statusLabel(quote: AdminPriceQuote): string {
  switch (quote.status) {
    case 'INQUIRY_ONLY':
      return '시세문의';
    case 'SOLD_OUT':
      return '품절';
    case 'NOT_FOR_SALE':
      return '비노출';
    default:
      return '주문 불가';
  }
}
