'use client';

import { useEffect, useRef, type ReactNode } from 'react';

/**
 * 머리띠 — 내려가면 메뉴 줄이 접히고, 올리면 돌아온다.
 *
 * <h2>왜</h2>
 * 머리가 2층이라 스크롤 내내 136px 를 차지한다. 좁은 노트북에서는 메뉴가 두 줄로
 * 꺾여 192px 까지 간다. 읽는 동안에는 1층만 남긴다.
 *
 * <h2>지키는 것 세 가지</h2>
 * <ol>
 *   <li><b>맨 위에서는 접지 않는다.</b> 들어오자마자 메뉴가 사라지면 이 사이트에
 *       무엇이 있는지 알 길이 없다.</li>
 *   <li><b>키보드로 들어오면 펼친다.</b> 접힌 채로 Tab 이 메뉴에 닿으면 보이지 않는
 *       곳에 초점이 가 있게 된다 — 화면에는 아무 일도 없는데 Enter 가 먹는다.
 *       같은 이유로 초점이 머리띠 안에 있는 동안에는 접지 않는다.</li>
 *   <li><b>움직임을 꺼 둔 사람에게는 아예 걸지 않는다.</b> 메뉴가 나타났다 사라지는
 *       것 자체가 움직임이다. 그 설정은 "전환을 짧게 해 달라"가 아니라
 *       "움직이지 말라"는 뜻이다.</li>
 * </ol>
 *
 * <h2>다시 그리지 않는다</h2>
 * 클래스를 DOM 에 직접 붙인다. 스크롤마다 리렌더가 돌면 정작 부드러워야 할
 * 순간에 프레임이 떨어진다. 상태가 화면 밖으로 새어 나갈 일도 없다.
 */
export function StickyHeader({
  className,
  compactClass,
  children,
}: {
  className: string;
  /** 접힌 상태에 붙일 클래스 (CSS 모듈의 `styles.compact`) */
  compactClass: string;
  children: ReactNode;
}) {
  const ref = useRef<HTMLElement>(null);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

    /** 이 아래에서는 늘 펼쳐 둔다. 첫 화면에서 메뉴가 사라지면 안 된다 */
    const KEEP_OPEN_UNTIL = 160;
    /** 트랙패드의 미세한 떨림을 방향 전환으로 읽지 않기 위한 문턱(px) */
    const JITTER = 2;
    /**
     * 접고 펴는 동안 스크롤을 보지 않는 시간(ms). CSS 전환(280ms)보다 넉넉히 길다.
     *
     * <h3>없으면 메뉴가 영원히 깜빡인다</h3>
     * 머리띠는 본문 위에 자리를 차지하고 있다. 펼쳐지면 본문이 그만큼 아래로
     * 밀리는데, 브라우저의 스크롤 앵커링이 보던 자리를 지키려고 {@code scrollY} 를
     * 같은 만큼 더해 준다. 그 보정이 scroll 이벤트로 올라오고, 그것을 "사용자가
     * 아래로 내렸다"로 읽으면 다시 접는다. 접으면 반대로 보정되어 또 펼친다.
     *
     * <p>실제로 그렇게 만들었다가 잡았다 — 추적해 보니 scrollY 가
     * {@code 707 → 681 → 707 → 681} 로 끝없이 진동했다.
     *
     * <p>그래서 <b>자기가 일으킨 스크롤은 보지 않는다.</b> 풀릴 때 기준점을 지금
     * 위치로 다시 잡아, 그동안 쌓인 보정분이 사용자의 움직임으로 섞이지 않게 한다.
     */
    const SETTLE_MS = 340;

    let last = window.scrollY;
    let queued = false;
    let settling: ReturnType<typeof setTimeout> | null = null;

    /** 접거나 편 뒤, 그 때문에 생기는 스크롤이 가라앉을 때까지 기다린다 */
    const hold = () => {
      if (settling) clearTimeout(settling);
      settling = setTimeout(() => {
        settling = null;
        last = window.scrollY;
      }, SETTLE_MS);
    };

    const setCompact = (on: boolean) => {
      if (el.classList.contains(compactClass) === on) return;
      el.classList.toggle(compactClass, on);
      hold();
    };

    const update = () => {
      queued = false;
      // 내가 일으킨 움직임이다. 기준점은 풀릴 때 다시 잡는다.
      if (settling) return;

      const y = window.scrollY;
      const dy = y - last;
      last = y;

      if (y < KEEP_OPEN_UNTIL) {
        setCompact(false);
        return;
      }
      // 초점이 머리띠 안에 있으면 건드리지 않는다 (위 주석 2)
      if (el.contains(document.activeElement)) return;

      if (dy > JITTER) setCompact(true);
      else if (dy < -JITTER) setCompact(false);
    };

    // 스크롤은 한 프레임에 여러 번 온다. 프레임당 한 번만 본다.
    const onScroll = () => {
      if (queued) return;
      queued = true;
      requestAnimationFrame(update);
    };

    // 키보드로 머리띠에 들어오면 펼친다 (위 주석 2)
    const onFocusIn = () => setCompact(false);

    window.addEventListener('scroll', onScroll, { passive: true });
    el.addEventListener('focusin', onFocusIn);
    return () => {
      window.removeEventListener('scroll', onScroll);
      el.removeEventListener('focusin', onFocusIn);
      if (settling) clearTimeout(settling);
    };
  }, [compactClass]);

  return (
    <header ref={ref} className={className}>
      {children}
    </header>
  );
}
