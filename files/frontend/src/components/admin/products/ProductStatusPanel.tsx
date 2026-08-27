'use client';

import { useState } from 'react';

import { Alert } from '../Alert';
import { Button } from '../Button';
import { Panel } from '../Panel';
import { ReasonDialog } from '../ReasonDialog';
import { SelectInput } from '../Field';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { useChangeProductStatus } from '@/hooks/admin/useProducts';
import { PRODUCT_STATUS_LABEL, type ProductStatus } from '@/types/product';
import type { AdminProduct } from '@/types/admin';
import { ApiError } from '@/types/api';
import styles from '../admin.module.css';

const STATUS_HINT: Record<ProductStatus, string> = {
  ON_SALE: '쇼핑몰에 노출되고 주문할 수 있다. 활성 옵션이 최소 1개 있어야 한다.',
  SOLD_OUT: '노출은 되지만 주문은 막힌다.',
  HIDDEN: '쇼핑몰 어디에도 나오지 않는다. 데이터는 남는다.',
  DISCONTINUED: '판매를 끝낸 상품. 과거 주문 이력은 그대로 유지된다.',
};

/**
 * 판매 상태 변경.
 *
 * 상태와 노출(active)은 별개다 — ON_SALE 이어도 노출을 끄면 고객에게 보이지 않는다.
 * 둘을 한 화면에 같이 두는 이유는 "판매중인데 왜 안 보이지"를 없애기 위해서다.
 */
export function ProductStatusPanel({
  product,
  canEdit,
}: {
  product: AdminProduct;
  canEdit: boolean;
}) {
  const change = useChangeProductStatus();
  const [status, setStatus] = useState<ProductStatus>(product.status);
  const [active, setActive] = useState(product.active);
  const [confirming, setConfirming] = useState(false);

  const dirty = status !== product.status || active !== product.active;

  return (
    <Panel
      title="판매 상태"
      note="상태와 노출은 따로 움직인다. 판매중이어도 노출을 끄면 고객에게 보이지 않는다."
    >
      <div className={styles.form}>
        <div className={styles.head}>
          <div className={styles.stat}>
            <span className={styles.statLabel}>현재</span>
            <span className={styles.statValueSm}>
              <StatusBadge tone={product.status === 'ON_SALE' ? 'success' : 'neutral'}>
                {PRODUCT_STATUS_LABEL[product.status]}
              </StatusBadge>{' '}
              <StatusBadge tone={product.active ? 'success' : 'neutral'}>
                {product.active ? '노출' : '숨김'}
              </StatusBadge>
            </span>
          </div>
        </div>

        {!canEdit ? (
          <Alert tone="info">상태 변경은 매니저(MANAGER) 이상만 할 수 있습니다.</Alert>
        ) : (
          <>
            <div className={styles.formGrid}>
              <div className={styles.field}>
                <label className={styles.label} htmlFor="status-select">
                  변경할 상태
                </label>
                <SelectInput
                  id="status-select"
                  value={status}
                  onChange={(e) => setStatus(e.target.value as ProductStatus)}
                >
                  {(Object.keys(PRODUCT_STATUS_LABEL) as ProductStatus[]).map((s) => (
                    <option key={s} value={s}>
                      {PRODUCT_STATUS_LABEL[s]}
                    </option>
                  ))}
                </SelectInput>
                <p className={styles.hint}>{STATUS_HINT[status]}</p>
              </div>

              <div className={styles.field}>
                <span className={styles.label}>노출</span>
                <label className={styles.checkRow}>
                  <input
                    type="checkbox"
                    checked={active}
                    onChange={(e) => setActive(e.target.checked)}
                  />
                  쇼핑몰에 노출
                </label>
                <p className={styles.hint}>
                  끄면 상태와 무관하게 모든 공개 조회에서 제외된다.
                </p>
              </div>
            </div>

            <div className={styles.formActions}>
              <Button variant="primary" disabled={!dirty} onClick={() => setConfirming(true)}>
                상태 변경
              </Button>
              {!dirty && <span className={styles.hint}>바뀐 값이 없다.</span>}
            </div>
          </>
        )}
      </div>

      <ReasonDialog
        open={confirming}
        title="판매 상태 변경"
        confirmLabel="변경"
        placeholder="예: 재고 입고 완료, 판매 시작"
        pending={change.isPending}
        error={change.error instanceof ApiError ? change.error.message : null}
        description={
          <>
            {PRODUCT_STATUS_LABEL[product.status]} · {product.active ? '노출' : '숨김'} →{' '}
            <strong>
              {PRODUCT_STATUS_LABEL[status]} · {active ? '노출' : '숨김'}
            </strong>
            <br />
            {status === 'ON_SALE' &&
              '판매중으로 바꾸려면 활성 옵션이 최소 1개 있어야 한다. 없으면 서버가 거절한다.'}
          </>
        }
        onConfirm={(reason) =>
          change.mutate(
            { id: product.id, status, active, reason },
            { onSuccess: () => setConfirming(false) },
          )
        }
        onClose={() => setConfirming(false)}
      />
    </Panel>
  );
}
