'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';

import { useAdminSession } from '@/hooks/admin/useAdminSession';
import { useUnansweredQnaCount } from '@/hooks/admin/useQnas';
import { ChartIcon, HomeIcon, MenuIcon, UserIcon } from '@/layouts/icons';
import type { AdminRole } from '@/types/admin';
import styles from './AdminTabBar.module.css';

/**
 * 관리자 하단 탭바 — 손으로 쓰라고 만든 것이다.
 *
 * <h2>왜 햄버거만으로는 부족한가</h2>
 * 좁은 화면에서 사이드바가 숨으면 화면을 옮길 때마다 <b>햄버거 → 목록 → 항목</b> 세
 * 번을 눌러야 한다. 관리자가 실제로 오가는 화면은 서너 개뿐이고, 그 사이를 하루에도
 * 여러 번 오간다.
 *
 * <p>메뉴 전체는 햄버거에 그대로 둔다. 여기에는 <b>자주 가는 곳만</b> 올린다 —
 * 탭이 다섯 개를 넘으면 하나하나가 좁아져 누르기 어려워지고, 그러면 탭바를 둔 의미가
 * 없다.
 *
 * <h2>미답변 배지</h2>
 * 문의는 답이 늦는 것이 유일한 실패 방식이다. 숫자를 탭에 얹어 두면 목록을 열지
 * 않아도 밀린 것이 있는지 보인다.
 */
const TABS: { href: string; label: string; minRole: AdminRole; Icon: typeof HomeIcon }[] = [
  { href: '/admin', label: '대시보드', minRole: 'OPERATOR', Icon: HomeIcon },
  { href: '/admin/prices', label: '시세', minRole: 'OPERATOR', Icon: ChartIcon },
  { href: '/admin/products', label: '상품', minRole: 'OPERATOR', Icon: MenuIcon },
  { href: '/admin/qnas', label: '문의', minRole: 'OPERATOR', Icon: UserIcon },
];

export function AdminTabBar() {
  const pathname = usePathname();
  const { can, isAuthenticated } = useAdminSession();
  const { data: unanswered } = useUnansweredQnaCount();

  // 로그인 화면에서는 탭바를 그리지 않는다. 갈 수 있는 곳이 없다.
  if (!isAuthenticated) return null;

  const tabs = TABS.filter((tab) => can(tab.minRole));

  return (
    <nav className={styles.bar} aria-label="관리자 주요 메뉴">
      {tabs.map(({ href, label, Icon }) => {
        // '/admin' 은 모든 관리자 경로의 접두사라 정확히 같을 때만 활성이다.
        const active = href === '/admin' ? pathname === '/admin' : pathname.startsWith(href);
        const badge = href === '/admin/qnas' ? (unanswered?.count ?? 0) : 0;

        return (
          <Link
            key={href}
            href={href}
            className={`${styles.tab} ${active ? styles.on : ''}`}
            aria-current={active ? 'page' : undefined}
          >
            <span className={styles.iconWrap}>
              <Icon size={20} />
              {badge > 0 && (
                <span className={styles.badge} aria-label={`미답변 ${badge}건`}>
                  {badge > 99 ? '99+' : badge}
                </span>
              )}
            </span>
            <span>{label}</span>
          </Link>
        );
      })}
    </nav>
  );
}
