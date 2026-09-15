'use client';

import Link from 'next/link';

import { Alert } from '@/components/admin/Alert';
import { Panel } from '@/components/admin/Panel';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { useAdminSession } from '@/hooks/admin/useAdminSession';
import { useCurrentPrices } from '@/hooks/admin/usePrices';
import { useAdminProducts } from '@/hooks/admin/useProducts';
import { useUnansweredQnaCount } from '@/hooks/admin/useQnas';
import { formatDateTime, formatPricePerGram } from '@/utils/format';
import { METAL_LABEL } from '@/types/price';
import { ApiError } from '@/types/api';
import styles from '@/components/admin/admin.module.css';

/**
 * 대시보드.
 *
 * "지금 뭔가 잘못돼 있는가"를 한 화면에서 답하는 것이 목적이다. 그래서 좋은 소식보다
 * <b>나쁜 소식을 위에</b> 둔다 — 아직 고시되지 않아 시세문의로 나가는 시세, 밀린 문의.
 */
export function AdminDashboardView() {
  const { admin } = useAdminSession();

  const prices = useCurrentPrices();
  const onSale = useAdminProducts({ status: 'ON_SALE', size: 1 });
  const hidden = useAdminProducts({ status: 'HIDDEN', size: 1 });
  const unansweredQnas = useUnansweredQnaCount();

  const priceRows = prices.data ?? [];
  const unquoted = priceRows.filter((p) => !p.available);

  // 가장 최근 고시 시각. 시세를 오늘 올렸는지가 운영자가 아침에 제일 먼저 확인하는 것이다.
  const lastQuotedAt = priceRows
    .map((p) => p.effectiveFrom)
    .filter((at): at is string => at != null)
    .reduce<string | null>(
      (latest, at) => (latest == null || Date.parse(at) > Date.parse(latest) ? at : latest),
      null,
    );

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <div>
          <h1 className={styles.title}>대시보드</h1>
          <p className={styles.subtitle}>
            {admin ? `${admin.name}님, ` : ''}지금 확인이 필요한 것부터 보여줍니다.
          </p>
        </div>
      </header>

      {prices.error && (
        <Alert tone="danger">
          {prices.error instanceof ApiError
            ? prices.error.message
            : '일부 정보를 불러오지 못했습니다.'}
        </Alert>
      )}

      {unquoted.length > 0 && (
        <Alert tone="warning" title={`아직 고시되지 않은 시세 ${unquoted.length}건`}>
          {unquoted
            .map(
              (p) =>
                `${METAL_LABEL[p.metalType] ?? p.metalName} ${p.priceSide === 'SELL' ? '살 때' : '팔 때'}`,
            )
            .join(', ')}
          {' — '}이 금속을 쓰는 상품은 금액 대신 &quot;시세문의&quot;가 나가고 주문이 막힌다.{' '}
          <Link href="/admin/prices">시세 고시하기 →</Link>
        </Alert>
      )}

      <div className={styles.cards}>
        <Panel>
          <div className={styles.stat}>
            <span className={styles.statLabel}>판매중 상품</span>
            <span className={styles.statValue}>
              {onSale.data?.totalElements ?? (onSale.isLoading ? '…' : 0)}
            </span>
            <Link className={styles.hint} href="/admin/products?status=ON_SALE">
              상품관리 →
            </Link>
          </div>
        </Panel>
        <Panel>
          <div className={styles.stat}>
            <span className={styles.statLabel}>숨김 상품</span>
            <span className={styles.statValue}>
              {hidden.data?.totalElements ?? (hidden.isLoading ? '…' : 0)}
            </span>
            <span className={styles.hint}>등록만 하고 아직 노출하지 않은 상품</span>
          </div>
        </Panel>
        {/* 답변이 밀리는 것이 게시판의 유일한 실패 방식이다. 목록을 열어야만 알 수
            있으면 아무도 열지 않는 날 쌓인다 — 그래서 첫 화면까지 올린다. */}
        <Panel>
          <div className={styles.stat}>
            <span className={styles.statLabel}>답변 대기 문의</span>
            <span className={styles.statValue}>
              {unansweredQnas.data?.count ?? (unansweredQnas.isLoading ? '…' : 0)}
            </span>
            <Link className={styles.hint} href="/admin/qnas">
              문의 관리 →
            </Link>
          </div>
        </Panel>
        <Panel>
          <div className={styles.stat}>
            <span className={styles.statLabel}>마지막 고시</span>
            <span className={styles.statValueSm}>
              {prices.isLoading ? '…' : formatDateTime(lastQuotedAt)}
            </span>
            <Link className={styles.hint} href="/admin/prices">
              시세관리 →
            </Link>
          </div>
        </Panel>
      </div>

      <Panel
        title="현재 시세"
        note="쇼핑몰이 이 값으로 상품 가격을 계산한다."
        actions={
          <Link className={`${styles.btn} ${styles.btnSm}`} href="/admin/prices">
            시세관리
          </Link>
        }
        flush
      >
        {prices.isLoading ? (
          <p className={styles.loading}>불러오는 중…</p>
        ) : priceRows.length === 0 ? (
          <p className={styles.empty}>표시할 시세가 없습니다.</p>
        ) : (
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>금속</th>
                  <th>구분</th>
                  <th className={styles.num}>고시가</th>
                  <th>상태</th>
                  <th>고시 시각</th>
                </tr>
              </thead>
              <tbody>
                {priceRows.map((row) => (
                  <tr key={`${row.metalType}-${row.priceSide}`}>
                    <td data-label="금속" data-card-title className={styles.strong}>
                      {METAL_LABEL[row.metalType] ?? row.metalName}
                    </td>
                    <td data-label="구분">{row.priceSide === 'SELL' ? '살 때' : '팔 때'}</td>
                    <td data-label="고시가" className={`${styles.num} ${styles.strong}`}>
                      {row.available ? formatPricePerGram(row.pricePerGram) : '시세문의'}
                    </td>
                    <td data-label="상태">
                      <StatusBadge tone={row.available ? 'success' : 'neutral'}>
                        {row.available ? '판매중' : '고시 안 됨'}
                      </StatusBadge>
                    </td>
                    <td data-label="고시 시각" className={styles.muted}>
                      {formatDateTime(row.effectiveFrom)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Panel>
    </div>
  );
}
