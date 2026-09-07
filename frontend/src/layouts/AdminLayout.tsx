'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';

import { AdminTabBar } from '@/layouts/AdminTabBar';
import { useAdminLogout, useAdminSession } from '@/hooks/admin/useAdminSession';
import { ADMIN_ROLE_LABEL, type AdminRole } from '@/types/admin';
import styles from './AdminLayout.module.css';

const LOGIN_PATH = '/admin/login';

/**
 * 좌측 메뉴.
 *
 * {@link minRole} 은 <b>메뉴를 감추기 위한 것일 뿐</b>이다. 실제 차단은 백엔드
 * {@code @PreAuthorize} 가 한다 — 주소를 직접 쳐서 들어가면 화면은 뜨지만 API 가 403 을 준다.
 */
const MENU: { href: string; label: string; minRole: AdminRole }[] = [
  { href: '/admin', label: '대시보드', minRole: 'OPERATOR' },
  { href: '/admin/prices', label: '시세관리', minRole: 'OPERATOR' },
  { href: '/admin/products', label: '상품관리', minRole: 'OPERATOR' },
  { href: '/admin/categories', label: '카테고리', minRole: 'OPERATOR' },
  { href: '/admin/qnas', label: '문의 관리', minRole: 'OPERATOR' },
  { href: '/admin/notices', label: '공지 관리', minRole: 'OPERATOR' },
  { href: '/admin/events', label: '이벤트 관리', minRole: 'OPERATOR' },
  { href: '/admin/members', label: '회원관리', minRole: 'OPERATOR' },
  { href: '/admin/audit-logs', label: '감사로그', minRole: 'ADMIN' },
  { href: '/admin/admins', label: '관리자 계정', minRole: 'SUPER_ADMIN' },
  { href: '/admin/account', label: '내 계정', minRole: 'OPERATOR' },
];

/** 아직 백엔드가 없는 영역. 링크로 만들지 않는다 — 눌러도 404 인 메뉴는 신뢰를 깎는다. */
const PLANNED = ['주문관리', '배송관리', '결제관리', '쿠폰'];

function isActive(pathname: string, href: string): boolean {
  if (href === '/admin') return pathname === '/admin';
  return pathname === href || pathname.startsWith(`${href}/`);
}

/**
 * 관리자 레이아웃.
 *
 * 쇼핑몰 레이아웃과 완전히 분리한다 — 헤더·네비게이션·인증 컨텍스트를 공유하지 않는다.
 * 검색엔진 노출은 route segment 의 metadata(robots: noindex)로 차단한다.
 */
export function AdminLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { admin, hydrated, isAuthenticated, can } = useAdminSession();
  const logout = useAdminLogout();

  /**
   * 좁은 화면에서 메뉴를 여닫는 상태.
   *
   * <p>사이드바는 1024px 미만에서 숨는데 대체 수단이 없어서, <b>휴대폰으로는 화면 사이를
   * 이동할 방법 자체가 없었다.</b> 같은 사이드바를 겹쳐 띄우는 방식으로 연다 — 모바일용
   * 메뉴를 따로 만들면 항목이 갈라져 한쪽만 갱신되는 일이 생긴다.
   */
  const [menuOpen, setMenuOpen] = useState(false);

  // 화면을 옮기면 닫는다. 열어둔 채 이동하면 새 화면 위에 메뉴가 남는다.
  const [lastPath, setLastPath] = useState(pathname);
  if (pathname !== lastPath) {
    setLastPath(pathname);
    setMenuOpen(false);
  }

  useEffect(() => {
    if (!menuOpen) return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setMenuOpen(false);
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [menuOpen]);

  const onLoginPage = pathname === LOGIN_PATH;

  useEffect(() => {
    // sessionStorage 복구가 끝나기 전에는 판단하지 않는다. 새로고침할 때마다
    // 로그인 화면이 한 번 번쩍이는 것을 막는다.
    if (!hydrated || onLoginPage || isAuthenticated) return;
    const next = encodeURIComponent(pathname);
    router.replace(`${LOGIN_PATH}?next=${next}`);
  }, [hydrated, isAuthenticated, onLoginPage, pathname, router]);

  // 로그인 화면은 사이드바 없이 그대로 그린다.
  if (onLoginPage) return <>{children}</>;

  if (!hydrated) {
    return <div className={styles.gate}>세션을 확인하는 중…</div>;
  }

  if (!isAuthenticated) {
    // 위 effect 가 곧 로그인 화면으로 보낸다. 그 사이 관리자 데이터를 그리지 않는다.
    return <div className={styles.gate}>로그인 화면으로 이동합니다…</div>;
  }

  const menu = MENU.filter((item) => can(item.minRole));

  return (
    <div className={styles.root}>
      {/* 좁은 화면에서 메뉴가 열렸을 때만 뒤를 덮는다. 넓은 화면에서는 아예 안 보인다. */}
      <div
        className={menuOpen ? styles.scrimOn : styles.scrim}
        onClick={() => setMenuOpen(false)}
        aria-hidden="true"
      />

      <aside className={menuOpen ? styles.sidebarOpen : styles.sidebar}>
        <div className={styles.brand}>
          한성금거래소
          <span className={styles.badge}>ADMIN</span>
        </div>
        <nav className={styles.nav} aria-label="관리자 메뉴">
          {menu.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              className={isActive(pathname, item.href) ? styles.navActive : undefined}
              aria-current={isActive(pathname, item.href) ? 'page' : undefined}
            >
              {item.label}
            </Link>
          ))}
        </nav>
        <div className={styles.planned}>
          <span className={styles.plannedTitle}>준비 중</span>
          {PLANNED.map((label) => (
            <span key={label} className={styles.plannedItem}>
              {label}
            </span>
          ))}
        </div>
      </aside>

      <div className={styles.body}>
        <header className={styles.topbar}>
          {/* 좁은 화면 전용. 넓은 화면에서는 사이드바가 늘 보이므로 숨긴다. */}
          <button
            type="button"
            className={styles.menuButton}
            aria-label={menuOpen ? '메뉴 닫기' : '메뉴 열기'}
            aria-expanded={menuOpen}
            onClick={() => setMenuOpen((v) => !v)}
          >
            <span />
            <span />
            <span />
          </button>

          <span className={styles.env}>{process.env.NEXT_PUBLIC_ENV_LABEL ?? 'local'}</span>
          <span className={styles.who}>
            <strong>{admin?.name}</strong>
            <span className={styles.role}>
              {admin ? ADMIN_ROLE_LABEL[admin.role] : ''} · {admin?.loginId}
            </span>
          </span>
          <button
            type="button"
            className={styles.logout}
            onClick={() => logout.mutate()}
            disabled={logout.isPending}
          >
            {logout.isPending ? '로그아웃 중…' : '로그아웃'}
          </button>
        </header>
        <main className={styles.main}>{children}</main>
      </div>

      {/* 좁은 화면 전용. 자주 가는 화면만 올린다 — 메뉴 전체는 햄버거에 그대로 있다. */}
      <AdminTabBar />
    </div>
  );
}
