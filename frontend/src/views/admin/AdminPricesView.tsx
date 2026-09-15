'use client';

import { Alert } from '@/components/admin/Alert';
import { CurrentPriceTable } from '@/components/admin/prices/CurrentPriceTable';
import { DerivationPanel } from '@/components/admin/prices/DerivationPanel';
import { PriceDeclareForm } from '@/components/admin/prices/PriceDeclareForm';
import { PriceHistoryPanel } from '@/components/admin/prices/PriceHistoryPanel';
import { useAdminSession } from '@/hooks/admin/useAdminSession';
import styles from '@/components/admin/admin.module.css';

/**
 * 시세관리.
 *
 * 이 화면이 이 시스템의 심장이다 — 여기서 고시한 값이 시세를 따르는 모든 상품 가격의
 * 근거가 된다. 시세는 외부에서 받아 오지 않는다. 관리자가 고시한 값만 쓴다.
 *
 * 순서는 "고시한다 → 지금 어떻게 나가고 있나 → 18K·14K 는 어떻게 따라가나 → 지난 기록"이다.
 * 이 화면에서 가장 자주 하는 일이 고시라 맨 위에 둔다.
 *
 * 조회는 OPERATOR 부터, 고시·환산 설정은 ADMIN 부터다. 여기서 감추는 것은 편의일 뿐이고,
 * 실제 차단은 백엔드가 한다.
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
            살 때·팔 때 시세를 고시하고 18K·14K 환산 비율을 정한다. 고시한 값은 새로 고시할
            때까지 그대로 쓰인다.
          </p>
        </div>
      </header>

      {!canWrite && (
        <Alert tone="info" title="조회 권한으로 보고 있습니다">
          시세 고시와 환산 설정은 관리자(ADMIN) 이상만 할 수 있습니다.
        </Alert>
      )}

      {canWrite && <PriceDeclareForm />}
      <CurrentPriceTable />
      <DerivationPanel canEdit={canWrite} />
      <PriceHistoryPanel />
    </div>
  );
}
