'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import * as adminsApi from '@/api/admin/admins';
import { adminKeys } from './keys';
import type { AdminRole, AdminStatus } from '@/types/admin';

/** SUPER_ADMIN 이 아니면 403 이므로 호출 자체를 막을 수 있게 enabled 를 받는다. */
export function useAdminUsers(page: number, enabled = true) {
  return useQuery({
    queryKey: adminKeys.admins(page),
    queryFn: () => adminsApi.listAdmins(page),
    enabled,
  });
}

function useAdminUserMutation<TArgs, TResult>(fn: (args: TArgs) => Promise<TResult>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: [...adminKeys.all, 'admins'] });
      // 계정 변경은 감사로그를 남긴다.
      void queryClient.invalidateQueries({ queryKey: [...adminKeys.all, 'audit-logs'] });
    },
  });
}

export function useCreateAdmin() {
  return useAdminUserMutation(adminsApi.createAdmin);
}

export function useUpdateAdmin() {
  return useAdminUserMutation(
    ({ id, name, role }: { id: number; name: string; role: AdminRole }) =>
      adminsApi.updateAdmin(id, { name, role }),
  );
}

export function useChangeAdminStatus() {
  return useAdminUserMutation(
    ({ id, status, reason }: { id: number; status: AdminStatus; reason: string }) =>
      adminsApi.changeAdminStatus(id, status, reason),
  );
}

export function useResetAdminPassword() {
  return useAdminUserMutation(
    ({ id, newPassword, reason }: { id: number; newPassword: string; reason: string }) =>
      adminsApi.resetAdminPassword(id, newPassword, reason),
  );
}
