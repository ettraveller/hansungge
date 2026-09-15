import { request, type RequestOptions } from '@/api/client';
import { adminAuth } from '@/stores/useAdminAuthStore';
import { ApiError } from '@/types/api';
import type { AdminLoginResponse } from '@/types/admin';

/**
 * 관리자 API 전용 클라이언트.
 *
 * 공개 클라이언트({@link request})와 나눠 둔 이유는 두 가지다.
 *  1. Authorization 헤더는 관리자 요청에만 붙어야 한다. 공개 API 는 SSR 에서도 불리는데
 *     서버에는 sessionStorage 가 없다.
 *  2. 401 재발급 재시도 로직이 공개 화면까지 흘러들면, 시세 조회 실패가 로그아웃을
 *     유발하는 식의 엉뚱한 결합이 생긴다.
 */

/** 재발급을 여러 요청이 동시에 트리거해도 실제 호출은 한 번만 나가게 묶어 둔다. */
let refreshInFlight: Promise<string> | null = null;

async function refreshAccessToken(): Promise<string> {
  const refreshToken = adminAuth.refreshToken();
  if (!refreshToken) {
    throw new ApiError('UNAUTHORIZED', '로그인이 필요합니다.', 401);
  }

  refreshInFlight ??= request<AdminLoginResponse>('/api/admin/auth/refresh', {
    method: 'POST',
    body: { refreshToken },
  })
    .then((res) => {
      if (!res.accessToken) {
        throw new ApiError('UNAUTHORIZED', '세션이 만료되었습니다. 다시 로그인해 주세요.', 401);
      }
      adminAuth.setAccessToken(res.accessToken);
      return res.accessToken;
    })
    .finally(() => {
      refreshInFlight = null;
    });

  return refreshInFlight;
}

async function call<T>(path: string, options: RequestOptions, token: string | null): Promise<T> {
  return request<T>(path, {
    ...options,
    headers: {
      ...options.headers,
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    // 관리자 데이터는 절대 캐시하지 않는다. 시세·재고가 한 박자 늦으면 오조작으로 이어진다.
    cache: 'no-store',
  });
}

export async function adminRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  try {
    return await call<T>(path, options, adminAuth.accessToken());
  } catch (error) {
    if (!(error instanceof ApiError) || error.status !== 401) throw error;

    // 401 은 "토큰이 만료됐다"일 수도, "이미 무효화됐다"일 수도 있다.
    // 재발급을 한 번만 시도하고, 그래도 401 이면 세션을 정리한다.
    let token: string;
    try {
      token = await refreshAccessToken();
    } catch {
      adminAuth.signOut();
      throw new ApiError('UNAUTHORIZED', '세션이 만료되었습니다. 다시 로그인해 주세요.', 401);
    }

    try {
      return await call<T>(path, options, token);
    } catch (retryError) {
      if (retryError instanceof ApiError && retryError.status === 401) {
        adminAuth.signOut();
      }
      throw retryError;
    }
  }
}

export const adminApi = {
  get: <T>(path: string, options?: RequestOptions) =>
    adminRequest<T>(path, { ...options, method: 'GET' }),
  post: <T>(path: string, options?: RequestOptions) =>
    adminRequest<T>(path, { ...options, method: 'POST' }),
  put: <T>(path: string, options?: RequestOptions) =>
    adminRequest<T>(path, { ...options, method: 'PUT' }),
  patch: <T>(path: string, options?: RequestOptions) =>
    adminRequest<T>(path, { ...options, method: 'PATCH' }),
  delete: <T>(path: string, options?: RequestOptions) =>
    adminRequest<T>(path, { ...options, method: 'DELETE' }),
};
