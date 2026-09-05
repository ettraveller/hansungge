import type { AuditSearch } from '@/api/admin/audit';
import type { AdminEventSearch } from '@/api/admin/events';
import type { AdminNoticeSearch } from '@/api/admin/notices';
import type { ProductSearch } from '@/api/admin/products';
import type { AdminQnaSearch } from '@/api/admin/qnas';
import type { PendingStatus } from '@/types/admin';
import type { MetalType, PriceSide } from '@/types/price';

/**
 * 관리자 쿼리 키.
 *
 * 문자열을 화면마다 손으로 쓰면 무효화가 조용히 빗나간다 — 시세를 고시했는데
 * 대시보드 숫자가 그대로인 식이다. 한 곳에서 만든다.
 */
export const adminKeys = {
  all: ['admin'] as const,

  session: () => [...adminKeys.all, 'session'] as const,

  prices: () => [...adminKeys.all, 'prices'] as const,
  currentPrices: () => [...adminKeys.prices(), 'current'] as const,
  collectionStatus: () => [...adminKeys.prices(), 'collection-status'] as const,
  policies: () => [...adminKeys.prices(), 'policies'] as const,
  priceHistory: (metal: MetalType, side: PriceSide, page: number) =>
    [...adminKeys.prices(), 'history', metal, side, page] as const,
  pending: (status: PendingStatus, page: number) =>
    [...adminKeys.prices(), 'pending', status, page] as const,

  products: () => [...adminKeys.all, 'products'] as const,
  productList: (search: ProductSearch) => [...adminKeys.products(), 'list', search] as const,
  product: (id: number) => [...adminKeys.products(), id] as const,
  productOptions: (id: number) => [...adminKeys.products(), id, 'options'] as const,
  productImages: (id: number) => [...adminKeys.products(), id, 'images'] as const,
  productPreview: (id: number) => [...adminKeys.products(), id, 'preview'] as const,

  categories: () => [...adminKeys.all, 'categories'] as const,

  qnas: () => [...adminKeys.all, 'qnas'] as const,
  qnaList: (search: AdminQnaSearch) => [...adminKeys.qnas(), 'list', search] as const,
  qnaUnansweredCount: () => [...adminKeys.qnas(), 'unanswered-count'] as const,

  notices: () => [...adminKeys.all, 'notices'] as const,
  noticeList: (search: AdminNoticeSearch) => [...adminKeys.notices(), 'list', search] as const,

  events: () => [...adminKeys.all, 'events'] as const,
  eventList: (search: AdminEventSearch) => [...adminKeys.events(), 'list', search] as const,
  eventEntries: (id: number, page: number) =>
    [...adminKeys.events(), id, 'entries', page] as const,

  auditLogs: (search: AuditSearch) => [...adminKeys.all, 'audit-logs', search] as const,

  admins: (page: number) => [...adminKeys.all, 'admins', page] as const,
};
