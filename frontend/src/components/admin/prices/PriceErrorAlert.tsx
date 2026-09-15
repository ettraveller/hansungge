'use client';

import { Alert } from '../Alert';
import { ApiError } from '@/types/api';
import styles from '../admin.module.css';

/**
 * 시세 API 오류 표시.
 *
 * 서버는 대표 문구(message)와 함께, 어느 금속의 어느 쪽이 왜 막혔는지를 항목별로
 * (fieldErrors) 보낸다. 대표 문구만 보여주면 "무엇을 고쳐야 하는지"가 빠진다 —
 * 24K 를 고시했는데 18K 환산값이 하한에 걸린 경우처럼, 입력한 칸이 아닌 곳이 원인일 때
 * 특히 그렇다.
 */
export function PriceErrorAlert({ error, fallback }: { error: unknown; fallback: string }) {
  if (!error) return null;

  if (!(error instanceof ApiError)) {
    return <Alert tone="danger">{fallback}</Alert>;
  }

  const details = Object.values(error.fieldErrors ?? {}).filter(
    (detail) => detail && detail !== error.message,
  );

  if (details.length === 0) {
    return <Alert tone="danger">{error.message}</Alert>;
  }

  return (
    <Alert tone="danger" title={error.message}>
      <ul className={styles.errorList}>
        {details.map((detail) => (
          <li key={detail}>{detail}</li>
        ))}
      </ul>
    </Alert>
  );
}
