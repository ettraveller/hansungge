import { request, type RequestOptions } from '@/api/client';
import { userAuth } from '@/stores/useUserAuthStore';
import { ApiError } from '@/types/api';
import type {
  AgreementCode,
  AgreementStatus,
  AuthProviders,
  OAuthAuthorize,
  OAuthProviderType,
  SignInResult,
  SocialAccount,
  User,
} from '@/types/user';

/**
 * 회원 인증 API.
 *
 * <p>Refresh 토큰은 HttpOnly 쿠키라 코드가 만질 수 없다. {@code credentials: 'include'}
 * 로 브라우저가 알아서 실어 보내며, 그것이 이 파일에 refresh 토큰 문자열이 한 번도
 * 등장하지 않는 이유다.
 */

// =====================================================================
// 인증 (토큰 불필요)
// =====================================================================

/** 로그인 화면 구성. 쓸 수 있는 제공자만 온다 — 버튼을 하드코딩하지 않는다. */
export function fetchAuthProviders() {
  return request<AuthProviders>('/api/v1/auth/providers');
}

export function startOAuth(provider: OAuthProviderType) {
  return request<OAuthAuthorize>(`/api/v1/auth/oauth/${provider}/authorize`, { method: 'POST' });
}

export function completeOAuth(provider: OAuthProviderType, code: string, state: string) {
  return request<SignInResult>(`/api/v1/auth/oauth/${provider}/callback`, {
    method: 'POST',
    body: { code, state },
  });
}

export interface SignUpInput {
  signUpToken: string;
  agreements: { type: AgreementCode; agreed: boolean }[];
  name?: string | null;
}

export function completeSignUp(input: SignUpInput) {
  return request<SignInResult>('/api/v1/auth/signup', { method: 'POST', body: input });
}

/** 쿠키만으로 동작한다. 실패는 "로그인 상태가 아니다"라는 뜻일 뿐 오류가 아니다. */
export function refreshSession() {
  return request<SignInResult>('/api/v1/auth/refresh', { method: 'POST' });
}

export function signOut() {
  return request<void>('/api/v1/auth/logout', { method: 'POST' });
}

// =====================================================================
// 인증 필요 (Bearer)
// =====================================================================

/** 재발급을 여러 요청이 동시에 트리거해도 실제 호출은 한 번만 나가게 묶어 둔다. */
let refreshInFlight: Promise<string> | null = null;

async function renewAccessToken(): Promise<string> {
  refreshInFlight ??= refreshSession()
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
    // 회원 개인정보는 캐시하지 않는다.
    cache: 'no-store',
  });
}

/**
 * 로그인이 필요한 요청.
 *
 * <p>401 이면 Refresh 쿠키로 한 번 재발급하고 재시도한다. Access 토큰이 30분이라
 * 화면을 오래 열어 둔 사용자는 이 경로를 자주 지난다 — 여기서 걸러내지 않으면
 * 멀쩡한 세션인데도 "로그인이 필요합니다"가 뜬다.
 */
async function authed<T>(path: string, options: RequestOptions = {}): Promise<T> {
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

export function fetchMe() {
  return authed<User>('/api/v1/me');
}

export function updateProfile(input: { name: string; phone: string }) {
  return authed<User>('/api/v1/me', { method: 'PATCH', body: input });
}

export function fetchAgreements() {
  return authed<AgreementStatus[]>('/api/v1/me/agreements');
}

/** 필수 약관은 철회할 수 없다 — 서버가 400 으로 막는다. */
export function changeAgreement(type: AgreementCode, agreed: boolean) {
  return authed<AgreementStatus[]>('/api/v1/me/agreements', {
    method: 'PATCH',
    body: { type, agreed },
  });
}

export function fetchSocialAccounts() {
  return authed<SocialAccount[]>('/api/v1/me/social-accounts');
}

/** 마지막 하나는 해제할 수 없다 — 로그인 수단이 사라진다. */
export function disconnectSocial(provider: OAuthProviderType) {
  return authed<SocialAccount[]>(`/api/v1/me/social-accounts/${provider}`, { method: 'DELETE' });
}

export function linkSocial(provider: OAuthProviderType, code: string, state: string) {
  return authed<SocialAccount[]>(`/api/v1/me/social-accounts/${provider}/link`, {
    method: 'POST',
    body: { code, state },
  });
}

export function withdraw() {
  return authed<void>('/api/v1/me/withdraw', { method: 'POST' });
}
