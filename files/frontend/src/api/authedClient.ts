import { request, type RequestOptions } from '@/api/client';
import { userAuth } from '@/stores/useUserAuthStore';
import { ApiError } from '@/types/api';
import type { SignInResult } from '@/types/user';

/**
 * 로그인이 필요한 회원 API 호출.
 *
 * <p>Access 토큰은 메모리에만 있고 30분이면 만료된다. 화면을 오래 열어 둔 사용자는
 * 이 경로를 자주 지나므로, 401 이면 HttpOnly Refresh 쿠키로 한 번 재발급하고 재시도한다.
 * 여기서 걸러내지 않으면 멀쩡한 세션인데도 "로그인이 필요합니다"가 뜬다.
 *
 * <p>재발급 호출을 {@code api/auth.ts} 에서 가져오지 않고 여기서 직접 만든다 —
 * auth.ts 가 이 모듈을 쓰므로 서로 참조하면 순환 의존이 된다.
 */

/** 재발급을 여러 요청이 동시에 트리거해도 실제 호출은 한 번만 나가게 묶어 둔다. */
let refreshInFlight: Promise<string> | null = null;

async function renewAccessToken(): Promise<string> {
  refreshInFlight ??= request<SignInResult>('/api/v1/auth/refresh', { method: 'POST' })
    .then((res) => {
      if (!res.accessToken) throw new ApiError('UNAUTHORIZED', '세션이 없습니다.', 401);
      userAuth.setAccessToken(res.accessToken);
      return res.accessToken;
    })
    .finally(() => {
      refreshInFlight = null;
    });
  return refreshInFlight;
}

function withToken<T>(path: string, options: RequestOptions, token: string | null) {
  return request<T>(path, {
    ...options,
    headers: {
      ...options.headers,
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    // 회원 개인정보·장바구니는 캐시하지 않는다.
    cache: 'no-store',
  });
}

export async function authed<T>(path: string, options: RequestOptions = {}): Promise<T> {
  try {
    return await withToken<T>(path, options, userAuth.accessToken());
  } catch (error) {
    if (!(error instanceof ApiError) || error.status !== 401) throw error;

    let token: string;
    try {
      token = await renewAccessToken();
    } catch {
      userAuth.signOut();
      throw new ApiError('UNAUTHORIZED', '로그인이 필요합니다.', 401);
    }

    try {
      return await withToken<T>(path, options, token);
    } catch (retryError) {
      if (retryError instanceof ApiError && retryError.status === 401) userAuth.signOut();
      throw retryError;
    }
  }
}
