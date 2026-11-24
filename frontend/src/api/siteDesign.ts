import { api } from '@/api/client';

/**
 * 화면 꾸미기 — 메인 구역의 차례·제목과 구역별 색.
 *
 * <p>원천은 백엔드의 {@code SiteDesignService} 다. 구역 이름({@link SectionKey})과 색
 * 자리 이름({@link ColorKey})은 그쪽 목록과 같아야 한다 — 서버는 모르는 이름을 버린다.
 *
 * <p>⚠️ 이 파일에 {@code 'use client'} 를 붙이지 않는다. 서버(레이아웃·메인)와
 * 관리자 화면이 같은 상수를 쓰는데, 클라이언트 모듈의 값을 서버가 import 하면
 * 프록시가 와서 전부 빈 값이 된다 (2026-11-18 「전체상품」 탭이 비었던 이유).
 */

export type SectionKey = 'PRICE' | 'PRODUCTS' | 'STORES' | 'NOTICES';

export interface DesignSection {
  key: SectionKey;
  /** 내걸었는가 */
  on: boolean;
  /** 비우면 기본 제목 */
  title: string;
  /** 비우면 기본 문구 */
  desc: string;
}

export type ColorKey =
  | 'accent'
  | 'topbarBg'
  | 'topbarFg'
  | 'headerBg'
  | 'headerFg'
  | 'priceTitle'
  | 'buttonBg'
  | 'buttonFg'
  | 'footerBg'
  | 'footerFg';

export interface SiteDesign {
  sections: DesignSection[];
  /** 자리 이름 → #rrggbb. 빈 값은 기본색 */
  colors: Record<ColorKey, string>;
}

/** 구역마다의 이름과 기본 글. 관리자 화면의 목록과 손님 화면의 기본값이 같이 쓴다. */
export const SECTION_META: Record<
  SectionKey,
  { label: string; title: string; desc: string | null; note: string }
> = {
  PRICE: {
    label: '오늘의 시세',
    title: '한성금거래소 오늘의 시세',
    desc: null,
    note: '시세판. 금·은·백금의 살 때·팔 때 가격',
  },
  PRODUCTS: {
    label: '상품',
    title: '판매 상품',
    desc: '고시 시세를 기준으로 판매가를 안내합니다',
    note: '카테고리 탭으로 나뉜 상품 목록',
  },
  STORES: {
    label: '지점 안내',
    title: '전국 지점 안내',
    desc: '가까운 지점에서 실물 확인과 매입 상담을 받으실 수 있습니다',
    note: '지역별 지점과 연락처',
  },
  NOTICES: {
    label: '공지사항',
    title: '공지사항',
    desc: null,
    note: '최근 공지 네 건',
  },
};

export const SECTION_ORDER: SectionKey[] = ['PRICE', 'PRODUCTS', 'STORES', 'NOTICES'];

export const MAX_SECTION_TITLE = 40;
export const MAX_SECTION_DESC = 80;

/**
 * 고를 수 있는 색 자리.
 *
 * <p>{@code cssVar} 는 tokens.css 의 변수다. 고른 색은 {@link themeCss} 가 그 변수를
 * {@code :root} 에서 덮어쓴다. {@code fallback} 은 고르지 않았을 때 화면에 나가는 색 —
 * 색판 단추에 "지금 이 색이다"를 보여 주는 데만 쓴다 (실제 기본값은 tokens.css 다).
 */
export const COLOR_META: {
  key: ColorKey;
  group: string;
  label: string;
  cssVar: string;
  fallback: string;
  note: string;
}[] = [
  { key: 'accent', group: '강조', label: '강조색', cssVar: '--gold-500', fallback: '#861f41', note: '링크·탭·뱃지·선 같은 포인트. 배너 기본 바탕도 이 색을 따른다' },
  { key: 'topbarBg', group: '상단 띠', label: '바탕', cssVar: '--strip-bg', fallback: '#861f41', note: '맨 위 가는 띠' },
  { key: 'topbarFg', group: '상단 띠', label: '글자', cssVar: '--topbar-fg', fallback: '#ffffff', note: '대표전화·링크·로그인·장바구니' },
  { key: 'headerBg', group: '헤더', label: '바탕', cssVar: '--header-bg', fallback: '#ffffff', note: '로고와 메뉴가 있는 자리' },
  { key: 'headerFg', group: '헤더', label: '메뉴 글자', cssVar: '--header-fg', fallback: '#0f1b2d', note: '메뉴와 검색창' },
  { key: 'priceTitle', group: '메인', label: '시세 제목', cssVar: '--price-title', fallback: '#861f41', note: '「오늘의 시세」 제목' },
  { key: 'buttonBg', group: '버튼', label: '바탕', cssVar: '--action', fallback: '#0f1b2d', note: '주문하기·바로 구매 같은 주요 버튼' },
  { key: 'buttonFg', group: '버튼', label: '글자', cssVar: '--on-action', fallback: '#ffffff', note: '주요 버튼 위의 글자' },
  { key: 'footerBg', group: '푸터', label: '바탕', cssVar: '--footer-bg', fallback: '#861f41', note: '맨 아래 회사 정보' },
  { key: 'footerFg', group: '푸터', label: '글자', cssVar: '--footer-fg', fallback: '#ffffff', note: '푸터의 글자. 진하기만 달리해서 쓴다' },
];

const EMPTY_COLORS = Object.fromEntries(COLOR_META.map((c) => [c.key, ''])) as Record<
  ColorKey,
  string
>;

/** 조회가 실패했을 때, 그리고 한 번도 저장한 적 없을 때의 모양. */
export const DEFAULT_DESIGN: SiteDesign = {
  sections: SECTION_ORDER.map((key) => ({ key, on: true, title: '', desc: '' })),
  colors: EMPTY_COLORS,
};

const HEX = /^#[0-9a-f]{6}$/i;

/**
 * 받은 값을 화면이 믿고 쓸 수 있는 모양으로 다듬는다.
 *
 * <p>서버가 이미 같은 일을 하지만, 미리보기는 <b>쿼리로 온 값</b>을 그린다 — 공개
 * 경로다. 색은 {@code <style>} 에 그대로 들어가므로 여기서도 한 번 더 거른다.
 */
export function normalizeDesign(raw: unknown): SiteDesign {
  const input = (raw ?? {}) as Partial<SiteDesign>;
  const seen = new Set<SectionKey>();
  const sections: DesignSection[] = [];

  for (const section of Array.isArray(input.sections) ? input.sections : []) {
    if (!section || !SECTION_ORDER.includes(section.key) || seen.has(section.key)) continue;
    seen.add(section.key);
    sections.push({
      key: section.key,
      on: section.on !== false,
      title: String(section.title ?? '').slice(0, MAX_SECTION_TITLE),
      desc: String(section.desc ?? '').slice(0, MAX_SECTION_DESC),
    });
  }
  for (const key of SECTION_ORDER) {
    if (!seen.has(key)) sections.push({ key, on: true, title: '', desc: '' });
  }

  const colors = { ...EMPTY_COLORS };
  for (const { key } of COLOR_META) {
    const value = (input.colors as Record<string, unknown> | undefined)?.[key];
    if (typeof value === 'string' && HEX.test(value)) colors[key] = value.toLowerCase();
  }
  return { sections, colors };
}

/**
 * 고른 색을 CSS 로. 고른 것이 없으면 빈 문자열이다.
 *
 * <p>tokens.css 의 변수를 {@code :root} 에서 덮어쓴다. 옅은 면·짙은 글자·선은 그
 * 변수에서 섞어 만들어지므로(color-mix) 한 줄만 바꿔도 전부 따라온다.
 */
export function themeCss(colors: Record<ColorKey, string>): string {
  const lines = COLOR_META.filter(({ key }) => HEX.test(colors[key] ?? '')).map(
    ({ key, cssVar }) => `${cssVar}:${colors[key]};`,
  );
  return lines.length > 0 ? `:root{${lines.join('')}}` : '';
}

export const siteDesignApi = {
  /** 관리자가 고치면 바로 보여야 한다 — 모든 화면의 색이 여기서 나온다. */
  get: () => api.get<SiteDesign>('/api/v1/site-design', { cache: 'no-store' }),
};
