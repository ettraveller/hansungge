'use client';

import Image from 'next/image';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect, useRef, useState, useSyncExternalStore } from 'react';
import { createPortal } from 'react-dom';
import { useSignOut, useUserSession } from '@/hooks/useUserSession';
import type { NavItem } from '@/layouts/navigation';
import styles from './MobileDrawer.module.css';

/**
 * 서버 렌더에서는 false, 브라우저에서는 true.
 *
 * <p>{@code document} 가 없는 서버에서 포털을 만들 수 없어 "지금 브라우저인가"를 알아야
 * 한다. effect 안에서 setState 하는 방식이 흔하지만, 그건 렌더를 한 번 더 돌리고
 * React 규칙에도 걸린다. 바뀔 일이 없는 값이라 구독은 빈 함수로 둔다.
 */
const subscribeNothing = () => () => {};
const onClient = () => true;
const onServer = () => false;

/**
 * 모바일 햄버거 · 드로어.
 *
 * <p>하단 탭바는 {@link MobileTabBar} 가 맡는다 — 드로어는 열고 닫는 상태를,
 * 탭바는 현재 경로만 알면 되어 관심사가 다르다.
 *
 * <p>레이아웃에서 이 부분만 클라이언트 컴포넌트로 떼어냈다. 헤더·GNB 까지 통째로
 * {@code 'use client'} 로 만들면 카테고리 링크가 <b>서버 HTML 에서 빠져</b> 검색엔진이
 * 사이트 구조를 읽지 못한다.
 *
 * <p>드로어 안에서는 하위 메뉴를 <b>펼침/접힘</b>으로 다룬다. 좁은 화면에서 마우스오버
 * 드롭다운은 쓸 수 없고, 하위 항목을 전부 펼쳐 두면 목록이 40줄이 넘는다.
 *
 * <p>맨 아래 로그인 영역은 {@link HeaderAuth} 와 같은 것을 보여 준다. 예전에는 링크 두
 * 개가 박혀 있어 <b>로그인한 뒤에도 "로그인 · 회원가입"이 그대로 남았다.</b> 휴대폰에서는
 * 상단 유틸 바가 접혀 있어 여기가 로그인 상태를 알 수 있는 유일한 곳이라, 사용자는
 * 로그인이 풀린 줄 알게 된다.
 */
export function MobileDrawer({ nav }: { nav: NavItem[] }) {
  const [open, setOpen] = useState(false);
  const [expanded, setExpanded] = useState<string | null>(null);
  const pathname = usePathname();
  const router = useRouter();
  const { user, isAuthenticated } = useUserSession();
  const signOut = useSignOut();

  // 페이지를 옮기면 드로어를 닫는다. 열어둔 채로 이동하면 새 화면 위에 메뉴가 남는다.
  //
  // effect 가 아니라 렌더 중에 보정한다 — React 가 권장하는 "이전 렌더 값 비교" 방식이다.
  // effect 로 하면 새 화면이 한 번 그려진 뒤에 드로어가 닫혀 깜빡인다.
  const [lastPath, setLastPath] = useState(pathname);
  if (pathname !== lastPath) {
    setLastPath(pathname);
    setOpen(false);
    setExpanded(null);
  }

  /**
   * 펼친 항목을 화면 안으로 끌어온다.
   *
   * <p>목록 아래쪽에 있는 항목(고객센터 같은)을 누르면 하위 메뉴가 <b>화면 밖에서</b>
   * 열린다. 스크롤을 내려야 보이니 사용자에게는 <b>눌러도 아무 일도 안 일어난 것</b>처럼
   * 보인다 — 그래서 한 번 더 누르고, 그러면 이번엔 접힌다.
   *
   * <p>기준을 <b>묶음 전체가 아니라 열린 하위 목록</b>으로 잡는다. 묶음을 기준으로 하면
   * 하위 목록이 길 때 브라우저가 묶음의 <b>윗변</b>에 맞춰 올려버려서, 정작 보여야 할
   * 하위 항목이 여전히 화면 밖에 남는다.
   *
   * <p>{@code block: 'nearest'} 라 이미 다 보이면 움직이지 않는다. 필요한 만큼만 민다.
   */
  const subRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (expanded == null) return;
    const el = subRef.current;
    // 하위 목록이 그려진 뒤에 재야 한다. 펼치는 순간에는 아직 높이가 0 이다.
    const id = requestAnimationFrame(() => {
      el?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    });
    return () => cancelAnimationFrame(id);
  }, [expanded]);

  useEffect(() => {
    if (!open) return;

    const previous = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    window.addEventListener('keydown', onKeyDown);

    return () => {
      document.body.style.overflow = previous;
      window.removeEventListener('keydown', onKeyDown);
    };
  }, [open]);

  /**
   * 드로어를 <b>{@code <body>} 바로 밑으로</b> 옮겨 그린다.
   *
   * <p>이 컴포넌트는 헤더 안에서 렌더되는데, 헤더가 {@code position: sticky} 에
   * {@code z-index: 100} 이라 <b>쌓임 맥락</b>을 만든다. 그 안에서는 드로어의
   * {@code z-index: 200} 이 헤더 내부에서만 의미가 있고, 바깥 세상에서는 헤더의
   * 100 으로 취급된다.
   *
   * <p>그래서 페이지 어딘가에 100 보다 큰 요소가 있으면 <b>드로어를 뚫고 올라온다.</b>
   * 지점찾기의 네이버 지도가 그랬다 — 지도가 만든 내부 요소들이 루트에서 그대로
   * 경쟁하는데(지도 컨테이너에 position 이 없어 자기 맥락을 만들지 않는다), 그중
   * 저작권 표시가 열린 메뉴 위로 비쳐 보였다.
   *
   * <p>맥락 밖으로 꺼내면 z-index 가 제 값대로 동작한다. 헤더의 z-index 를 올리는
   * 방법도 있지만, 그건 다음에 z-index 를 쓰는 사람이 또 같은 함정에 빠진다.
   */
  const mounted = useSyncExternalStore(subscribeNothing, onClient, onServer);

  return (
    <>
      <button
        type="button"
        className={styles.hamburger}
        aria-label="메뉴 열기"
        aria-expanded={open}
        onClick={() => setOpen(true)}
      >
        <span />
        <span />
        <span />
      </button>

      {mounted &&
        createPortal(
          <>
            <div
              className={open ? styles.overlayOn : styles.overlay}
              onClick={() => setOpen(false)}
              aria-hidden="true"
            />

            <aside className={open ? styles.drawerOn : styles.drawer} aria-hidden={!open}>
        <div className={styles.drawerHead}>
          {/* 서랍도 흰 바탕이라 헤더와 같은 로고다 */}
          <Image src="/logo.svg" alt="한성금거래소" width={560} height={104} />
          <button
            type="button"
            className={styles.drawerClose}
            aria-label="메뉴 닫기"
            onClick={() => setOpen(false)}
          >
            ✕
          </button>
        </div>

        <nav className={styles.drawerNav}>
          {nav.map((item) =>
            item.children.length === 0 ? (
              <Link key={item.href} href={item.href} className={styles.drawerLink}>
                {item.label}
              </Link>
            ) : (
              <div key={item.href} className={styles.drawerGroup}>
                <button
                  type="button"
                  className={styles.drawerToggle}
                  aria-expanded={expanded === item.label}
                  onClick={() => setExpanded(expanded === item.label ? null : item.label)}
                >
                  {item.label}
                  <span className={expanded === item.label ? styles.caretOn : styles.caret} />
                </button>

                {expanded === item.label && (
                  <div ref={subRef} className={styles.drawerSub}>
                    <Link href={item.href}>{item.label} 전체</Link>
                    {item.children.map((child) => (
                      <Link key={child.href} href={child.href}>
                        {child.label}
                      </Link>
                    ))}
                  </div>
                )}
              </div>
            ),
          )}
        </nav>

            <div className={styles.drawerFoot}>
              {isAuthenticated ? (
                <>
                  <Link href="/mypage">
                    <strong>{user?.name ?? '고객'}</strong>님
                  </Link>
                  <button
                    type="button"
                    disabled={signOut.isPending}
                    onClick={() =>
                      // 홈에서 로그아웃하면 경로가 그대로라 드로어를 닫는 effect 가 돌지
                      // 않는다. 직접 닫는다.
                      signOut.mutate(undefined, {
                        onSuccess: () => {
                          setOpen(false);
                          router.replace('/');
                        },
                      })
                    }
                  >
                    {signOut.isPending ? '로그아웃 중…' : '로그아웃'}
                  </button>
                </>
              ) : (
                <>
                  <Link href="/login">로그인</Link>
                  <Link href="/signup">회원가입</Link>
                </>
              )}
            </div>
            </aside>
          </>,
          document.body,
        )}
    </>
  );
}
