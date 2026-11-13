import Link from 'next/link';

import type { BannerContent, BannerImageEffect } from '@/api/banners';
import { mediaUrl } from '@/utils/media';
import styles from './HeroSlider.module.css';

/**
 * 색으로 쓸 수 있는 값인가.
 *
 * <p>서버가 이미 걸렀다. 여기서 다시 보는 이유는 이 값이 {@code style} 로 들어가기
 * 때문이다 — 앞 문이 한 겹 뚫렸을 때 {@code url(https://남의서버/…)} 한 줄이면
 * 우리 화면을 연 사람의 접속이 밖으로 나간다. 두 문을 다 뚫어야 하게 둔다.
 */
const COLOR = /^#[0-9a-fA-F]{6}$/;

function color(value: string | null | undefined): string | undefined {
  return value && COLOR.test(value) ? value : undefined;
}

/** 고를 수 있는 흐림. 자유 숫자를 받으면 blur(9999px) 로 화면이 지워진다. */
const BLUR_STEPS = [0, 4, 8, 16];

/** 글자 크기(px) 의 위아래 끝. 서버도 같은 값으로 당긴다. */
const MIN_SIZE = 10;
const MAX_SIZE = 120;

/** 사진 너비(px) 의 위아래 끝. 글자와 달리 배너 폭의 절반까지는 쓸 일이 있다. */
const MIN_IMAGE = 20;
const MAX_IMAGE = 800;

/** 고를 수 있는 투명도(%). 0 을 그리면 "사진이 사라졌다" 가 된다. */
const OPACITY_STEPS = [100, 85, 70, 50];

const FADE_CLASS: Record<string, string> = {
  SOFT: styles.dFadeSoft,
  STRONG: styles.dFadeStrong,
  LEFT: styles.dFadeLeft,
  RIGHT: styles.dFadeRight,
  TOP: styles.dFadeTop,
  BOTTOM: styles.dFadeBottom,
};

const VEIL_CLASS: Record<string, string> = {
  GRADIENT: styles.dImgVeilGradient,
  SOLID: styles.dImgVeilSolid,
};

/** 비율 맞춰 잘라내기. 상품 사진이 쓰는 그것과 같다. */
const CROP_RATIO: Record<string, string> = {
  SQUARE: '1 / 1',
  WIDE: '16 / 9',
  BANNER: '16 / 7',
};

const RADIUS_VALUE: Record<string, string> = {
  NONE: '0',
  SMALL: 'var(--radius-sm)',
  LARGE: '24px',
};

/**
 * 사진 효과 → CSS 변수.
 *
 * <p>서버가 이미 걸렀다. 여기서 다시 보는 이유는 색과 같다 — 이 값이 {@code style} 로
 * 들어가므로, 앞 문이 한 겹 뚫렸을 때 두 번째 문이 있어야 한다.
 */
function effectCss(effect: BannerImageEffect | null | undefined): React.CSSProperties {
  if (!effect) return {};
  const blur = BLUR_STEPS.includes(effect.blur ?? 0) ? effect.blur ?? 0 : 0;
  const opacity = OPACITY_STEPS.includes(effect.opacity ?? 100) ? effect.opacity ?? 100 : 100;
  return {
    '--bimgblur': `${blur}px`,
    '--bimgop': `${opacity / 100}`,
    '--bimgr': RADIUS_VALUE[effect.radius ?? 'SMALL'] ?? RADIUS_VALUE.SMALL,
    '--bimggray': effect.tone === 'GRAY' ? '1' : '0',
    ...(CROP_RATIO[effect.crop ?? 'NONE']
      ? { '--bimgar': CROP_RATIO[effect.crop as string] }
      : {}),
  } as React.CSSProperties;
}

/** 좁은 화면에서 px 크기를 줄이는 비율. PC 제목 48px → 휴대폰 29px. */
const MOBILE_SCALE = 0.6;

/** 숫자로 들어와야 할 자리. NaN·Infinity·문자열이 섞여 와도 칸만 비운다. */
function num(value: unknown, min: number, max: number): number | undefined {
  if (typeof value !== 'number' || !Number.isFinite(value)) return undefined;
  return Math.max(min, Math.min(max, value));
}

/** 글자의 역할 → 쓰는 자리. 크기·굵기는 전부 CSS 가 들고 있다. */
const TEXT_CLASS: Record<string, string> = {
  EYEBROW: styles.dEyebrow,
  TITLE: styles.dTitle,
  HIGHLIGHT: styles.dHighlight,
  DESC: styles.dDesc,
};

const ALIGN_CLASS: Record<string, string> = {
  LEFT: styles.dAlignLeft,
  CENTER: styles.dAlignCenter,
  RIGHT: styles.dAlignRight,
};

const LOOK_CLASS: Record<string, string> = {
  GOLD: styles.cta,
  OUTLINE: `${styles.cta} ${styles.dCtaOutline}`,
  SOLID: `${styles.cta} ${styles.dCtaSolid}`,
};

/**
 * 화면에서 꾸민 배너 한 장.
 *
 * <h2>자리는 넓은 화면에만 건다</h2>
 * 블록은 기본적으로 배열 순서대로 위에서 아래로 쌓인다. {@code x}·{@code y}(%)가
 * <b>둘 다</b> 있는 블록만 그 자리에 세운다. 좁은 화면(≤960px)에서는 그 좌표를 버리고
 * 다시 쌓는다 — 배너 비율이 폭에 따라 뒤집히므로(PC 2.3:1, 휴대폰 0.85:1) PC 에서
 * 오른쪽 끝에 둔 글자는 휴대폰에서 화면 밖이다. 버리는 쪽이 배치를 두 번 잡는 것보다
 * 낫다.
 *
 * <h2>사진 블록</h2>
 * {@code IMAGE} 블록은 업로드한 사진 한 장을 배너 안에 얹는다. {@code size} 는 글자
 * 크기가 아니라 <b>너비</b>(px)이고, {@code text} 는 눈으로 보지 않는 사람에게 읽어
 * 줄 대체 텍스트다. 글자·버튼과 똑같이 끌어다 둘 수 있다.
 *
 * <h2>px 로 고른 크기는 좁은 화면에서 줄어든다</h2>
 * 그대로 두면 PC 에서 고른 48px 이 휴대폰에서도 48px 이라 제목 한 줄이 배너를 덮는다.
 * 크기를 {@code --bs}·{@code --bsm} 두 변수로 넘기고 좁은 화면에서는 CSS 가 뒤엣것을
 * 쓴다. 크기를 고르지 않은 블록은 역할(제목·강조·설명)의 값을 그대로 쓴다.
 *
 * @param editable 관리자 화면에서 고치는 중이면 글자 자리를 편집기에 내준다.
 *                 손님 화면은 넘기지 않으므로 글자 그대로 나간다.
 */
export function BannerDesign({
  content,
  renderText,
}: {
  content: BannerContent;
  renderText?: (blockId: string, value: string) => React.ReactNode;
}) {
  const { background, align, blocks, mobile } = content;

  const image = mediaUrl(background.imageKey);
  const mobileImage = mediaUrl(background.mobileImageKey);
  const blur = BLUR_STEPS.includes(background.blur) ? background.blur : 0;
  const hidden = new Set(mobile?.hide ?? []);

  // 흐린 사진은 가장자리가 비어 보인다. 블러 반지름만큼 번지면서 바깥쪽이 투명해지기
  // 때문이다. 조금 키워서 그 띠를 화면 밖으로 밀어낸다.
  const bgStyle = (url: string): React.CSSProperties =>
    blur > 0
      ? { backgroundImage: `url(${url})`, filter: `blur(${blur}px)`, transform: 'scale(1.06)' }
      : { backgroundImage: `url(${url})` };

  return (
    <>
      <div className={styles.dCanvas} style={{ background: color(background.color) }}>
        {image && (
          <div
            className={mobileImage ? `${styles.dBg} ${styles.bgWide}` : styles.dBg}
            style={bgStyle(image)}
          />
        )}
        {mobileImage && (
          <div className={`${styles.dBg} ${styles.bgNarrow}`} style={bgStyle(mobileImage)} />
        )}
        {background.overlay !== 'NONE' && (
          <div
            className={
              background.overlay === 'SOLID' ? styles.dVeilSolid : styles.dVeilGradient
            }
          />
        )}
      </div>

      <div className={`wrap ${styles.copyWrap} ${styles.dWrap}`}>
        <div
          data-stack
          className={`${styles.dStack} ${ALIGN_CLASS[align] ?? styles.dAlignLeft} ${
            mobile?.align ? styles[`dm${mobile.align}`] ?? '' : ''
          }`}
        >
          {blocks.map((block) => {
            // 휴대폰에서 숨긴 블록도 DOM 에는 둔다. 폭으로 숨기는 것이라 그려 두어야
            // 창을 넓혔을 때 바로 나타나고, 초기 HTML 에서 빠지지 않는다 (SEO).
            const hideClass = hidden.has(block.id) ? ` ${styles.dHideOnMobile}` : '';

            // 자리는 x·y 가 <b>둘 다</b> 있을 때만 건다. 한쪽만 걸면 다른 쪽은 흐름이
            // 정하는 자리에 남아, 끌어다 둔 적 없는 곳에 글자가 선다.
            const x = num(block.x, 0, 100);
            const y = num(block.y, 0, 100);
            const placed = x !== undefined && y !== undefined;
            const image = block.type === 'IMAGE';
            const size = image
              ? num(block.size, MIN_IMAGE, MAX_IMAGE)
              : num(block.size, MIN_SIZE, MAX_SIZE);

            // 크기는 px 가 아니라 변수로 넘긴다. 좁은 화면에서 같은 변수를 CSS 가
            // 0.6 배로 다시 잡는다 — px 를 직접 박으면 휴대폰에서도 48px 이다.
            const css = {
              color: color(block.color),
              fontWeight: block.bold === true ? 800 : block.bold === false ? 400 : undefined,
              ...(size ? { '--bs': `${size}px`, '--bsm': `${Math.round(size * MOBILE_SCALE)}px` } : {}),
              ...(placed ? { left: `${x}%`, top: `${y}%` } : {}),
            } as React.CSSProperties;

            const extra = `${hideClass}${placed ? ` ${styles.dPlaced}` : ''}${
              size ? ` ${styles.dSized}` : ''
            }`;

            if (image) {
              const src = mediaUrl(block.imageKey);
              if (!src) return null;

              const fade = FADE_CLASS[block.effect?.fade ?? 'NONE'] ?? '';
              const veil = VEIL_CLASS[block.effect?.veil ?? 'NONE'] ?? '';
              const shadow = block.effect?.shadow === 'SOFT' ? styles.dImgShadow : '';

              return (
                // 사진은 자식을 가질 수 없어서 상자로 한 번 싼다. 손잡이(편집기)가
                // 걸릴 자리이면서, 너비를 px 로 받는 자리이기도 하다.
                <span
                  key={block.id}
                  data-block={block.id}
                  className={`${styles.dImage}${extra}${fade ? ` ${fade}` : ''}${
                    veil ? ` ${veil}` : ''
                  }${shadow ? ` ${shadow}` : ''}`}
                  style={{ ...css, ...effectCss(block.effect) }}
                >
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img src={src} alt={block.text} />
                  {renderText?.(block.id, block.text)}
                </span>
              );
            }

            if (block.type === 'BUTTON') {
              return (
                <Link
                  key={block.id}
                  data-block={block.id}
                  href={block.href ?? '/'}
                  className={`${LOOK_CLASS[block.look ?? 'GOLD'] ?? styles.cta}${extra}`}
                  style={css}
                >
                  {renderText ? renderText(block.id, block.text) : block.text}
                  <span className={styles.arr} aria-hidden="true">
                    →
                  </span>
                </Link>
              );
            }

            return (
              <p
                key={block.id}
                data-block={block.id}
                className={`${TEXT_CLASS[block.style ?? 'DESC'] ?? styles.dDesc}${extra}`}
                style={css}
              >
                {renderText ? renderText(block.id, block.text) : block.text}
              </p>
            );
          })}
        </div>
      </div>
    </>
  );
}
