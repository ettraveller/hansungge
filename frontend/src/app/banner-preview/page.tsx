import type { Metadata } from 'next';

import type { HeroBanner } from '@/api/banners';
import { footerApi, type FooterInfo } from '@/api/footer';
import { load } from '@/api/server';
import { normalizeDesign, themeCss, type SiteDesign } from '@/api/siteDesign';
import { topLinksApi, type TopLink } from '@/api/topLinks';
import { FOOTER_DEFAULTS } from '@/data/company';
import { MobileTabBar } from '@/layouts/MobileTabBar';
// ⚠️ 고객이 보는 바로 그 컴포넌트들이다. 관리자용으로 비슷하게 다시 그리지 않는다 —
// 그러면 쇼핑몰 쪽이 바뀔 때 미리보기만 낡아서 "미리보기와 실제가 다르다"가 된다.
import { SiteFooter } from '@/layouts/SiteFooter';
import { SiteHeader } from '@/layouts/SiteHeader';
import { TopBar } from '@/layouts/TopBar';
import { buildNav } from '@/layouts/navigation';
import styles from '@/layouts/ShopLayout.module.css';
import { HomeView } from '@/views/HomeView';
import { loadHomeData } from '../(shop)/home-data';
import { ScrollKeeper } from './ScrollKeeper';

export const metadata: Metadata = {
  title: '배너 미리보기',
  // 손님이 찾아올 화면이 아니다. 색인되면 메인과 거의 같은 페이지가 하나 더 검색에 뜬다.
  robots: { index: false, follow: false },
};

export const dynamic = 'force-dynamic';

/**
 * 받아들일 이미지 키 모양.
 *
 * <p>이 주소는 공개 경로이고 값은 쿼리에서 온다. {@code mediaUrl} 은 {@code http} 로
 * 시작하는 값을 <b>그대로 쓰므로</b>, 걸러내지 않으면 남의 서버 이미지를 우리 도메인
 * 화면에 띄우는 주소가 만들어진다. {@code :} 와 맨 앞 {@code /} 가 막히면 그 길이 닫힌다.
 */
const KEY_PATTERN = /^[A-Za-z0-9][A-Za-z0-9/_.-]*$/;

/** 푸터 글자 한 칸의 최대 길이. 서버가 칸마다 자르는 값(최대 200)보다 넉넉히 둔다. */
const MAX_TEXT = 300;

/** 쉼표로 묶인 키 목록. 모양이 아닌 것은 빈 칸으로 둔다 — 자리를 당기면 짝이 어긋난다. */
function parseKeys(value: string | undefined): string[] {
  if (!value) return [];
  return value
    .split(',')
    .map((key) => (KEY_PATTERN.test(key) && !key.includes('..') ? key : ''));
}

/**
 * 쉼표로 묶인 「누르면 갈 곳」 목록.
 *
 * <p>칸마다 {@code encodeURIComponent} 로 싸여 온다 — 주소에 {@code ?} 와 {@code &}
 * 가 들어가기 때문이다({@code /shop?categoryId=1}).
 *
 * <p><b>우리 화면 안의 주소만</b> 받는다. 공개 경로라서, 거르지 않으면 우리 도메인의
 * 화면에 남의 사이트로 보내는 링크를 띄우는 주소를 누구나 만들 수 있다. {@code //} 는
 * 프로토콜 생략 주소(= 바깥)라 함께 막는다.
 */
function parseHrefs(value: string | undefined): string[] {
  if (!value) return [];
  return value.split(',').map((raw) => {
    let href: string;
    try {
      href = decodeURIComponent(raw);
    } catch {
      return '';
    }
    return href.startsWith('/') && !href.startsWith('//') ? href : '';
  });
}

/**
 * 쿼리로 온 푸터 글자.
 *
 * <p>저장된 값 위에 덮어쓴다. <b>{@link FooterInfo} 의 칸 이름만</b> 본다 — 쿼리를
 * 훑어 올리는 방식이면 아무 이름이나 들어오고, 거르는 코드를 따로 적어야 한다.
 * 글자는 React 가 escape 하므로 그대로 그려도 안전하고, 어디에도 저장되지 않는다.
 */
function parseFooter(
  saved: FooterInfo,
  params: Record<string, string | string[] | undefined>,
): FooterInfo {
  const info = { ...saved };
  for (const name of Object.keys(saved) as (keyof FooterInfo)[]) {
    const raw = params[name];
    if (typeof raw === 'string') {
      info[name] = raw.slice(0, MAX_TEXT);
    }
  }
  return info;
}

/**
 * 쿼리로 온 화면 설정 (구역 차례·색). 없거나 깨졌으면 저장된 것을 쓴다.
 *
 * <p>{@link normalizeDesign} 이 아는 구역·아는 색 자리만, 색은 {@code #rrggbb} 만
 * 남긴다. 공개 경로라서 이 값은 누구나 만들 수 있고, 색은 {@code <style>} 에 들어간다.
 */
function parseDesign(value: string | string[] | undefined, saved: SiteDesign): SiteDesign {
  if (typeof value !== 'string') return saved;
  try {
    return normalizeDesign(JSON.parse(value));
  } catch {
    return saved;
  }
}

/** 띠에 걸 수 있는 주소 — 저장할 때 서버(TopLinkService)가 보는 것과 같은 규칙이다. */
function isSafeHref(href: string): boolean {
  return /^https?:\/\/[^\s/]+\S*$/i.test(href) || /^\/(?![/\\])\S*$/.test(href);
}

/**
 * 쿼리로 온 상단 띠 링크. 없으면 저장된 것을 쓴다.
 *
 * <p>모양이 맞는 것만 남긴다 — 주소는 {@code javascript:} 같은 것이 섞이지 않게,
 * 아이콘은 우리 저장소의 키만.
 */
function parseTopLinks(value: string | string[] | undefined, saved: TopLink[]): TopLink[] {
  if (typeof value !== 'string') return saved;
  try {
    const raw: unknown = JSON.parse(value);
    if (!Array.isArray(raw)) return saved;
    return raw.slice(0, 12).flatMap((item) => {
      const label = String(item?.label ?? '').slice(0, 40);
      const href = String(item?.href ?? '');
      const iconKey = typeof item?.iconKey === 'string' ? item.iconKey : '';
      if (!label || !isSafeHref(href)) return [];
      const safeIcon = KEY_PATTERN.test(iconKey) && !iconKey.includes('..') ? iconKey : null;
      return [{ label, href, iconKey: safeIcon }];
    });
  } catch {
    return saved;
  }
}

/**
 * 배너 미리보기 (`/banner-preview`).
 *
 * <h2>손님 화면을 그대로 띄우고 고치는 중인 것만 갈아 끼운다</h2>
 * 배너 조각만 그리면 <b>확인하고 싶은 것을 확인할 수 없다.</b> 배너는 그 아래 시세표와
 * 맞닿는 자리에 놓이고, 사진 아래쪽 문구가 시세표 위에 어떻게 걸리는지가 실제로 보는
 * 그림이다. 푸터도 같다 — 주소를 한 줄 늘렸을 때 줄이 어떻게 접히는지는 푸터 안에서만
 * 보인다. 머리띠·시세·상품·푸터까지 함께 있어야 그게 보인다.
 *
 * <h2>왜 {@code (shop)} 레이아웃 밖인가</h2>
 * 레이아웃은 쿼리를 읽지 못한다. 그래서 레이아웃 안에 두면 <b>저장하지 않은</b> 배너와
 * 푸터를 넘길 길이 없다. 머리띠와 푸터를 컴포넌트로 떼어 두고({@link SiteHeader},
 * {@link SiteFooter}) 여기서 직접 조립한다 — 손님 화면과 같은 코드다.
 *
 * <p>{@code PriceChangeWatcher} 는 넣지 않는다. 미리보기를 열어 둔 사이에 시세가
 * 고시되면 확인 중인 화면에 알림창이 끼어든다.
 *
 * <h2>왜 iframe 안에서 여는가</h2>
 * 관리자 화면의 모달에서 바로 그리면 <b>휴대폰용을 확인할 수 없다.</b> 가로용·휴대폰용을
 * 가르는 것이 창 폭을 보는 미디어 쿼리이고, 그 창은 모달이 아니라 관리자가 쓰는 브라우저
 * 창이다. iframe 은 제 폭이 곧 제 뷰포트라 390px 안에서 모든 값이 휴대폰 값으로 풀린다.
 */
export default async function BannerPreviewPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const [params, data, footer, savedLinks] = await Promise.all([
    searchParams,
    loadHomeData(),
    load(() => footerApi.get(), '미리보기 푸터'),
    load(() => topLinksApi.get(), '미리보기 상단 띠'),
  ]);

  // 고치는 중인 화면 설정·띠 링크가 왔으면 그것으로, 아니면 저장된 것으로 그린다.
  const design = parseDesign(params.d, data.design);
  const topLinks = parseTopLinks(params.tl, savedLinks.data ?? []);
  const footerInfo = parseFooter(footer.data ?? FOOTER_DEFAULTS, params);
  const theme = themeCss(design.colors);
  const at = typeof params.at === 'string' ? params.at.slice(0, 20) : '';

  const wideKeys = parseKeys(typeof params.w === 'string' ? params.w : undefined);
  const mobileKeys = parseKeys(typeof params.m === 'string' ? params.m : undefined);
  const hrefs = parseHrefs(typeof params.h === 'string' ? params.h : undefined);

  /**
   * 저장돼 있는 꾸민 배너.
   *
   * <p>지금 관리자 화면이 고치는 것은 사진 배너뿐이라, 꾸민 배너는 저장된 것을
   * 그대로 앞에 둔다 — 빼면 사진 한 장을 올려 보는 동안 꾸민 배너가 사라진 것처럼
   * 보인다. 저장할 때의 차례와 같다 (꾸민 배너 먼저, 사진 배너 나중).
   */
  const designs = data.banners.filter((banner) => banner.kind === 'DESIGN');

  const banners: HeroBanner[] = wideKeys
    .map((imageKey, index) => ({
      // 저장된 배너의 id 와 겹치지 않게 음수로 둔다 — 겹치면 React 가 둘을 한 칸으로 본다
      id: -(index + 1),
      kind: 'IMAGE' as const,
      imageKey,
      mobileImageKey: mobileKeys[index] || null,
      // 손님 화면과 같이 눌리게 둔다 — 고른 목적지가 맞는지는 눌러 봐야 안다.
      // 눌러서 iframe 안이 그 화면으로 바뀌면 창을 닫았다 다시 열면 된다.
      linkHref: hrefs[index] || null,
      altText: `${index + 1}번 배너`,
      sort: index,
      active: true,
    }))
    // 거른 뒤에 짝을 맞춘다 — 먼저 걸러내면 휴대폰용이 한 칸씩 당겨져 엉뚱한 배너에 붙는다.
    .filter((banner) => banner.imageKey !== '');

  const all = [...designs, ...banners];

  return (
    <>
      {theme && <style>{theme}</style>}
      <ScrollKeeper at={at} />
      <TopBar
        info={footerInfo}
        links={topLinks}
        util={
          <>
            <span>로그인</span>
            <span>회원가입</span>
            <span>장바구니</span>
          </>
        }
      />
      <SiteHeader nav={buildNav(data.categories)} />

      <main id="main" className={styles.main}>
        {/* 배너가 없으면 HomeView 가 기본 배너로 돌아간다 — 메인에서 일어나는 일과 같다. */}
        <HomeView {...data} banners={all} design={design} />
      </main>

      <MobileTabBar />

      <SiteFooter info={footerInfo} />
    </>
  );
}
