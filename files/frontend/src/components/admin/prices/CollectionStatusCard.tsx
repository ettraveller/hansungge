'use client';

import { Alert } from '../Alert';
import { Panel } from '../Panel';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { useCollectionStatus } from '@/hooks/admin/usePrices';
import { formatDateTime } from '@/utils/format';
import type { FetchResult } from '@/types/admin';
import { ApiError } from '@/types/api';
import styles from '../admin.module.css';

const RESULT_LABEL: Record<FetchResult, string> = {
  SUCCESS: '성공',
  SKIPPED_DUPLICATE: '동일값 건너뜀',
  HTTP_ERROR: 'HTTP 오류',
  TIMEOUT: '응답 시간 초과',
  PARSE_ERROR: '응답 해석 실패',
  INVALID_VALUE: '값 검증 실패',
  EXCEPTION: '예외 발생',
};

/**
 * 시세 수집 상태.
 *
 * 수집이 실패해도 <b>기존 시세는 유지된다</b> — 실패를 0 이나 NULL 로 반영하지 않는다
 * (설계서 원칙 P6). 그래서 화면이 정상으로 보여도 실제로는 며칠 전 값일 수 있다.
 * 마지막 성공 시각을 눈에 띄게 두는 이유다.
 */
export function CollectionStatusCard() {
  const { data, isLoading, error } = useCollectionStatus();

  return (
    <Panel title="수집 상태" note="수집이 실패해도 기존 시세는 유지된다. 마지막 성공 시각을 확인할 것.">
      {error && (
        <Alert tone="danger">
          {error instanceof ApiError ? error.message : '수집 상태를 불러오지 못했습니다.'}
        </Alert>
      )}

      {isLoading || !data ? (
        <p className={styles.loading}>불러오는 중…</p>
      ) : (
        <>
          {data.neverSucceeded && (
            <div style={{ marginBottom: 14 }}>
              <Alert tone="info" title="자동 수집 성공 기록이 없습니다">
                외부 시세 연동(KRX)은 아직 붙지 않았다. 현재는 관리자 수동 고시만 쓰인다 —
                정상이다.
              </Alert>
            </div>
          )}

          {!data.neverSucceeded && data.failureCount > 0 && (
            <div style={{ marginBottom: 14 }}>
              <Alert tone="warning" title={`연속 실패 ${data.failureCount}회`}>
                {data.message}
              </Alert>
            </div>
          )}

          <div className={styles.cards}>
            <div className={styles.stat}>
              <span className={styles.statLabel}>스케줄러</span>
              <span className={styles.statValueSm}>
                <StatusBadge tone={data.schedulerEnabled ? 'success' : 'neutral'}>
                  {data.schedulerEnabled ? '동작 중' : '중지'}
                </StatusBadge>
              </span>
              <span className={styles.hint}>
                주기 <code className={styles.inlineCode}>{data.cron}</code>
              </span>
            </div>

            <div className={styles.stat}>
              <span className={styles.statLabel}>마지막 시도</span>
              <span className={styles.statValueSm}>{formatDateTime(data.lastAttemptAt)}</span>
              <span className={styles.hint}>
                {data.lastAttemptResult ? RESULT_LABEL[data.lastAttemptResult] : '기록 없음'}
              </span>
            </div>

            <div className={styles.stat}>
              <span className={styles.statLabel}>마지막 성공</span>
              <span className={styles.statValueSm}>{formatDateTime(data.lastSuccessAt)}</span>
              <span className={styles.hint}>이 시각 이후로는 수집 값이 갱신되지 않았다.</span>
            </div>

            <div className={styles.stat}>
              <span className={styles.statLabel}>누적 실패</span>
              <span className={styles.statValue}>{data.failureCount}</span>
              <span className={styles.hint}>성공하면 0 으로 돌아간다.</span>
            </div>
          </div>
        </>
      )}
    </Panel>
  );
}
