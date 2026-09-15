'use client';

import { Alert } from '../Alert';
import { Button } from '../Button';
import { Modal } from '../Modal';
import { PriceErrorAlert } from './PriceErrorAlert';
import { QuoteChangeTable } from './QuoteChangeTable';
import type { QuotePlan } from '@/types/admin';
import styles from '../admin.module.css';

/**
 * 크게 바뀌는 값 확인.
 *
 * 예전에는 기준을 넘는 값이 "승인 대기"로 보류됐다. 사람이 입력한 값을 같은 사람이
 * 나중에 다시 승인하는 절차는 확인 한 번과 다르지 않았고, 그 사이 시세만 멈춰 있었다.
 * 그래서 고시하기 <b>전에</b> 바뀔 값을 보여주고 한 번 더 묻는다.
 *
 * 무엇이 기준을 넘는지 표에서 줄로 짚어 준다. "크게 바뀝니다"만으로는 18K 환산값처럼
 * 입력하지 않은 줄이 원인일 때 어디를 봐야 할지 모른다.
 */
export function QuoteConfirmDialog({
  plan,
  confirmLabel,
  pending,
  error,
  onConfirm,
  onClose,
}: {
  plan: QuotePlan;
  confirmLabel: string;
  pending: boolean;
  error: unknown;
  onConfirm: () => void;
  onClose: () => void;
}) {
  const over = plan.lines.filter((line) => line.exceedsLimit).length;

  return (
    <Modal
      open
      title="크게 바뀌는 값이 있습니다"
      onClose={onClose}
      footer={
        <>
          <Button onClick={onClose} disabled={pending}>
            다시 확인하기
          </Button>
          <Button variant="danger" disabled={pending} onClick={onConfirm}>
            {pending ? '고시 중…' : confirmLabel}
          </Button>
        </>
      }
    >
      <div className={styles.form}>
        <PriceErrorAlert error={error} fallback="고시하지 못했습니다." />

        <Alert tone="warning" title={`직전 고시보다 ${plan.limitPercent}% 넘게 바뀌는 값이 ${over}건`}>
          자릿수나 1돈·1g 단위가 바뀌지 않았는지 한 번 더 확인해 주세요. 맞으면 그대로
          고시합니다.
        </Alert>

        <QuoteChangeTable lines={plan.lines} />
      </div>
    </Modal>
  );
}
