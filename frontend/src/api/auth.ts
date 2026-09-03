import { authed } from '@/api/authedClient';
import { request } from '@/api/client';
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
// 인증 필요 (Bearer) — 401 재발급·재시도는 authedClient 가 맡는다
// =====================================================================

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
