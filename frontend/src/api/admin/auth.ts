import { request } from '@/api/client';
import { adminApi } from '@/api/admin/client';
import type {
  AdminLoginResponse,
  AdminOtpActivateResult,
  AdminOtpSetup,
  AdminOtpStatus,
  AdminUser,
} from '@/types/admin';

/** 로그인은 토큰이 없는 상태에서 부르므로 공개 클라이언트를 쓴다. */
export function login(loginId: string, password: string) {
  return request<AdminLoginResponse>('/api/admin/auth/login', {
    method: 'POST',
    body: { loginId, password },
  });
}

// ---------- 로그인 2단계 ----------
// 비밀번호만 통과한 단계라 Access 토큰이 없다. 표(stepToken)를 본문에 실어 공개 클라이언트로
// 부른다 — 관리자 클라이언트로 부르면 401 에 재발급을 시도하다 세션을 지운다.

/** 인증 앱의 6자리 또는 복구 코드 */
export function verifyOtp(stepToken: string, code: string) {
  return request<AdminLoginResponse>('/api/admin/auth/otp/verify', {
    method: 'POST',
    body: { stepToken, code },
  });
}

/** 첫 로그인의 인증 앱 등록 시작. 다시 부르면 키가 바뀐다 */
export function beginOtpSetupDuringLogin(stepToken: string) {
  return request<AdminOtpSetup>('/api/admin/auth/otp/setup', {
    method: 'POST',
    body: { stepToken },
  });
}

/** 첫 로그인의 인증 앱 등록 완료. 토큰과 복구 코드가 함께 온다 */
export function activateOtpDuringLogin(stepToken: string, code: string) {
  return request<AdminOtpActivateResult>('/api/admin/auth/otp/activate', {
    method: 'POST',
    body: { stepToken, code },
  });
}

// ---------- 로그인한 관리자 ----------

/** 서버에서 토큰 세대를 올려 발급된 토큰을 전부 무효화한다. */
export function logout() {
  return adminApi.post<void>('/api/admin/auth/logout');
}

export function me() {
  return adminApi.get<AdminUser>('/api/admin/auth/me');
}

export function changeMyPassword(currentPassword: string, newPassword: string) {
  return adminApi.patch<void>('/api/admin/auth/password', {
    body: { currentPassword, newPassword },
  });
}

export function myOtpStatus() {
  return adminApi.get<AdminOtpStatus>('/api/admin/auth/otp');
}

export function beginMyOtpSetup() {
  return adminApi.post<AdminOtpSetup>('/api/admin/auth/otp/setup');
}

export function activateMyOtp(code: string) {
  return adminApi.post<AdminOtpActivateResult>('/api/admin/auth/otp/activate', {
    body: { code },
  });
}

/** 지금 6자리를 확인한 뒤 새 복구 코드를 준다. 옛 코드는 전부 무효가 된다 */
export function regenerateRecoveryCodes(code: string) {
  return adminApi.post<{ recoveryCodes: string[] }>('/api/admin/auth/otp/recovery-codes', {
    body: { code },
  });
}
