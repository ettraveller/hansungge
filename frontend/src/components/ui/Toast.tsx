'use client';

import Link from 'next/link';
import { useEffect, useRef, useState } from 'react';

import styles from './Toast.module.css';

/**
 * 잠깐 떠 있다 사라지는 알림.
 *
 * <h2>왜 버튼 글자를 바꾸지 않는가</h2>
 * 「장바구니 담기」를 누르면 그 버튼이 「담았습니다」로 바뀌던 자리가 있었다. 두 가지가
 * 어긋난다 — <b>버튼 너비가 변해</b> 옆 버튼까지 밀리고, 한 번 바뀐 글자가 돌아오지 않아
 * 같은 상품을 다시 담을 수 없는 것처럼 보인다. 알림은 누른 자리가 아니라 따로 떠야 한다.
 *
 * <h2>스스로 사라진다</h2>
 * 닫기 버튼을 두지 않는다. 할 일이 하나 더 늘 뿐이다. 대신 <b>마우스를 올리거나 안의 링크로
 * 탭하면 시간이 멈춘다</b> — 링크를 누르려는 순간 사라지는 알림은 없느니만 못하다.
 *
 * <p>읽어 주는 장치를 위해 {@code role="status"} 다. assertive 가 아닌 이유는 이것이
 * 하던 일을 멈추고 들어야 할 소식이 아니기 때문이다.
 */
export function Toast({
  message,
  onDone,
  action,
  durationMs = 3000,
}: {
  /** 보일 글. null 이면 아무것도 그리지 않는다 */
  message: string | null;
  /** 사라질 때. 부모가 message 를 null 로 되돌린다 */
  onDone: () => void;
  /** 오른쪽에 붙는 링크 하나 */
  action?: { href: string; label: string };
  durationMs?: number;
}) {
  const [paused, setPaused] = useState(false);

  /*
    onDone 을 ref 에 담아 두는 이유: 부모가 매 렌더마다 새 화살표 함수를 넘기면
    아래 타이머 effect 의 의존성이 매번 달라져, 알림이 영영 사라지지 않는다.
    (렌더 중에 ref 를 건드리면 안 되므로 — react-hooks/refs — effect 에서 담는다.)
  */
  const done = useRef(onDone);
  useEffect(() => {
    done.current = onDone;
  });

  useEffect(() => {
    if (!message || paused) return;
    const id = setTimeout(() => done.current(), durationMs);
    return () => clearTimeout(id);
  }, [message, paused, durationMs]);

  if (!message) return null;

  return (
    <div
      className={styles.toast}
      role="status"
      aria-live="polite"
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      onFocus={() => setPaused(true)}
      onBlur={() => setPaused(false)}
    >
      <span className={styles.text}>{message}</span>
      {action && (
        <Link className={styles.action} href={action.href}>
          {action.label}
        </Link>
      )}
    </div>
  );
}
