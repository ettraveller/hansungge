'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { CartIcon, ChartIcon, HomeIcon, MenuIcon, UserIcon } from '@/layouts/icons';
import styles from './MobileTabBar.module.css';

const TABS = [
  { href: '/', label: '홈', Icon: HomeIcon },
  { href: '/price', label: '시세', Icon: ChartIcon },
  { href: '/shop', label: '카테고리', Icon: MenuIcon },
  { href: '/cart', label: '장바구니', Icon: CartIcon },
  { href: '/mypage', label: '마이', Icon: UserIcon },
] as const;

/**
 * 모바일 하단 고정 탭바 (960px 이하).
 *
 * <p>토큰에 이미 {@code --tabbar-h: 60px} 가 있고 {@code globals.css} 가 그만큼
 * {@code body} 하단 여백을 준다 — 원래 설계에 있던 자리를 채우는 것이다.
 *
 * <p>현재 위치 표시가 없으면 사용자가 어디에 있는지 알 수 없다. 경로가 정확히 같거나
 * 하위 경로일 때 활성으로 본다 — 단, 홈("/")은 모든 경로의 접두사라 <b>정확히 같을
 * 때만</b> 활성이다. 그렇지 않으면 어느 화면에서든 홈이 켜져 있다.
 */
export function MobileTabBar() {
  const pathname = usePathname();

  return (
    <nav className={styles.bar} aria-label="주요 메뉴">
      {TABS.map(({ href, label, Icon }) => {
        const active = href === '/' ? pathname === '/' : pathname.startsWith(href);
        return (
          <Link
            key={href}
            href={href}
            className={`${styles.tab} ${active ? styles.on : ''}`}
            aria-current={active ? 'page' : undefined}
          >
            <Icon size={20} />
            <span>{label}</span>
          </Link>
        );
      })}
    </nav>
  );
}
