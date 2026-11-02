'use client';

import { useEffect, useRef, useState } from 'react';

import type { RefundPolicy } from '@/api/policies';
import styles from './RefundPolicyConsent.module.css';

/** 바닥 판정 여유. 소수점 스크롤·확대 배율 때문에 정확히 0 이 되지 않는 브라우저가 있다. */
const BOTTOM_SLACK_PX = 8;

/**
 * 환불규정 동의 — 전문을 끝까지 읽어야 체크할 수 있다.
 *
 * <h2>왜 끝까지 읽게 하는가</h2>
 * 이 규정의 핵심(단순 변심 환불 불가)은 분쟁에서 "고객이 읽을 수 있었다"가 아니라
 * "읽었다"에 가깝게 보여야 힘이 있다. 체크박스를 처음부터 열어 두면 안 읽고 누른
 * 것과 구분되지 않는다. 대신 「맨 아래로」 버튼을 두어 긴 글을 손가락으로 끝까지
 * 밀어야 하는 수고는 덜어 준다.
 *
 * <h2>규정이 바뀌면 처음부터</h2>
 * 읽음 상태는 판 번호에 매여 있다. 보고 있는 사이 관리자가 새 판을 발행해 판 번호가
 * 바뀌면 읽음이 되돌아가고, 동의 여부는 부모가 "동의한 판 번호 = 지금 판 번호" 로
 * 계산하므로 따로 되돌릴 것 없이 저절로 무효가 된다.
 */
export function RefundPolicyConsent({
  policy,
  isLoading,
  checked,
  onChange,
}: {
  policy: RefundPolicy | undefined;
  isLoading: boolean;
  checked: boolean;
  onChange: (checked: boolean) => void;
}) {
  const boxRef = useRef<HTMLDivElement>(null);

  // 읽음 상태를 판 번호와 함께 든다. 판이 바뀌면 렌더 중에 즉시 되돌린다 —
  // effect 로 하면 한 프레임 동안 "새 판인데 읽음" 인 화면이 지나간다.
  const policyId = policy?.id;
  const [read, setRead] = useState<{ id: number | undefined; done: boolean }>({
    id: policyId,
    done: false,
  });
  if (read.id !== policyId) {
    setRead({ id: policyId, done: false });
  }
  const readToEnd = read.id === policyId && read.done;

  const markIfAtBottom = () => {
    const el = boxRef.current;
    if (!el) return;
    if (el.scrollTop + el.clientHeight >= el.scrollHeight - BOTTOM_SLACK_PX) {
      setRead((prev) => (prev.done ? prev : { ...prev, done: true }));
    }
  };

  // 글이 상자보다 짧아 스크롤할 게 없으면 바로 "읽음"이다. 그려진 뒤에 재야 해서
  // 한 프레임 미룬다.
  useEffect(() => {
    const frame = requestAnimationFrame(markIfAtBottom);
    return () => cancelAnimationFrame(frame);
  }, [policyId]);

  const scrollToBottom = () => {
    const el = boxRef.current;
    if (!el) return;
    el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' });
  };

  if (isLoading) {
    return <p className={styles.loading}>환불규정을 불러오는 중…</p>;
  }
  if (!policy) {
    return (
      <p className={styles.loadFailed}>
        환불규정을 불러오지 못했습니다. 새로고침한 뒤 다시 시도해 주세요 — 규정에 동의해야
        주문할 수 있습니다.
      </p>
    );
  }

  return (
    <div>
      <div
        ref={boxRef}
        className={styles.box}
        onScroll={markIfAtBottom}
        tabIndex={0}
        role="region"
        aria-label="교환·반품·환불 규정 전문"
      >
        <p className={styles.text}>{policy.content}</p>
      </div>

      <div className={styles.bar}>
        <span className={styles.hint} aria-live="polite">
          {readToEnd
            ? '끝까지 확인하셨습니다.'
            : '규정을 끝까지 읽으면 동의할 수 있습니다.'}
        </span>
        {!readToEnd && (
          <button type="button" className={styles.toBottom} onClick={scrollToBottom}>
            맨 아래로 ↓
          </button>
        )}
      </div>

      <label className={styles.check}>
        <input
          type="checkbox"
          checked={checked}
          disabled={!readToEnd}
          onChange={(e) => onChange(e.target.checked)}
        />
        <span>
          환불규정을 모두 읽었으며, <strong>청약철회가 제한되는 상품</strong>임에 동의합니다.
          <span className={styles.required}>(필수)</span>
        </span>
      </label>
    </div>
  );
}
