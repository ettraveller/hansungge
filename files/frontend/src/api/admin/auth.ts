import { request } from '@/api/client';
import { adminApi } from '@/api/admin/client';
import type { AdminLoginResponse, AdminUser } from '@/types/admin';

/** 로그인은 토큰이 없는 상태에서 부르므로 공개 클라이언트를 쓴다. */
export function login(loginId: string, password: string) {
  return request<AdminLoginResponse>('/api/admin/auth/login', {
    method: 'POST',
    body: { loginId, password },
  });
}

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
