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

/**
 * 관리자가 올린 배너 한 장.
 *
 * <p>문구·버튼까지 이미지 안에 들어간다. 그래서 {@link Slide} 처럼 글자를 받지 않고
 * 화면 전체를 사진으로 덮는다 — 글자를 밖에 두면 사진의 어느 자리에 글이 올라갈지를
 * 화면 코드가 정하게 되고, 사진이 바뀔 때마다 그 자리가 어긋난다.
 */
export interface BannerSlide {
  id: number;
  image: string;
  /** 휴대폰용 이미지. 없으면 {@link image} 한 장이 두 폭을 다 맡는다 */
  mobileImage?: string | null;
  /** 누르면 갈 곳. 없으면 누를 수 없는 배너다 */
  href?: string | null;
  /** 화면 낭독기가 읽을 설명. 이미지 안의 글자는 읽히지 않는다 */
  alt?: string | null;
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
export function HeroSlider({ slides, banners }: { slides: Slide[]; banners?: BannerSlide[] }) {
  // 관리자가 올린 배너가 있으면 그것만 쓴다. 섞지 않는다 — 글자가 밖에 있는 슬라이드와
  // 이미지 한 장짜리가 번갈아 나오면 같은 배너의 두 가지 모양처럼 보인다.
  const uploaded = banners ?? [];
  const useUploaded = uploaded.length > 0;
  const count = useUploaded ? uploaded.length : slides.length;

  const [index, setIndex] = useState(0);
  const [paused, setPaused] = useState(false);
  const timer = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    if (paused || count <= 1) return;
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

    timer.current = setInterval(() => {
      setIndex((i) => (i + 1) % count);
    }, AUTOPLAY_MS);

    return () => {
      if (timer.current) clearInterval(timer.current);
    };
  }, [paused, count]);

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
    setIndex((i) => (i + delta + count) % count);

  const onPointerDown = (e: React.PointerEvent) => {
    if (e.pointerType === 'mouse' && e.button !== 0) return;
    start.current = { x: e.clientX, y: e.clientY };
    swiped.current = false;
  };

  const onPointerUp = (e: React.PointerEvent) => {
    const from = start.current;
    start.current = null;
    if (!from || count <= 1) return;

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
      {useUploaded &&
        uploaded.map((banner, i) => (
          <div
            key={banner.id}
            className={i === index ? styles.slideOn : styles.slide}
            aria-hidden={i !== index}
          >
            {/* 글자가 이미지 안에 있으므로 화면 전체를 덮는다. 오른쪽만 덮는 .bg 와
                달리 어둠도 깔지 않는다 — 깔면 디자인한 색이 그대로 나오지 않는다.

                휴대폰용이 따로 올라온 배너는 폭에 따라 한쪽만 보인다 (.bgWide/.bgNarrow).
                안 쓰는 쪽은 display:none 이라 브라우저가 내려받지도 않는다. 휴대폰용이
                없으면 가로용 한 장이 두 폭을 다 맡는다. */}
            <div
              className={
                banner.mobileImage ? `${styles.bgFull} ${styles.bgWide}` : styles.bgFull
              }
              style={{ backgroundImage: `url(${banner.image})` }}
              role="img"
              aria-label={banner.alt ?? ''}
            />
            {banner.mobileImage && (
              <div
                className={`${styles.bgFull} ${styles.bgNarrow}`}
                style={{ backgroundImage: `url(${banner.mobileImage})` }}
                role="img"
                aria-label={banner.alt ?? ''}
              />
            )}
            {/* 주소가 없으면 누를 수 없는 배너다. 빈 링크를 두면 눌리는데 아무 일도
                일어나지 않아, 쓰는 사람은 고장으로 읽는다. */}
            {banner.href && (
              <Link
                href={banner.href}
                className={styles.bannerLink}
                tabIndex={i === index ? 0 : -1}
                aria-label={banner.alt ?? '배너 바로가기'}
              />
            )}
          </div>
        ))}

      {!useUploaded &&
        slides.map((slide, i) => (
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
              {/*
                제목을 줄마다 가림상자로 싼다. 안쪽 글자가 아래에서 올라오면서
                상자 밖에 있던 동안은 보이지 않는다 — 투명도만 바꾸는 것과 달리
                "어딘가에서 올라온다"는 느낌이 생긴다.

                ⚠️ 글자를 한 자씩 쪼개지 않는다. 한글을 쪼개면 읽어 주는 도구가
                자모를 끊어 읽고, 복사했을 때 글자 사이에 공백이 낀다. 줄 단위로도
                같은 인상이 나고 본문은 그대로 성한다.
              */}
              <h2>
                <span className={styles.line}>
                  <span>{slide.title}</span>
                </span>
                {slide.highlight && (
                  <span className={styles.line}>
                    <em>{slide.highlight}</em>
                  </span>
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

      {count > 1 && (
        <div className={`wrap ${styles.dotsWrap}`}>
          <div className={styles.dots} role="tablist" aria-label="배너 선택">
            {/* 점은 몇 장이든 장수만큼 생긴다. 어느 쪽을 쓰든 같은 수를 센다. */}
            {Array.from({ length: count }, (_, i) => (
              <button
                key={useUploaded ? uploaded[i].id : slides[i].title}
                type="button"
                role="tab"
                aria-selected={i === index}
                aria-label={`${i + 1}번 배너${
                  useUploaded ? '' : `: ${slides[i].title}`
                }`}
                className={i === index ? styles.dotOn : styles.dot}
                onClick={() => setIndex(i)}
              />
            ))}
            <span className={styles.counter}>
              <b>{index + 1}</b> / {count}
            </span>
          </div>
        </div>
      )}
    </section>
  );
}
