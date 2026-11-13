import { api } from '@/api/client';

/**
 * 배너를 만드는 두 가지 길 (V45).
 *
 * - `IMAGE` — 사진 한 장이 전부다. 문구·버튼까지 이미지 안에 들어간다.
 * - `DESIGN` — 배경 위에 글자와 버튼을 쌓는다. 관리자가 화면에서 짠다.
 */
export type BannerKind = 'IMAGE' | 'DESIGN';

export type BannerAlign = 'LEFT' | 'CENTER' | 'RIGHT';
export type BannerTextStyle = 'EYEBROW' | 'TITLE' | 'HIGHLIGHT' | 'DESC';
export type BannerButtonLook = 'GOLD' | 'OUTLINE' | 'SOLID';
export type BannerOverlay = 'GRADIENT' | 'SOLID' | 'NONE';

export interface BannerBlock {
  id: string;
  type: 'TEXT' | 'BUTTON';
  /** TEXT 의 역할. 크기·굵기·색의 기본값이다 — 아래 값이 있으면 그것이 이긴다 */
  style?: BannerTextStyle | null;
  text: string;
  /** #rrggbb. 비우면 그 역할의 기본색 */
  color?: string | null;
  href?: string | null;
  look?: BannerButtonLook | null;
  /** 글자 크기(px). 비우면 역할의 기본 크기. 좁은 화면에서는 0.6 배로 줄어든다 */
  size?: number | null;
  bold?: boolean | null;
  /** 가로·세로 자리(%). <b>둘 다</b> 있을 때만 그 자리에 선다. 좁은 화면에서는 버리고 쌓는다 */
  x?: number | null;
  y?: number | null;
}

/**
 * 꾸민 배너의 내용.
 *
 * <p>요소를 <b>위에서 아래로 쌓는다.</b> 좌표가 없는 이유는 배너의 비율이 폭에 따라
 * 뒤집히기 때문이다 — PC 에서 오른쪽 끝에 둔 글자가 휴대폰에서는 화면 밖이라,
 * 좌표를 받으면 배너 한 장마다 배치를 두 번 잡아야 한다.
 *
 * <p>값은 서버가 이미 다듬어 보낸다({@code BannerContentValidator}). 그래도 그리는
 * 쪽에서 한 번 더 거른다 — 이 값이 {@code style} 로 들어가기 때문이다.
 */
export interface BannerContent {
  background: {
    color?: string | null;
    imageKey?: string | null;
    mobileImageKey?: string | null;
    /** 0 · 4 · 8 · 16 */
    blur: number;
    overlay: BannerOverlay;
  };
  align: BannerAlign;
  blocks: BannerBlock[];
  /** 휴대폰에서만 다르게 할 것. 없으면 PC 와 같다 */
  mobile?: { align?: BannerAlign | null; hide?: string[] | null } | null;
}

/** 손님 화면에 걸리는 메인 배너. */
export interface HeroBanner {
  id: number;
  kind: BannerKind;
  /** DESIGN 일 때의 내용. IMAGE 면 없다 */
  content?: BannerContent | null;
  /** IMAGE 일 때의 사진 */
  imageKey?: string | null;
  /** 휴대폰용 이미지. 없으면 가로용 한 장을 그대로 쓴다 */
  mobileImageKey?: string | null;
  linkHref?: string | null;
  altText?: string | null;
  sort: number;
  active: boolean;
}

export const bannerApi = {
  // 관리자가 바꾸면 바로 보여야 한다. 메인은 자주 열리는 화면이라 캐시가 남으면
  // "바꿨는데 안 바뀐다"가 된다.
  list: () => api.get<HeroBanner[]>('/api/v1/banners', { cache: 'no-store' }),
};
