import Form from 'next/form';
import Image from 'next/image';
import Link from 'next/link';

import { SearchIcon } from '@/layouts/icons';
import logoGold from '@/layouts/logo-gold.svg';
import { Suspense } from 'react';

import { MobileDrawer } from '@/layouts/MobileDrawer';
import { MobileSearch } from '@/layouts/MobileSearch';
import { StickyHeader } from '@/layouts/StickyHeader';
import type { NavItem } from '@/layouts/navigation';
import styles from './ShopLayout.module.css';

/**
 * 쇼핑몰 머리띠 — 로고(가운데)·검색(오른쪽) + GNB.
 * 로그인·장바구니는 그 위의 띠({@code TopBar})로 올라갔다 (2026-11-19).
 *
 * <h2>왜 레이아웃에서 떼어 놓았는가</h2>
 * 배너 미리보기가 <b>손님이 보는 그 화면 그대로</b>를 띄워야 하는데, 그 화면은
 * {@code (shop)} 레이아웃 밖에 있다 — 레이아웃은 쿼리를 읽지 못해서 저장하지 않은
 * 배너·푸터를 받을 길이 없다. 머리띠와 푸터를 컴포넌트로 두면 미리보기가 같은 것을
 * 그대로 쓴다. 비슷하게 다시 그리는 길을 두면 쇼핑몰 쪽이 바뀔 때 미리보기만 낡는다.
 *
 * <h2>드롭다운을 JS 없이 만든 이유</h2>
 * {@code :hover} 와 {@code :focus-within} 으로만 연다. 클라이언트 컴포넌트로 만들면
 * 하위 메뉴 링크가 <b>초기 HTML 에서 빠져</b> 검색엔진이 카테고리 구조를 읽지 못한다.
 * 이 프로젝트가 Next.js 를 고른 첫 번째 이유가 SEO 다. {@code focus-within} 덕분에
 * 키보드 Tab 이동으로도 열린다.
 */
export function SiteHeader({ nav }: { nav: NavItem[] }) {
  // 내려가면 메뉴 줄이 접힌다. 안쪽은 그대로 서버에서 그려진다 —
  // StickyHeader 는 껍데기만 클라이언트다.
  return (
  <StickyHeader className={styles.header} compactClass={styles.compact}>
    <div className={`wrap ${styles.headerMain}`}>
      {/* 햄버거는 모바일에서만 보인다. 로고 왼쪽이 엄지가 닿는 자리다 */}
      <MobileDrawer nav={nav} />

      {/* 헤더가 흰 판이다 (2026-11-19). 글자까지 금색인 logo-gold.svg 를 쓴다 —
          logo-dark.svg 는 영문 한 줄이 흰색에 가까워 흰 바탕에서 사라진다.

          ⚠️ 주소 문자열("/logo-gold.svg")로 쓰지 않고 import 한다. 문자열 주소에는
          basePath 가 붙지 않아 QA(/qa)에서 그림이 깨졌다 (2026-11-19). import 한
          파일은 빌드가 주소를 만들어 주므로 어느 자리에 올려도 맞는다. */}
      <Link href="/" className={styles.logo} aria-label="한성금거래소 홈">
        <Image src={logoGold} alt="한성금거래소" width={560} height={104} priority />
      </Link>

      {/* next/form 을 쓴다. 맨 <form action="/shop"> 에는 basePath 가 붙지 않아
          QA(/qa)에서 검색하면 운영 주소로 넘어갔다. */}
      <Form className={styles.search} action="/shop" role="search">
        <SearchIcon size={17} className={styles.searchIcon} />
        <input type="text" name="keyword" placeholder="상품 검색" aria-label="상품 검색" />
        <button type="submit">검색</button>
      </Form>

      {/* 좁은 화면의 검색. 주소의 검색어를 읽으므로(useSearchParams) Suspense 로
          감싼다 — 감싸지 않으면 이 헤더를 쓰는 화면 전체가 정적 생성에서 빠진다. */}
      <Suspense fallback={null}>
        <MobileSearch />
      </Suspense>

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
  </StickyHeader>
  );
}
