'use client';

import Link from 'next/link';

import { Alert } from '@/components/admin/Alert';
import { Panel } from '@/components/admin/Panel';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { useAdminSession } from '@/hooks/admin/useAdminSession';
import { useCollectionStatus, useCurrentPrices, usePendingPrices } from '@/hooks/admin/usePrices';
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
 * <b>나쁜 소식을 위에</b> 둔다 — 시세문의로 빠진 조합, 승인 대기, 재고 0.
 */
export function AdminDashboardView() {
  const { admin } = useAdminSession();

  const prices = useCurrentPrices();
  const collection = useCollectionStatus();
  const pending = usePendingPrices('PENDING', 0);
  const onSale = useAdminProducts({ status: 'ON_SALE', size: 1 });
  const hidden = useAdminProducts({ status: 'HIDDEN', size: 1 });
  const unansweredQnas = useUnansweredQnaCount();

  const priceRows = prices.data ?? [];
  const unusable = priceRows.filter((p) => !p.available);
  const pendingCount = pending.data?.totalElements ?? 0;

  const anyError = prices.error ?? collection.error ?? pending.error;

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

      {anyError && (
        <Alert tone="danger">
          {anyError instanceof ApiError ? anyError.message : '일부 정보를 불러오지 못했습니다.'}
        </Alert>
      )}

      {pendingCount > 0 && (
        <Alert tone="warning" title={`시세 승인 대기 ${pendingCount}건`}>
          변동률이 허용치를 넘어 자동 적용이 중단된 건이다. 승인하기 전까지 해당 금속은
          직전 시세로 계속 판매된다.{' '}
          <Link href="/admin/prices">시세관리에서 처리 →</Link>
        </Alert>
      )}

      {unusable.length > 0 && (
        <Alert tone="warning" title={`시세문의로 빠진 조합 ${unusable.length}건`}>
          {unusable
            .map((p) => `${METAL_LABEL[p.metalType] ?? p.metalName} ${p.priceSide === 'SELL' ? '판매' : '매입'}`)
            .join(', ')}
          {' — '}이 금속을 쓰는 상품은 금액 대신 &quot;시세문의&quot;가 나가고 주문이 막힌다.{' '}
          <Link href="/admin/prices">시세 고시하기 →</Link>
        </Alert>
      )}

      {collection.data?.neverSucceeded === false && collection.data.failureCount > 0 && (
        <Alert tone="warning" title={`시세 수집 연속 실패 ${collection.data.failureCount}회`}>
          기존 시세는 유지되고 있다. 마지막 성공은{' '}
          {formatDateTime(collection.data.lastSuccessAt)} 이다.
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
        <Panel>
          <div className={styles.stat}>
            <span className={styles.statLabel}>승인 대기</span>
            <span className={styles.statValue}>{pendingCount}</span>
            <Link className={styles.hint} href="/admin/prices">
              시세관리 →
            </Link>
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
            <span className={styles.statLabel}>마지막 수집 성공</span>
            <span className={styles.statValueSm}>
              {formatDateTime(collection.data?.lastSuccessAt)}
            </span>
            <span className={styles.hint}>
              {collection.data?.neverSucceeded
                ? '외부 연동 전 — 수동 고시만 사용 중'
                : `스케줄러 ${collection.data?.schedulerEnabled ? '동작 중' : '중지'}`}
            </span>
          </div>
        </Panel>
      </div>

      <Panel
        title="현재 시세"
        note="쇼핑몰이 이 최종가로 상품 가격을 계산한다."
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
                  <th className={styles.num}>최종 시세</th>
                  <th>상태</th>
                  <th>고시 시각</th>
                </tr>
              </thead>
              <tbody>
                {priceRows.map((row) => (
                  <tr key={`${row.metalType}-${row.priceSide}`}>
                    <td data-label="금속" data-card-title className={styles.strong}>{METAL_LABEL[row.metalType] ?? row.metalName}</td>
                    <td data-label="구분">{row.priceSide === 'SELL' ? '판매' : '매입'}</td>
                    <td data-label="최종 시세" className={`${styles.num} ${styles.strong}`}>
                      {row.available ? formatPricePerGram(row.pricePerGram) : '시세문의'}
                    </td>
                    <td data-label="상태">
                      <StatusBadge
                        tone={
                          row.availability === 'AVAILABLE'
                            ? 'success'
                            : row.availability === 'STALE'
                              ? 'warning'
                              : 'neutral'
                        }
                      >
                        {row.availability === 'AVAILABLE'
                          ? '판매중'
                          : row.availability === 'STALE'
                            ? '오래됨'
                            : '고시 안 됨'}
                      </StatusBadge>
                    </td>
                    <td data-label="고시 시각" className={styles.muted}>{formatDateTime(row.effectiveFrom)}</td>
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
