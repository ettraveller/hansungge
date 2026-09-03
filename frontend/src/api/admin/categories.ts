import { adminApi } from '@/api/admin/client';
import type { Category } from '@/types/product';

/** 관리자 목록에는 비활성 카테고리도 포함된다. */
export function listCategories() {
  return adminApi.get<Category[]>('/api/admin/categories');
}

export interface CategoryInput {
  parentId?: number | null;
  name: string;
  sort: number;
  active?: boolean;
}

export function createCategory(input: CategoryInput) {
  return adminApi.post<Category>('/api/admin/categories', { body: input });
}

export function updateCategory(id: number, input: CategoryInput) {
  return adminApi.put<Category>(`/api/admin/categories/${id}`, { body: input });
}

/** 상품이나 하위 카테고리가 있으면 409 다 — 그 경우 삭제 대신 active=false 로 숨긴다. */
export function deleteCategory(id: number) {
  return adminApi.delete<void>(`/api/admin/categories/${id}`);
}
