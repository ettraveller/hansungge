import { adminApi } from '@/api/admin/client';
import type { AdminRole, AdminStatus, AdminUser } from '@/types/admin';
import type { PageResponse } from '@/types/api';

/** 전 구간 SUPER_ADMIN 전용이다. */
export function listAdmins(page = 0, size = 20) {
  return adminApi.get<PageResponse<AdminUser>>('/api/admin/admins', { query: { page, size } });
}

export function createAdmin(input: {
  loginId: string;
  password: string;
  name: string;
  role: AdminRole;
}) {
  return adminApi.post<AdminUser>('/api/admin/admins', { body: input });
}

/** 본인 권한은 바꿀 수 없고, 마지막 활성 SUPER_ADMIN 도 강등할 수 없다. */
export function updateAdmin(id: number, input: { name: string; role: AdminRole }) {
  return adminApi.put<AdminUser>(`/api/admin/admins/${id}`, { body: input });
}

/** 잠금 해제(ACTIVE) · 정지(DISABLED). 사유가 감사로그에 남는다. */
export function changeAdminStatus(id: number, status: AdminStatus, reason: string) {
  return adminApi.patch<AdminUser>(`/api/admin/admins/${id}/status`, { body: { status, reason } });
}

/** 잠긴 계정이면 잠금도 함께 풀린다. 대상의 기존 토큰은 전부 무효가 된다. */
export function resetAdminPassword(id: number, newPassword: string, reason: string) {
  return adminApi.patch<AdminUser>(`/api/admin/admins/${id}/password`, {
    body: { newPassword, reason },
  });
}
