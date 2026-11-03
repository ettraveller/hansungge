'use client';

import type { CSSProperties, ElementType, ReactNode } from 'react';
import { useReveal } from './useReveal';
import styles from './Reveal.module.css';

interface Props {
  children: ReactNode;
  /** 감싸는 태그. 기본 div — 목록 안이면 li 로 바꾼다 */
  as?: ElementType;
  /** 올라오는 거리. 같은 묶음에서 값을 달리하면 지연 없이도 시차가 생긴다 */
  rise?: 'sm' | 'md' | 'lg';
  /** 시작을 늦춘다(ms). 줄줄이 늘어선 카드에 쓴다 */
  delay?: number;
  className?: string;
}

const RISE: Record<NonNullable<Props['rise']>, string> = {
  sm: 'var(--rise-sm)',
  md: 'var(--rise)',
  lg: 'var(--rise-lg)',
};

/**
 * 화면에 들어오면 떠오르는 블록.
 *
 * <h2>왜 라이브러리를 쓰지 않나</h2>
 * 하는 일이 "보이면 클래스 하나 붙인다"뿐이다. 이걸 위해 GSAP·Framer Motion 을
 * 얹으면 번들에 수십 KB 가 붙고 메인 화면의 첫 렌더가 그만큼 늦어진다 —
 * 빨리 뜨는 것이 고급스러운 움직임보다 먼저다.
 *
 * <h2>본문은 늘 HTML 에 있다</h2>
 * 내용을 조건부로 렌더링하지 않는다. 투명도와 위치만 CSS 가 건드리므로
 * 검색엔진과 읽어 주는 도구에는 처음부터 전부 보인다.
 */
export function Reveal({ children, as: Tag = 'div', rise = 'md', delay = 0, className }: Props) {
  const ref = useReveal<HTMLElement>(styles.on);

  const style = {
    '--reveal-rise': RISE[rise],
    ...(delay > 0 ? { '--reveal-delay': `${delay}ms` } : null),
  } as CSSProperties;

  return (
    <Tag
      ref={ref}
      className={className ? `${styles.reveal} ${className}` : styles.reveal}
      style={style}
    >
      {children}
    </Tag>
  );
}
