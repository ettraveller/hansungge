'use client';

import { Alert } from '@/components/admin/Alert';
import { CollectionStatusCard } from '@/components/admin/prices/CollectionStatusCard';
import { CurrentPriceTable } from '@/components/admin/prices/CurrentPriceTable';
import { PriceDeclareForm } from '@/components/admin/prices/PriceDeclareForm';
import { PriceHistoryPanel } from '@/components/admin/prices/PriceHistoryPanel';
import { PricePendingPanel } from '@/components/admin/prices/PricePendingPanel';
import { PricePolicyPanel } from '@/components/admin/prices/PricePolicyPanel';
import { useAdminSession } from '@/hooks/admin/useAdminSession';
import styles from '@/components/admin/admin.module.css';

/**
 * 시세관리.
 *
 * 이 화면이 이 시스템의 심장이다 — 여기서 정한 값이 모든 상품 가격의 근거가 된다.
 * 그래서 화면 순서를 "지금 어떤 상태인가 → 무엇을 바꾸는가 → 무엇이 남았는가"로 둔다.
 *
 * 조회는 OPERATOR 부터, 고시·정책 변경·승인은 ADMIN 부터다. 여기서 버튼을 감추는 것은
 * 편의일 뿐이고, 실제 차단은 백엔드가 한다.
 */
export function AdminPricesView() {
  const { can } = useAdminSession();
  const canWrite = can('ADMIN');

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <div>
          <h1 className={styles.title}>시세관리</h1>
          <p className={styles.subtitle}>
            원본 시세에 보정 정책을 적용해 최종 시세를 발행한다. 발행된 시세는 수정·삭제되지
            않고 마감 후 새 행으로 쌓인다.
          </p>
        </div>
      </header>

      {!canWrite && (
        <Alert tone="info" title="조회 권한으로 보고 있습니다">
          시세 고시·정책 변경·승인은 관리자(ADMIN) 이상만 할 수 있습니다.
        </Alert>
      )}

      <CurrentPriceTable />
      <CollectionStatusCard />
      {canWrite && <PriceDeclareForm />}
      <PricePendingPanel canDecide={canWrite} />
      <PricePolicyPanel canEdit={canWrite} />
      <PriceHistoryPanel />
    </div>
  );
}
