'use client';

import { useQuery } from '@tanstack/react-query';

import * as auditApi from '@/api/admin/audit';
import { adminKeys } from './keys';

/** 감사로그는 append-only 라 캐시가 오래돼도 틀린 값이 되지는 않는다. */
export function useAuditLogs(search: auditApi.AuditSearch) {
  return useQuery({
    queryKey: adminKeys.auditLogs(search),
    queryFn: () => auditApi.searchAuditLogs(search),
    staleTime: 15_000,
  });
}
