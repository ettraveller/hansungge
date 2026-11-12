import { priceApi } from '@/api/prices';
import { productApi } from '@/api/products';
import { load } from '@/api/server';
import { footerApi } from '@/api/footer';
import { MobileTabBar } from '@/layouts/MobileTabBar';
import { SiteFooter } from '@/layouts/SiteFooter';
import { SiteHeader } from '@/layouts/SiteHeader';
import { buildNav } from '@/layouts/navigation';
import { PriceChangeWatcher } from '@/components/price/PriceChangeWatcher';
import { FOOTER_DEFAULTS } from '@/data/company';
import styles from './ShopLayout.module.css';

/**
 * 쇼핑몰 공통 레이아웃.
 *
 * <pre>
 * 헤더 (sticky) — [햄버거] 로고 · 검색 · 로그인/장바구니
 * GNB — 카테고리 드롭다운 (모바일에서는 햄버거 드로어가 대신한다)
 * [ 본문 ]
 * 푸터 (다크)
 * 모바일 하단 탭바 (960px 이하)
 * </pre>
 *
 * <h2>머리가 2층인 이유</h2>
 * 예전에는 유틸바까지 3층이라 내용이 시작되기 전에 168px 를 썼다. 첫 화면에서 가장
 * 먼저 보여야 할 것은 시세지 메뉴가 아니다. 유틸바에 있던 로그인·장바구니는 헤더
 * 오른쪽으로, 주문조회·고객센터는 마이페이지와 푸터로 옮겼다.
 *
 * <h2>머리띠·푸터는 컴포넌트다</h2>
 * 그리는 일은 {@link SiteHeader}·{@link SiteFooter} 가 하고 여기는 <b>데이터를
 * 불러와 넘기는 일</b>만 한다. 배너 미리보기가 손님 화면 그대로를 띄워야 하는데,
 * 레이아웃은 쿼리를 읽지 못해 저장하지 않은 배너·푸터를 받을 길이 없다. 그래서
 * 미리보기는 레이아웃 밖에서 같은 두 컴포넌트를 직접 쓴다 — 비슷하게 다시 그리는
 * 길을 두면 쇼핑몰 쪽이 바뀔 때 미리보기만 낡는다.
 *
 * <h2>메뉴가 서버 데이터인 이유</h2>
 * 카테고리를 코드에 박아두면 관리자가 카테고리를 추가할 때마다 배포해야 한다.
 * 조회 실패 시에는 고정 메뉴(시세조회·매입절차·지점안내·고객센터)만 남는다 —
 * 헤더가 통째로 사라지는 것보다 낫다.
 *
 * <h2>열어 둔 화면에 새 시세를 반영한다</h2>
 * 시세는 메인·시세조회·상품·장바구니 어디에나 나온다. 그래서 감시를 페이지마다 두지 않고
 * 여기 한 곳에 둔다({@link PriceChangeWatcher}). 이 화면을 그릴 때의 시세 버전을 함께 넘겨,
 * 그리는 사이에 고시가 끼어도 놓치지 않게 한다.
 */
export async function ShopLayout({ children }: { children: React.ReactNode }) {
  const [categories, priceVersion, footer] = await Promise.all([
    load(() => productApi.categories(), 'GNB 카테고리'),
    load(() => priceApi.version(), '시세 버전'),
    // 실패해도 화면을 멈추지 않는다 — 코드의 기본값으로 푸터를 그린다.
    load(() => footerApi.get(), '푸터 정보'),
  ]);

  return (
    <>
      <SiteHeader nav={buildNav(categories.data)} />

      <main id="main" className={styles.main}>
        {children}
      </main>

      <PriceChangeWatcher initialVersion={priceVersion.data?.version ?? null} />

      <MobileTabBar />

      <SiteFooter info={footer.data ?? FOOTER_DEFAULTS} />
    </>
  );
}
