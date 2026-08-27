import { adminApi } from '@/api/admin/client';
import type { ActorType, AuditLog } from '@/types/admin';
import type { PageResponse } from '@/types/api';

export interface AuditSearch {
  actor?: string | null;
  actorType?: ActorType | null;
  action?: string | null;
  targetType?: string | null;
  targetId?: string | null;
  /** ISO-8601. 이상 */
  from?: string | null;
  /** ISO-8601. 미만 */
  to?: string | null;
  page?: number;
  size?: number;
}

/** 정렬은 서버가 최신순으로 고정한다. */
export function searchAuditLogs(search: AuditSearch = {}) {
  return adminApi.get<PageResponse<AuditLog>>('/api/admin/audit-logs', {
    query: {
      actor: search.actor,
      actorType: search.actorType,
      action: search.action,
      targetType: search.targetType,
      targetId: search.targetId,
      from: search.from,
      to: search.to,
      page: search.page ?? 0,
      size: search.size ?? 20,
    },
  });
}
