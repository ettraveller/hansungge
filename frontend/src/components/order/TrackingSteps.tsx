'use client';

import type { TrackingStep } from '@/types/order';
import { formatDateTime } from '@/utils/format';
import styles from './TrackingSteps.module.css';

/**
 * 우체국이 준 배송 진행 단계.
 *
 * <h2>최신이 위다</h2>
 * 이 칸을 보는 이유는 "지금 어디까지 왔나" 하나다. 접수부터 시간순으로 쌓으면
 * 가장 궁금한 줄이 맨 아래로 내려가고, 단계가 늘수록 더 내려간다.
 *
 * <h2>택배사 표현을 바꾸지 않는다</h2>
 * "집화완료", "배송출발" 같은 말을 우리말로 다듬지 않는다. 고객이 우체국 조회
 * 화면에서 같은 번호를 찍어 보면 그쪽 표현이 나오는데, 두 화면이 다른 말을 하면
 * 어느 쪽이 맞는지 묻게 된다.
 *
 * <h2>시각이 없는 줄도 버리지 않는다</h2>
 * 날짜를 읽지 못한 단계는 시각 자리를 비워 두고 내용만 보여준다. 줄째로 빼면
 * 진행 중인데 "아무 일도 없음"으로 보인다.
 */
export function TrackingSteps({ steps, syncedAt }: {
  steps: TrackingStep[];
  syncedAt?: string | null;
}) {
  if (steps.length === 0) return null;

  const latest = [...steps].reverse();

  return (
    <div className={styles.box}>
      <p className={styles.title}>배송 진행</p>
      <ol className={styles.list}>
        {latest.map((step, index) => (
          <li key={`${step.at ?? ''}-${index}`} className={index === 0 ? styles.now : undefined}>
            <span className={styles.when}>{step.at ? formatDateTime(step.at) : '—'}</span>
            <span className={styles.what}>
              <strong>{step.what ?? '진행 중'}</strong>
              {step.where && <span className={styles.where}>{step.where}</span>}
            </span>
          </li>
        ))}
      </ol>
      {syncedAt && (
        <p className={styles.synced}>{formatDateTime(syncedAt)} 기준 · 우체국 제공</p>
      )}
    </div>
  );
}

/**
 * 서버가 준 JSON 문자열을 단계 배열로.
 *
 * <p>실패하면 빈 배열이다. 택배사 응답 모양이 바뀌어 우리가 못 읽게 되더라도
 * 주문 화면 전체가 깨지면 안 된다 — 송장번호와 조회 링크는 그대로 쓸 수 있다.
 */
export function parseTrackingSteps(json?: string | null): TrackingStep[] {
  if (!json) return [];
  try {
    const parsed: unknown = JSON.parse(json);
    if (!Array.isArray(parsed)) return [];
    return parsed.filter((s): s is TrackingStep => typeof s === 'object' && s !== null);
  } catch {
    return [];
  }
}
