import Link from 'next/link';

import type { BannerContent } from '@/api/banners';
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
 * <h2>쌓기만 한다</h2>
 * 블록을 배열 순서대로 위에서 아래로 그린다. 좌표가 없으므로 PC(1280×560)와
 * 휴대폰(390×460)이 <b>같은 내용으로 각자 맞게</b> 그려진다 — 지금 기본 배너가 쓰는
 * 바로 그 CSS 를 그대로 쓴다.
 *
 * <h2>크기를 px 로 받지 않는다</h2>
 * 받으면 PC 에서 고른 48px 이 휴대폰에서도 48px 이라 제목 한 줄이 배너를 덮는다.
 * 역할(제목·강조·설명)만 고르면 두 폭의 크기는 CSS 가 안다.
 *
 * @param editable 관리자 화면에서 고치는 중이면 글자 자리를 편집기에 내준다.
 *                 손님 화면은 넘기지 않으므로 글자 그대로 나간다.
 */
export function BannerDesign({
  content,
  renderText,
  trailing,
}: {
  content: BannerContent;
  renderText?: (blockId: string, value: string) => React.ReactNode;
  /** 쌓인 것들 맨 아래에 끼워 넣을 것. 편집기가 「+ 글자 · + 버튼」을 여기에 둔다. */
  trailing?: React.ReactNode;
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

      <div className={`wrap ${styles.copyWrap}`}>
        <div
          className={`${styles.dStack} ${ALIGN_CLASS[align] ?? styles.dAlignLeft} ${
            mobile?.align ? styles[`dm${mobile.align}`] ?? '' : ''
          }`}
        >
          {blocks.map((block) => {
            // 휴대폰에서 숨긴 블록도 DOM 에는 둔다. 폭으로 숨기는 것이라 그려 두어야
            // 창을 넓혔을 때 바로 나타나고, 초기 HTML 에서 빠지지 않는다 (SEO).
            const hideClass = hidden.has(block.id) ? ` ${styles.dHideOnMobile}` : '';

            if (block.type === 'BUTTON') {
              return (
                <Link
                  key={block.id}
                  href={block.href ?? '/'}
                  className={`${LOOK_CLASS[block.look ?? 'GOLD'] ?? styles.cta}${hideClass}`}
                  style={{ color: color(block.color) }}
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
                className={`${TEXT_CLASS[block.style ?? 'DESC'] ?? styles.dDesc}${hideClass}`}
                style={{ color: color(block.color) }}
              >
                {renderText ? renderText(block.id, block.text) : block.text}
              </p>
            );
          })}
          {trailing}
        </div>
      </div>
    </>
  );
}
