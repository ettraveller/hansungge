'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import type { ReactNode } from 'react';

import { RequireMember } from '@/components/auth/RequireMember';
import { useMyPageSummary } from '@/hooks/useInterest';
import { useUserSession } from '@/hooks/useUserSession';
import type { MyPageSummary } from '@/types/interest';
import type { OrderStatusGroup } from '@/types/order';
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
    name: '계정 관리',
    items: [
      // 내 정보·비밀번호·약관·소셜 연결·탈퇴가 한 화면에 모여 있다.
      // /mypage 는 주문내역으로 보내므로 여기는 별도 주소를 쓴다.
      { name: '계정 관리', href: '/mypage/account' },
    ],
  },
  {
    name: '고객센터',
    items: [
      // ⚠️ 여기서 곧바로 게시판(/support/...)으로 내보내지 않는다.
      // 그러면 왼쪽 메뉴가 통째로 사라져 마이페이지를 벗어난 것처럼 보이고,
      // 돌아오려면 뒤로가기밖에 없다. 내 글만 보는 화면을 안쪽에 둔다.
      { name: '내 문의', href: '/mypage/inquiries' },
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
              const on = pathname.startsWith(item.href.split('#')[0]);
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

/**
 * 주문처리 현황.
 *
 * <p>주문이 거치는 순서 그대로 늘어놓는다. 전에는 「총 주문 · 결제 대기 · 준비·배송 중 ·
 * 찜한 상품」 넷을 한 줄에 같은 크기로 놓았는데, 두 가지가 어긋났다 —
 *
 * <ul>
 *   <li>「준비·배송 중」이 두 단계를 합쳐, 정작 묻고 싶은 <b>"떠났나"</b>에 답하지 못했다.
 *   <li>찜한 상품이 주문 상태 사이에 끼어, 넷이 서로 관계없는 숫자 넷으로 읽혔다.
 * </ul>
 *
 * <p>숫자는 {@code ?group=} 을 달고 주문 목록으로 간다. 그 묶음은 서버가 세는 단위와
 * 같다 — 「배송 중 1」을 눌렀는데 세 건이 뜨면 숫자를 믿지 않게 된다.
 */
const FLOW: { name: string; group: OrderStatusGroup; of: (s: MyPageSummary) => number }[] = [
  { name: '결제 대기', group: 'AWAITING_PAYMENT', of: (s) => s.awaitingPayment },
  // 「배송 준비」가 아니라 「상품 준비」다 — 주문 줄에 찍히는 상태가 "상품 준비 중"이다.
  // 숫자와 그 숫자를 눌러 들어간 목록이 서로 다른 말을 쓰면 같은 것인지 알 수 없다.
  { name: '상품 준비', group: 'PREPARING', of: (s) => s.preparing },
  { name: '배송 중', group: 'SHIPPED', of: (s) => s.shipping },
  { name: '배송 완료', group: 'DELIVERED', of: (s) => s.delivered },
];

function SummaryBar() {
  const { data } = useMyPageSummary();
  // 이름·번호는 이미 세션에 있다. 요약 응답에 또 담으면 두 값이 갈라진다.
  const { user } = useUserSession();
  if (!data) {
    return null;
  }

  return (
    <div className={styles.summary}>
      <div className={styles.who}>
        <span className={styles.whoName}>{user?.name ?? data.name} 님</span>
        <span className={styles.whoSub}>{formatPhone(user?.phone) || ''}</span>
        {/* 총계는 단계가 아니다 — 흐름 안에 「전체 14」를 끼우면 결제 대기 앞 단계로
            읽힌다. 찜한 상품은 주문 상태와 아무 관계가 없다. 둘 다 작게 옆에 둔다. */}
        <span className={styles.whoLinks}>
          <Link href="/mypage/orders" className={styles.whoLink}>
            주문 {data.orderCount}
          </Link>
          <Link href="/mypage/wishlist" className={styles.whoLink}>
            찜한 상품 {data.wishlistCount}
          </Link>
        </span>
      </div>

      <div className={styles.flow}>
        <ol className={styles.steps} aria-label="주문처리 현황">
          {FLOW.map((step, index) => (
            <li key={step.group} className={styles.step}>
              {index > 0 && (
                <span className={styles.arrow} aria-hidden="true">
                  →
                </span>
              )}
              <Link href={`/mypage/orders?group=${step.group}`} className={styles.stat}>
                <span className={styles.statName}>{step.name}</span>
                <span className={styles.statValue}>{step.of(data)}</span>
              </Link>
            </li>
          ))}
        </ol>

        {/* 취소·환불은 단계가 아니라 흐름에서 빠져나간 주문이다. 화살표 뒤가 아니라
            선 바깥에 둔다 — 안에 두면 배송 완료 다음 단계로 읽힌다. */}
        <div className={styles.aside}>
          <Link href="/mypage/orders?group=CANCELLED" className={styles.stat}>
            <span className={styles.statName}>취소·환불</span>
            <span className={styles.statValue}>{data.cancelled}</span>
          </Link>
        </div>
      </div>
    </div>
  );
}

export { styles as myPageStyles };
