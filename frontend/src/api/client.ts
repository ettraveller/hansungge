import { ApiError, type ErrorResponse } from '@/types/api';

/**
 * 백엔드 API 클라이언트.
 *
 * 원칙 (설계서 §2.1):
 *  - 프론트엔드는 가격을 계산하지 않는다. 서버가 준 값을 표시만 한다.
 *  - 주문/결제 요청에 금액 필드를 담지 않는다. 서버가 다시 계산한다.
 *
 * 베이스 URL 은 환경변수에서 온다. 코드에 도메인을 하드코딩하지 않는다.
 *   .env.development → http://localhost:8080
 *   .env.production  → https://api.hanseongge.com
 */
const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080';

const FALLBACK_MESSAGE = '일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.';

export interface RequestOptions extends Omit<RequestInit, 'body'> {
  /** JSON 으로 직렬화할 요청 본문 */
  body?: unknown;
  /** 쿼리 파라미터 (undefined/null/빈문자열은 제외된다) */
  query?: Record<string, string | number | boolean | undefined | null>;
  /** Next.js 캐시 옵션 (SSR 페이지에서 사용) */
  next?: NextFetchRequestConfig;
}

function buildUrl(path: string, query?: RequestOptions['query']): string {
  const url = new URL(path.startsWith('/') ? path : `/${path}`, BASE_URL);
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value != null && value !== '') {
        url.searchParams.append(key, String(value));
      }
    }
  }
  return url.toString();
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { body, query, headers, next, ...rest } = options;

  const response = await fetch(buildUrl(path, query), {
    ...rest,
    next,
    headers: {
      'Content-Type': 'application/json',
      ...headers,
    },
    // 관리자 토큰은 쿠키가 아니라 응답 본문으로 오고 Authorization 헤더로 되돌아간다
    // (SPA 라 쿠키를 쓰면 CSRF 방어를 따로 붙여야 한다). 지금 쿠키를 쓰는 경로는 없지만,
    // 회원 세션이 붙을 때를 위해 자격증명은 허용해 둔다. 백엔드 CORS 도 이에 맞춰
    // allowCredentials=true + 명시적 origin 목록으로 설정돼 있다.
    credentials: 'include',
    body: body == null ? undefined : JSON.stringify(body),
  });

  // 204 등 본문 없는 응답
  if (response.status === 204) {
    return undefined as T;
  }

  let payload: unknown = null;
  try {
    payload = await response.json();
  } catch {
    // 본문이 없거나 JSON 이 아닌 경우
  }

  if (!response.ok) {
    const error = (payload ?? {}) as Partial<ErrorResponse>;
    throw new ApiError(
      error.code ?? 'INTERNAL_ERROR',
      error.message ?? FALLBACK_MESSAGE,
      response.status,
      error.timestamp,
      error.errors,
    );
  }

  return payload as T;
}

export const api = {
  get: <T>(path: string, options?: RequestOptions) => request<T>(path, { ...options, method: 'GET' }),
  post: <T>(path: string, options?: RequestOptions) => request<T>(path, { ...options, method: 'POST' }),
  put: <T>(path: string, options?: RequestOptions) => request<T>(path, { ...options, method: 'PUT' }),
  patch: <T>(path: string, options?: RequestOptions) => request<T>(path, { ...options, method: 'PATCH' }),
  delete: <T>(path: string, options?: RequestOptions) => request<T>(path, { ...options, method: 'DELETE' }),
};
