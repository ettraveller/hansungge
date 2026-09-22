import { authed } from '@/api/authedClient';
import { request } from '@/api/client';
import type {
  AgreementCode,
  AgreementStatus,
  AuthProviders,
  IdentityResult,
  IdentityStart,
  OAuthAuthorize,
  OAuthProviderType,
  PasswordResetGrant,
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

export type AgreementChoice = { type: AgreementCode; agreed: boolean };

// =====================================================================
// 로그인 (토큰 불필요)
// =====================================================================

/** 로그인 화면 구성. 쓸 수 있는 제공자·가입 가능 여부·인증번호 로그인 가능 여부. */
export function fetchAuthProviders() {
  return request<AuthProviders>('/api/v1/auth/providers');
}

/** 휴대폰번호 + 비밀번호. 하이픈이 있어도 서버가 정규화한다. */
export function signInWithPassword(phone: string, password: string) {
  return request<SignInResult>('/api/v1/auth/login', {
    method: 'POST',
    body: { phone, password },
  });
}

/**
 * 인증번호 문자 요청.
 *
 * <p>가입하지 않은 번호여도 성공으로 응답한다. 서버가 일부러 그렇게 한다 — 알려주면 회원
 * 명단을 훑는 도구가 된다. 문자는 회원에게만 실제로 간다.
 */
export function sendLoginCode(phone: string) {
  return request<void>('/api/v1/auth/sms/send', { method: 'POST', body: { phone } });
}

export function signInWithCode(phone: string, code: string) {
  return request<SignInResult>('/api/v1/auth/sms/login', {
    method: 'POST',
    body: { phone, code },
  });
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

// =====================================================================
// 가입 — 자체: 약관 → 간편인증 → (비밀번호·이메일) / 소셜: 약관
// =====================================================================

/** 자체(휴대폰) 가입 시작 — 약관 동의. */
export function startSignUp(agreements: AgreementChoice[]) {
  return request<{ signUpToken: string }>('/api/v1/auth/signup/start', {
    method: 'POST',
    body: { agreements },
  });
}

/**
 * 소셜 가입 — 약관 동의로 끝난다. 본인인증을 받지 않는다.
 *
 * @param signUpToken 소셜 콜백에서 받은 토큰
 */
export function signUpSocial(signUpToken: string, agreements: AgreementChoice[]) {
  return request<SignInResult>('/api/v1/auth/signup/social', {
    method: 'POST',
    body: { signUpToken, agreements },
  });
}

/** 본인확인 창을 여는 폼. 화면은 그 폼을 그대로 전송한다({@code submitIdentityForm}). */
export function startIdentity(signUpToken: string) {
  return request<IdentityStart>('/api/v1/auth/identity/start', {
    method: 'POST',
    body: { signUpToken },
  });
}

/**
 * 본인확인 결과 제출 (가입).
 *
 * @param token         본인확인 시작 때 받은 진행 토큰
 * @param providerToken 복귀 화면 주소의 providerToken
 */
export function completeIdentity(token: string, providerToken: string) {
  return request<IdentityResult>('/api/v1/auth/identity/complete', {
    method: 'POST',
    body: { token, providerToken },
  });
}

/** 가입 중 선택 이메일로 인증번호 보내기. */
export function sendSignUpEmailCode(signUpToken: string, email: string) {
  return request<void>('/api/v1/auth/email/verification', {
    method: 'POST',
    body: { signUpToken, email },
  });
}

export interface CompleteSignUpInput {
  signUpToken: string;
  password: string;
  email?: string;
  emailCode?: string;
}

/** 자체 가입 완료. 이름·생년월일·휴대폰은 보내지 않는다 — 본인확인 값을 서버가 쓴다. */
export function completeSignUp(input: CompleteSignUpInput) {
  return request<SignInResult>('/api/v1/auth/signup/complete', { method: 'POST', body: input });
}

// =====================================================================
// 비밀번호 찾기 — 본인확인 → 새 비밀번호
// =====================================================================

export function startPasswordReset() {
  return request<IdentityStart>('/api/v1/auth/password/reset/identity/start', { method: 'POST' });
}

export function completePasswordResetIdentity(token: string, providerToken: string) {
  return request<PasswordResetGrant>('/api/v1/auth/password/reset/identity/complete', {
    method: 'POST',
    body: { token, providerToken },
  });
}

/** 새 비밀번호. 성공하면 다른 기기의 세션이 전부 끊기고 잠금도 풀린다. */
export function resetPassword(resetToken: string, newPassword: string) {
  return request<void>('/api/v1/auth/password/reset', {
    method: 'POST',
    body: { resetToken, newPassword },
  });
}

// =====================================================================
// 세션
// =====================================================================

/** 쿠키만으로 동작한다. 실패는 "로그인 상태가 아니다"라는 뜻일 뿐 오류가 아니다. */
export function refreshSession() {
  return request<SignInResult>('/api/v1/auth/refresh', { method: 'POST' });
}

export function signOut() {
  return request<void>('/api/v1/auth/logout', { method: 'POST' });
}

// =====================================================================
// 마이페이지 (Bearer) — 401 재발급·재시도는 authedClient 가 맡는다
// =====================================================================

export function fetchMe() {
  return authed<User>('/api/v1/me');
}

/**
 * 로그인한 회원의 본인인증 — 매입 신청·정산계좌 앞, 또는 소셜 회원이 휴대폰 로그인을 쓰려고
 * 번호를 등록할 때.
 */
export function startMemberIdentity() {
  return authed<IdentityStart>('/api/v1/me/identity/start', { method: 'POST' });
}

/** 본인인증 결과 제출. 다른 사람 명의면 409 IDENTITY_MISMATCH. */
export function completeMemberIdentity(token: string, providerToken: string) {
  return authed<User>('/api/v1/me/identity/complete', {
    method: 'POST',
    body: { token, providerToken },
  });
}

/**
 * 비밀번호 설정·변경.
 *
 * <p>비밀번호가 없는 회원(소셜 가입)은 {@code currentPassword} 없이 새로 정한다.
 */
export function changePassword(newPassword: string, currentPassword?: string) {
  return authed<void>('/api/v1/me/password', {
    method: 'POST',
    body: { newPassword, ...(currentPassword ? { currentPassword } : {}) },
  });
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

/** 비밀번호가 없는 회원의 마지막 하나는 해제할 수 없다 — 로그인 수단이 사라진다. */
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
