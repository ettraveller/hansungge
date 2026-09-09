import Image from 'next/image';
import Link from 'next/link';
import { productApi } from '@/api/products';
import { load } from '@/api/server';
import { HeaderAuth } from '@/layouts/HeaderAuth';
import { HeaderCartBadge } from '@/layouts/HeaderCartBadge';
import { CartIcon, SearchIcon } from '@/layouts/icons';
import { MobileDrawer } from '@/layouts/MobileDrawer';
import { MobileTabBar } from '@/layouts/MobileTabBar';
import { buildNav } from '@/layouts/navigation';
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
 * <h2>드롭다운을 JS 없이 만든 이유</h2>
 * {@code :hover} 와 {@code :focus-within} 으로만 연다. 클라이언트 컴포넌트로 만들면
 * 하위 메뉴 링크가 <b>초기 HTML 에서 빠져</b> 검색엔진이 카테고리 구조를 읽지 못한다.
 * 이 프로젝트가 Next.js 를 고른 첫 번째 이유가 SEO 다. {@code focus-within} 덕분에
 * 키보드 Tab 이동으로도 열린다.
 *
 * <h2>메뉴가 서버 데이터인 이유</h2>
 * 카테고리를 코드에 박아두면 관리자가 카테고리를 추가할 때마다 배포해야 한다.
 * 조회 실패 시에는 고정 메뉴(시세조회·매입절차·지점안내·고객센터)만 남는다 —
 * 헤더가 통째로 사라지는 것보다 낫다.
 */
export async function ShopLayout({ children }: { children: React.ReactNode }) {
  const categories = await load(() => productApi.categories(), 'GNB 카테고리');
  const nav = buildNav(categories.data);

  return (
    <>
      {/* ---------- 헤더 ---------- */}
      <header className={styles.header}>
        <div className={`wrap ${styles.headerMain}`}>
          {/* 햄버거는 모바일에서만 보인다. 로고 왼쪽이 엄지가 닿는 자리다 */}
          <MobileDrawer nav={nav} />

          <Link href="/" className={styles.logo} aria-label="한성금거래소 홈">
            <Image src="/logo.png" alt="한성금거래소" width={560} height={104} priority />
          </Link>

          <form className={styles.search} action="/shop" role="search">
            <SearchIcon size={17} className={styles.searchIcon} />
            <input type="text" name="keyword" placeholder="찾으시는 상품을 검색해 보세요" />
            <button type="submit">검색</button>
          </form>

          <div className={styles.headerUtil}>
            {/* 로그인 여부에 따라 바뀌는 부분만 클라이언트 컴포넌트다 */}
            <HeaderAuth />
            <Link href="/cart" className={styles.cartLink}>
              <CartIcon size={18} />
              <span className={styles.cartText}>장바구니</span>
              <HeaderCartBadge />
            </Link>
          </div>
        </div>

        {/* ---------- GNB (CSS 드롭다운) ---------- */}
        <nav className={styles.gnbNav} aria-label="주 메뉴">
          <ul className={`wrap ${styles.gnb}`}>
            {nav.map((item) => (
              <li key={item.href} className={styles.gnbItem}>
                <Link href={item.href} className={styles.gnbLink}>
                  {item.label}
                  {item.children.length > 0 && (
                    <span className={styles.caret} aria-hidden="true" />
                  )}
                </Link>

                {item.children.length > 0 && (
                  <div className={styles.dropdown}>
                    <ul>
                      {item.children.map((child) => (
                        <li key={child.href}>
                          <Link href={child.href}>{child.label}</Link>
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
              </li>
            ))}
          </ul>
        </nav>
      </header>

      <main id="main">{children}</main>

      <MobileTabBar />

      {/* ---------- 푸터 ---------- */}
      <footer className={styles.footer}>
        <div className={styles.footTop}>
          <div className="wrap">
            <Link href="/about">회사소개</Link>
            <Link href="/terms">이용약관</Link>
            <Link href="/privacy">개인정보처리방침</Link>
            <Link href="/guide">이용안내</Link>
            <Link href="/store">지점안내</Link>
          </div>
        </div>

        <div className={styles.footMain}>
          <div className="wrap">
            <div className={styles.footInfo}>
              <Image
                src="/logo.png"
                alt="한성금거래소"
                width={560}
                height={104}
                className={styles.footLogo}
              />
              <p>
                대표자 : 홍길동 &nbsp;|&nbsp; 사업자등록번호 : 000-00-00000 &nbsp;|&nbsp;
                통신판매업신고 : 제0000-서울중구-0000호
              </p>
              <p>본사 : 서울특별시 종로구 돈화문로 00, 0층 (묘동)</p>
              <p>이메일 : info@hansunggold.co.kr</p>
              <p className={styles.footNote}>
                정직과 신뢰를 바탕으로 고객의 투자를 함께 설계합니다.
              </p>
            </div>

            <div className={styles.footCs}>
              <p className={styles.csLabel}>대표전화</p>
              <p className={styles.tel}>1588-0000</p>
              <p>온라인 구매 문의 1551-0000</p>
              <p>운영시간 : 월-토 10:00 ~ 19:00 (일/공휴일 휴무)</p>
            </div>
          </div>
        </div>

        <div className={styles.copyright}>
          COPYRIGHT © HANSUNG GOLD EXCHANGE. ALL RIGHTS RESERVED.
        </div>
      </footer>
    </>
  );
}
