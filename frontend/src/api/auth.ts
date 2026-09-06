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
  /**
   * 자체 가입에만 보낸다.
   *
   * <p>본인확인 단계에서도 보내지만 그것은 <b>병합용</b>이다 — 이미 가입한 사람이면
   * 그 자리에서 기존 계정에 붙인다. 신규 가입이면 서버가 그 값을 들고 있지 않으므로
   * 여기서 다시 보낸다. 단계 사이에 비밀번호를 서버에 보관하지 않기 위해서다.
   */
  password?: string | null;
}

export interface IdentityStart {
  startUrl: string;
  signUpToken: string;
}

export function completeSignUp(input: SignUpInput) {
  return request<SignInResult>('/api/v1/auth/signup', { method: 'POST', body: input });
}

/** 쿠키만으로 동작한다. 실패는 "로그인 상태가 아니다"라는 뜻일 뿐 오류가 아니다. */
/* ------------------------------------------------------------------ */
/* 자체 가입 — 이메일 인증 → 본인확인 → 약관                            */
/* ------------------------------------------------------------------ */

/**
 * 인증번호를 보낸다.
 *
 * <p>이미 가입된 주소여도 성공으로 응답한다. 서버가 일부러 그렇게 한다 —
 * 알려주면 회원 명단을 훑는 도구가 된다. 중복은 가입을 마치는 시점에 걸린다.
 */
export function sendEmailCode(email: string) {
  return request<void>('/api/v1/auth/email/verification', { method: 'POST', body: { email } });
}

export function confirmEmailCode(email: string, code: string) {
  return request<{ signUpToken: string }>('/api/v1/auth/email/verification/confirm', {
    method: 'POST',
    body: { email, code },
  });
}

/** 본인확인 창 주소를 받는다. 자체 가입과 소셜 가입이 같은 경로를 쓴다. */
export function startIdentity(signUpToken: string, returnUrl: string) {
  return request<IdentityStart>('/api/v1/auth/identity/start', {
    method: 'POST',
    body: { signUpToken, returnUrl },
  });
}

/**
 * 본인확인 결과를 제출한다.
 *
 * <p>이미 가입한 신원이면 <b>여기서 바로 로그인된다</b> — 응답에 accessToken 이 온다.
 * 처음 보는 신원이면 약관 화면으로 갈 signUpToken 이 온다.
 */
export function completeIdentity(signUpToken: string, providerToken: string, password?: string) {
  return request<SignInResult>('/api/v1/auth/identity/complete', {
    method: 'POST',
    body: { signUpToken, providerToken, ...(password ? { password } : {}) },
  });
}

/** 이메일 + 비밀번호 로그인. */
export function signInWithPassword(email: string, password: string) {
  return request<SignInResult>('/api/v1/auth/login', {
    method: 'POST',
    body: { email, password },
  });
}

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

export function updateProfile(input: { name: string }) {
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
