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

/**
 * 서버 렌더링이 쓸 기준 주소 — 같은 서버의 백엔드로 직결한다.
 *
 * <p>Next 서버에서 공개 주소(api.hanseongge.com)로 나가면 nginx 를 한 바퀴 돌아
 * 자기 자신에게 온다. 그런데 그 길에는 IP 당 요청 속도 제한이 걸려 있고, 한 페이지
 * 렌더링이 API 를 열 번 넘게 부르므로 <b>새로고침 몇 번이면 서버가 자기 IP 로
 * 제한에 걸린다</b> — 시세·상품·배너·메뉴가 통째로 실패 폴백으로 그려지던 운영
 * 장애(2026-11-21)가 이것이다. 루프백으로 직결하면 nginx 도 제한도 지나지 않고,
 * 왕복도 빠르다.
 *
 * <p>주소 짝은 배포 구성(deploy.yml·deploy-qa.yml·systemd)이 정한 포트와 한 쌍이다.
 * 런타임 환경변수 {@code API_INTERNAL_URL} 이 있으면 그것이 먼저다 — 구성이 바뀌는
 * 날 코드 수정 없이 덮어쓰는 문이다. 브라우저는 여전히 공개 주소를 쓴다.
 */
function serverBaseUrl(): string {
  const internal = process.env.API_INTERNAL_URL;
  if (internal) return internal;
  if (BASE_URL === 'https://hanseongge.com/qa-api') return 'http://127.0.0.1:8180';
  if (BASE_URL === 'https://api.hanseongge.com') return 'http://127.0.0.1:8080';
  return BASE_URL;
}

const FALLBACK_MESSAGE = '일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.';

export interface RequestOptions extends Omit<RequestInit, 'body'> {
  /** 요청 본문. {@link FormData} 면 그대로 보내고, 그 밖에는 JSON 으로 직렬화한다. */
  body?: unknown;
  /** 쿼리 파라미터 (undefined/null/빈문자열은 제외된다) */
  query?: Record<string, string | number | boolean | undefined | null>;
  /** Next.js 캐시 옵션 (SSR 페이지에서 사용) */
  next?: NextFetchRequestConfig;
}

function buildUrl(path: string, query?: RequestOptions['query']): string {
  // 기준 주소에 경로가 있으면 이어 붙인다.
  //
  // new URL('/api/v1/x', 'https://a.com/qa-api') 는 'https://a.com/api/v1/x' 다 —
  // 두 번째 인자의 경로가 통째로 버려진다. 규격이 그렇다. 기준 주소가
  // https://api.hanseongge.com 처럼 경로가 없을 때는 차이가 없어서 오래 드러나지
  // 않았는데, QA(https://hanseongge.com/qa-api)를 띄우자 모든 호출이 운영 쪽
  // /api/... 로 갔다. 그런 경로가 없으니 전부 실패하고, 화면에는 "일시적인 오류"만
  // 떴다 (2026-11-09).
  const raw = typeof window === 'undefined' ? serverBaseUrl() : BASE_URL;
  const base = raw.replace(/\/+$/, '');
  const url = new URL(base + (path.startsWith('/') ? path : `/${path}`));
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

  // 파일 업로드.
  //
  // ⚠️ Content-Type 을 직접 넣으면 안 된다. multipart 는 헤더에 경계 문자열
  // (`boundary=----WebKitFormBoundary…`)이 함께 들어가야 하고, 그 값은 fetch 가
  // FormData 를 보면서 만든다. 손으로 `multipart/form-data` 만 적으면 경계가
  // 빠져서 서버가 본문을 파싱하지 못하고, 원인을 말해주지 않는 400 이 돌아온다.
  const isMultipart = typeof FormData !== 'undefined' && body instanceof FormData;

  const response = await fetch(buildUrl(path, query), {
    ...rest,
    next,
    headers: {
      ...(isMultipart ? {} : { 'Content-Type': 'application/json' }),
      ...headers,
    },
    // 관리자 토큰은 쿠키가 아니라 응답 본문으로 오고 Authorization 헤더로 되돌아간다
    // (SPA 라 쿠키를 쓰면 CSRF 방어를 따로 붙여야 한다). 지금 쿠키를 쓰는 경로는 없지만,
    // 회원 세션이 붙을 때를 위해 자격증명은 허용해 둔다. 백엔드 CORS 도 이에 맞춰
    // allowCredentials=true + 명시적 origin 목록으로 설정돼 있다.
    credentials: 'include',
    body: body == null ? undefined : isMultipart ? (body as FormData) : JSON.stringify(body),
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
