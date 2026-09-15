'use client';

import { useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useEffect, useRef } from 'react';

import { priceApi } from '@/api/prices';
import { cartKeys } from '@/hooks/useCart';

/**
 * 확인 주기.
 *
 * <p>짧을수록 빨리 반영되지만 열어 둔 화면 수만큼 요청이 는다. 묻는 것은 서버가 캐시해 둔
 * 숫자 하나라 20초면 부담이 없고, 고시를 마친 관리자가 쇼핑몰 탭으로 넘어가는 순간에는
 * 아래의 "탭으로 돌아왔을 때" 확인이 먼저 반영한다.
 */
const POLL_MS = 20_000;

/**
 * 열어 둔 쇼핑몰 화면에 새 시세를 반영한다. 그리는 것은 없다.
 *
 * <h2>왜 필요한가</h2>
 * 쇼핑몰 페이지는 요청할 때마다 서버에서 새로 그린다. 새로고침하거나 다른 페이지로 옮기면
 * 새 시세가 나오지만, <b>열어 둔 채로 있으면 아무 일도 일어나지 않는다</b> — 화면은 자기가
 * 그려진 뒤에 시세가 바뀌었다는 것을 알 방법이 없다. 관리자가 고시해도 고객은 옛 값을 보고
 * 있게 된다.
 *
 * <h2>어떻게</h2>
 * 시세표 전체가 아니라 <b>시세 버전</b>(숫자 하나)만 주기적으로 묻고, 달라졌을 때만
 * {@code router.refresh()} 로 서버 컴포넌트를 다시 그린다. 페이지를 새로 여는 것이 아니라서
 * 스크롤 위치와 고른 옵션이 그대로 남는다. 장바구니 금액은 클라이언트 캐시에 있으므로
 * 함께 무효화한다.
 *
 * <h2>하지 않는 것</h2>
 * <ul>
 *   <li>탭이 가려져 있으면 묻지 않는다. 보는 사람이 없는 화면을 갱신할 이유가 없고,
 *       돌아오는 순간 한 번 묻는다</li>
 *   <li>확인이 실패해도 화면을 건드리지 않는다. 다음 주기에 다시 묻는다</li>
 * </ul>
 *
 * @param initialVersion 서버가 이 화면을 그릴 때의 버전. 없으면 첫 확인값을 기준으로 삼는다.
 *                       서버 값을 기준으로 두는 이유는, 그리는 사이에 고시가 끼면 첫 확인값이
 *                       이미 새 버전이라 바뀐 것을 놓치기 때문이다
 */
export function PriceChangeWatcher({ initialVersion }: { initialVersion: number | null }) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const known = useRef<number | null>(initialVersion);

  useEffect(() => {
    let alive = true;
    let timer: ReturnType<typeof setTimeout> | undefined;

    const check = async () => {
      if (document.visibilityState !== 'visible') return;
      try {
        const { version } = await priceApi.version();
        if (!alive) return;
        if (known.current != null && version !== known.current) {
          router.refresh();
          void queryClient.invalidateQueries({ queryKey: cartKeys.all });
        }
        known.current = version;
      } catch {
        // 화면은 그대로 둔다. 다음 주기에 다시 묻는다.
      }
    };

    const loop = () => {
      timer = setTimeout(async () => {
        await check();
        if (alive) loop();
      }, POLL_MS);
    };

    const onReturn = () => {
      if (document.visibilityState === 'visible') void check();
    };

    loop();
    document.addEventListener('visibilitychange', onReturn);
    window.addEventListener('focus', onReturn);

    return () => {
      alive = false;
      clearTimeout(timer);
      document.removeEventListener('visibilitychange', onReturn);
      window.removeEventListener('focus', onReturn);
    };
  }, [router, queryClient]);

  return null;
}
