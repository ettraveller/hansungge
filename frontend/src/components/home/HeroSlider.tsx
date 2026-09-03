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

  return (
    <section
      className={styles.hero}
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      onFocus={() => setPaused(true)}
      onBlur={() => setPaused(false)}
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
