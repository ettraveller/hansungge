import { unstable_rethrow } from 'next/navigation';
import { ApiError } from '@/types/api';

/**
 * 서버 컴포넌트용 조회 래퍼.
 *
 * <h2>왜 예외를 그대로 던지지 않는가</h2>
 * 서버 컴포넌트에서 fetch 가 실패하면 <b>페이지 전체가 오류 화면</b>이 된다. 백엔드가
 * 잠깐 내려갔다는 이유로 쇼핑몰 메인이 통째로 죽으면, 고객에게는 사이트가 없어진 것과 같다.
 *
 * 그래서 실패를 값으로 돌려주고, 화면이 "일시적으로 불러올 수 없습니다"를 그 자리에만
 * 표시하게 한다. 시세가 없을 때 "시세문의"를 보여주는 것과 같은 원칙이다
 * (설계서 §12.4 — 500 이 아니라 안내).
 *
 * <h2>Next.js 내부 오류는 삼키지 않는다</h2>
 * Next.js 는 {@code notFound()} / {@code redirect()} 와 <b>정적·동적 렌더링 판별</b>을
 * 예외로 신호한다({@code no-store} fetch 등). 그것까지 catch 하면
 * <ul>
 *   <li>{@code notFound()} 가 404 화면 대신 그냥 삼켜지고,</li>
 *   <li>빌드 시 동적으로 판정돼야 할 페이지가 <b>오류 폴백이 박힌 채 정적으로 굳는다.</b></li>
 * </ul>
 * 그래서 catch 블록 첫 줄에서 {@code unstable_rethrow} 로 프레임워크 오류를 되던진다.
 */
export type Loaded<T> = { data: T; error: null } | { data: null; error: string };

export async function load<T>(fetcher: () => Promise<T>, context: string): Promise<Loaded<T>> {
  try {
    return { data: await fetcher(), error: null };
  } catch (e) {
    // 반드시 첫 줄이어야 한다. 아래 로직이 프레임워크 오류를 건드리기 전에 되던진다.
    unstable_rethrow(e);

    const message =
      e instanceof ApiError
        ? `${e.code}: ${e.message}`
        : e instanceof Error
          ? e.message
          : '알 수 없는 오류';

    console.error(`[SSR] ${context} 조회 실패 — ${message}`);
    return { data: null, error: message };
  }
}

/** 백엔드에 닿지 못한 경우인가. 안내 문구를 구분하는 데 쓴다. */
export function isConnectionError(error: string | null): boolean {
  if (!error) return false;
  return /fetch failed|ECONNREFUSED|ENOTFOUND|network/i.test(error);
}
