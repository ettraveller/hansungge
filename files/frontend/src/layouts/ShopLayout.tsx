import Image from 'next/image';
import Link from 'next/link';
import { productApi } from '@/api/products';
import { load } from '@/api/server';
import { MobileDrawer } from '@/layouts/MobileDrawer';
import { buildNav } from '@/layouts/navigation';
import styles from './ShopLayout.module.css';

/**
 * 쇼핑몰 공통 레이아웃.
 *
 * <pre>
 * 상단 유틸바 (다크)
 * 헤더 (sticky) — 로고 · 검색 · 로그인/마이페이지/장바구니/위시리스트 · 햄버거
 * GNB — 카테고리 드롭다운
 * [ 본문 ]
 * 푸터 (다크)
 * 모바일 하단 탭바 (960px 이하)
 * </pre>
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
      {/* ---------- 상단 유틸 바 ---------- */}
      <div className={styles.topbar}>
        <div className="wrap">
          <div className={styles.notice}>
            <span aria-hidden="true">🔊</span> 오늘의 순금 시세가 업데이트 되었습니다.
          </div>
          <div className={styles.util}>
            <Link href="/login">로그인</Link>
            <Link href="/signup">회원가입</Link>
            <Link href="/mypage/orders" className={styles.mHide}>
              주문조회
            </Link>
            <Link href="/support" className={styles.mHide}>
              고객센터
            </Link>
          </div>
        </div>
      </div>

      {/* ---------- 헤더 ---------- */}
      <header className={styles.header}>
        <div className={`wrap ${styles.headerMain}`}>
          <Link href="/" className={styles.logo} aria-label="한성금거래소 홈">
            <Image src="/logo.png" alt="한성금거래소" width={560} height={104} priority />
          </Link>

          <form className={styles.search} action="/shop" role="search">
            <input type="text" name="keyword" placeholder="찾으시는 상품을 검색해 보세요" />
            <button type="submit" aria-label="검색">
              🔍
            </button>
          </form>

          {/* 순서는 레퍼런스와 동일: 로그인 → 마이페이지 → 장바구니 → 위시리스트 */}
          <div className={styles.headerIcons}>
            <Link href="/login">
              <span className={styles.ico} aria-hidden="true">
                🔑
              </span>
              로그인
            </Link>
            <Link href="/mypage">
              <span className={styles.ico} aria-hidden="true">
                👤
              </span>
              마이페이지
            </Link>
            <Link href="/cart" className={styles.cartWrap}>
              <span className={styles.ico} aria-hidden="true">
                🛒
              </span>
              <span className={styles.cartBadge}>0</span>
              장바구니
            </Link>
            <Link href="/wishlist">
              <span className={styles.ico} aria-hidden="true">
                ♡
              </span>
              위시리스트
            </Link>
          </div>

          {/* 햄버거·드로어는 브라우저 상태가 필요해 클라이언트 컴포넌트로 분리한다 */}
          <MobileDrawer nav={nav} />
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

      <main>{children}</main>

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
