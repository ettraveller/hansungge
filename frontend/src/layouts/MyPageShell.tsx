'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import type { ReactNode } from 'react';

import { RequireMember } from '@/components/auth/RequireMember';
import { useMyPageSummary } from '@/hooks/useInterest';
import { useUserSession } from '@/hooks/useUserSession';
import { formatPhone } from '@/utils/format';
import styles from '@/layouts/MyPageShell.module.css';

/**
 * 마이페이지 공통 껍데기 — 왼쪽 메뉴 + 위 요약 + 내용.
 *
 * <h2>왜 껍데기를 따로 두나</h2>
 * 주문내역·찜한 상품·최근 본 상품·내 정보가 전부 같은 틀 안에서 오간다. 화면마다 메뉴를
 * 따로 그리면 한 곳만 고쳤을 때 다른 화면과 어긋나고, 고객은 메뉴가 <b>움직인다</b>고 느낀다.
 *
 * <h2>숫자는 서버가 센다</h2>
 * 주문 목록은 한 장씩 끊어 오므로, 화면이 센 값은 그 한 장의 개수일 뿐이다.
 * 총 건수는 따로 묻는다.
 */

/** 메뉴 — 그룹 제목 아래 항목. 없는 기능은 넣지 않는다. */
const GROUPS: { name: string; items: { name: string; href: string }[] }[] = [
  {
    name: '주문관리',
    items: [
      { name: '주문/배송내역', href: '/mypage/orders' },
      { name: '찜한 상품', href: '/mypage/wishlist' },
      { name: '최근 본 상품', href: '/mypage/recently-viewed' },
    ],
  },
  {
    name: '나의 정보관리',
    items: [
      { name: '내 정보', href: '/mypage' },
      { name: '본인인증', href: '/mypage#identity' },
    ],
  },
  {
    name: '고객센터',
    items: [
      { name: '1:1 문의', href: '/support/qna' },
      { name: '상품 문의', href: '/support/product-qna' },
    ],
  },
];

export function MyPageShell({
  title,
  lead,
  showSummary = true,
  children,
}: {
  title: string;
  lead?: string;
  /** 요약 줄을 감출지. 내용이 이미 요약인 화면(내 정보)에서는 겹친다 */
  showSummary?: boolean;
  children: ReactNode;
}) {
  return (
    <RequireMember>
      <div className={styles.wrap}>
        <SideNav />
        <div>
          <h1 className={styles.head}>{title}</h1>
          {lead && <p className={styles.lead}>{lead}</p>}
          {showSummary && <SummaryBar />}
          {children}
        </div>
      </div>
    </RequireMember>
  );
}

function SideNav() {
  const pathname = usePathname();

  return (
    <nav aria-label="마이페이지 메뉴">
      <p className={styles.navTitle}>마이페이지</p>
      <div className={styles.nav}>
        {GROUPS.map((group) => (
          <div key={group.name} className={styles.group}>
            <p className={styles.groupName}>{group.name}</p>
            {group.items.map((item) => {
              // '/mypage' 는 다른 모든 마이페이지 주소의 앞부분이라 startsWith 로 보면
              // 어느 화면에서나 켜진 것으로 보인다. 정확히 같을 때만 켠다.
              const on = item.href === '/mypage'
                ? pathname === '/mypage'
                : pathname.startsWith(item.href.split('#')[0]);
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={on ? `${styles.item} ${styles.itemOn}` : styles.item}
                  aria-current={on ? 'page' : undefined}
                >
                  {item.name}
                </Link>
              );
            })}
          </div>
        ))}
      </div>
    </nav>
  );
}

function SummaryBar() {
  const { data } = useMyPageSummary();
  // 이름·번호는 이미 세션에 있다. 요약 응답에 또 담으면 두 값이 갈라진다.
  const { user } = useUserSession();
  if (!data) {
    return null;
  }

  const stats: { name: string; value: number; href?: string }[] = [
    { name: '총 주문', value: data.orderCount, href: '/mypage/orders' },
    { name: '결제 대기', value: data.awaitingPayment, href: '/mypage/orders' },
    { name: '준비·배송 중', value: data.preparing + data.shipping, href: '/mypage/orders' },
    { name: '찜한 상품', value: data.wishlistCount, href: '/mypage/wishlist' },
  ];

  return (
    <div className={styles.summary}>
      <div className={styles.who}>
        <div className={styles.whoName}>{user?.name ?? data.name} 님</div>
        <div className={styles.whoSub}>{formatPhone(user?.phone) || ''}</div>
      </div>

      {stats.map((stat) =>
        stat.href ? (
          <Link key={stat.name} href={stat.href} className={`${styles.stat} ${styles.statLink}`}>
            <div className={styles.statName}>{stat.name}</div>
            <div className={styles.statValue}>{stat.value}</div>
          </Link>
        ) : (
          <div key={stat.name} className={styles.stat}>
            <div className={styles.statName}>{stat.name}</div>
            <div className={styles.statValue}>{stat.value}</div>
          </div>
        ),
      )}
    </div>
  );
}

export { styles as myPageStyles };
