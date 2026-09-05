'use client';

import Link from 'next/link';
import { useEffect, useRef, useState } from 'react';
import styles from './HeroSlider.module.css';

export interface Slide {
  eyebrow: string;
  title: string;
  highlight?: string;
  desc: string;
  href: string;
  cta: string;
  /** 배경 이미지 경로. 없으면 그라데이션만 */
  image?: string;
}

const AUTOPLAY_MS = 6000;

/**
 * 스와이프로 인정할 최소 이동 거리(px).
 *
 * <p>너무 짧게 잡으면 배너 안의 버튼을 누르려다 손가락이 조금 밀린 것까지 넘김으로
 * 읽는다. 너무 길게 잡으면 넘기려고 그은 손짓이 무시된다.
 */
const SWIPE_MIN_PX = 40;

/**
 * 메인 배너 슬라이드 (레퍼런스의 회전식 배너).
 *
 * <h2>첫 슬라이드는 항상 서버 HTML 에 있다</h2>
 * 슬라이드 전부를 DOM 에 그려두고 활성 항목만 보이게 한다. 활성 항목만 렌더링하면
 * 나머지 문구가 <b>초기 HTML 에서 빠져</b> 검색엔진이 읽지 못한다.
 *
 * <h2>자동 넘김을 멈추는 조건</h2>
 * 마우스가 올라가 있거나 키보드 포커스가 안에 있으면 멈춘다. 읽는 중에 배너가 넘어가면
 * 사용자가 방금 본 것을 다시 찾아야 한다. {@code prefers-reduced-motion} 을 켠 사용자는
 * 자동 넘김 자체를 하지 않는다 — 움직임이 불편해서 끈 설정이다.
 */
export function HeroSlider({ slides }: { slides: Slide[] }) {
  const [index, setIndex] = useState(0);
  const [paused, setPaused] = useState(false);
  const timer = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    if (paused || slides.length <= 1) return;
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

    timer.current = setInterval(() => {
      setIndex((i) => (i + 1) % slides.length);
    }, AUTOPLAY_MS);

    return () => {
      if (timer.current) clearInterval(timer.current);
    };
  }, [paused, slides.length]);

  /**
   * 손짓 넘김.
   *
   * <p>{@code touchstart/touchend} 대신 포인터 이벤트를 쓴다 — 손가락·펜·마우스 끌기가
   * 한 코드로 처리되고, 데스크톱에서 끌어 넘기는 것도 자연스럽게 따라온다.
   *
   * <p><b>{@code preventDefault} 를 부르지 않는다.</b> 가로 이동만 보고 판단하므로
   * 세로 스크롤은 브라우저가 그대로 처리한다 — 배너를 지나 아래로 내리려는 손짓을
   * 가로채면 페이지가 손에 붙어 버린다.
   */
  const start = useRef<{ x: number; y: number } | null>(null);
  /** 끌어서 넘긴 직후의 click 을 삼키기 위한 표시. 이게 없으면 스와이프가 링크를 연다. */
  const swiped = useRef(false);

  const go = (delta: number) =>
    setIndex((i) => (i + delta + slides.length) % slides.length);

  const onPointerDown = (e: React.PointerEvent) => {
    if (e.pointerType === 'mouse' && e.button !== 0) return;
    start.current = { x: e.clientX, y: e.clientY };
    swiped.current = false;
  };

  const onPointerUp = (e: React.PointerEvent) => {
    const from = start.current;
    start.current = null;
    if (!from || slides.length <= 1) return;

    const dx = e.clientX - from.x;
    const dy = e.clientY - from.y;
    // 세로로 더 많이 움직였으면 스크롤하려던 것이다. 넘기지 않는다.
    if (Math.abs(dx) < SWIPE_MIN_PX || Math.abs(dx) <= Math.abs(dy)) return;

    swiped.current = true;
    go(dx < 0 ? 1 : -1);
  };

  return (
    <section
      className={styles.hero}
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      onFocus={() => setPaused(true)}
      onBlur={() => setPaused(false)}
      onPointerDown={onPointerDown}
      onPointerUp={onPointerUp}
      onPointerCancel={() => {
        start.current = null;
      }}
      onClickCapture={(e) => {
        if (!swiped.current) return;
        e.preventDefault();
        e.stopPropagation();
        swiped.current = false;
      }}
      aria-roledescription="carousel"
      aria-label="메인 배너"
    >
      {slides.map((slide, i) => (
        <div
          key={slide.title}
          className={i === index ? styles.slideOn : styles.slide}
          aria-hidden={i !== index}
        >
          {slide.image && (
            <div className={styles.bg} style={{ backgroundImage: `url(${slide.image})` }} />
          )}
          <div className={`wrap ${styles.copyWrap}`}>
            <div className={styles.copy}>
              <small>{slide.eyebrow}</small>
              <h2>
                {slide.title}
                {slide.highlight && (
                  <>
                    <br />
                    <em>{slide.highlight}</em>
                  </>
                )}
              </h2>
              <p>{slide.desc}</p>
              <Link href={slide.href} className={styles.cta} tabIndex={i === index ? 0 : -1}>
                {slide.cta}
                <span className={styles.arr} aria-hidden="true">
                  ›
                </span>
              </Link>
            </div>
          </div>
        </div>
      ))}

      {slides.length > 1 && (
        <div className={`wrap ${styles.dotsWrap}`}>
          <div className={styles.dots} role="tablist" aria-label="배너 선택">
            {slides.map((slide, i) => (
              <button
                key={slide.title}
                type="button"
                role="tab"
                aria-selected={i === index}
                aria-label={`${i + 1}번 배너: ${slide.title}`}
                className={i === index ? styles.dotOn : styles.dot}
                onClick={() => setIndex(i)}
              />
            ))}
            <span className={styles.counter}>
              <b>{index + 1}</b> / {slides.length}
            </span>
          </div>
        </div>
      )}
    </section>
  );
}
