'use client';

import { useEffect } from 'react';

const KEY = 'hanseongge:preview-scroll';

/**
 * 미리보기가 새로 뜰 때 보던 자리로 되돌린다.
 *
 * <p>옆 미리보기는 고칠 때마다 주소가 바뀌어 통째로 새로 뜬다. 그때마다 맨 위로
 * 돌아가면 푸터를 고치는 사람은 글자 하나마다 다시 내려가야 한다.
 *
 * <p>{@code at} 은 관리자가 보고 있는 탭이다. 탭을 바꾸면 그 탭이 고치는 자리로
 * 옮겨 주고(푸터 → 맨 아래), 같은 탭 안에서는 보던 자리를 지킨다.
 */
export function ScrollKeeper({ at }: { at: string }) {
  useEffect(() => {
    let saved: { at?: string; y?: number } = {};
    try {
      saved = JSON.parse(sessionStorage.getItem(KEY) ?? '{}');
    } catch {
      // 못 읽으면 맨 위에서 시작한다
    }

    const target =
      saved.at === at
        ? (saved.y ?? 0)
        : at === 'footer'
          ? document.documentElement.scrollHeight
          : 0;
    // 그림이 자리를 잡은 뒤에 옮긴다 — 바로 옮기면 아직 짧은 문서의 끝에서 멈춘다.
    const timer = setTimeout(() => window.scrollTo(0, target), 60);

    const remember = () => {
      try {
        sessionStorage.setItem(KEY, JSON.stringify({ at, y: window.scrollY }));
      } catch {
        // 저장 공간을 못 쓰는 창이면 기억하지 않는다
      }
    };
    remember();
    window.addEventListener('scroll', remember, { passive: true });
    return () => {
      clearTimeout(timer);
      window.removeEventListener('scroll', remember);
    };
  }, [at]);

  return null;
}
