'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import * as categoriesApi from '@/api/admin/categories';
import { adminKeys } from './keys';
import type { Category } from '@/types/product';

export function useAdminCategories() {
  return useQuery({
    queryKey: adminKeys.categories(),
    queryFn: categoriesApi.listCategories,
    staleTime: 60_000,
  });
}

/** "골드바 > 기본" 처럼 경로로 보여주기 위한 이름 맵. */
export function categoryPathMap(categories: Category[]): Map<number, string> {
  const byId = new Map(categories.map((c) => [c.id, c]));
  const cache = new Map<number, string>();

  function pathOf(id: number, depth = 0): string {
    const cached = cache.get(id);
    if (cached) return cached;
    const node = byId.get(id);
    if (!node) return String(id);
    // 데이터가 잘못돼 순환이 생겨도 화면이 멈추지 않게 깊이를 제한한다.
    const path =
      node.parentId == null || depth >= 8
        ? node.name
        : `${pathOf(node.parentId, depth + 1)} > ${node.name}`;
    cache.set(id, path);
    return path;
  }

  for (const c of categories) cache.set(c.id, pathOf(c.id));
  return cache;
}

/** 부모 → 자식 순서로 평탄화한다. 셀렉트 박스에 계층을 보여줄 때 쓴다. */
export function flattenCategories(categories: Category[]): { category: Category; depth: number }[] {
  const children = new Map<number | null, Category[]>();
  for (const c of categories) {
    const key = c.parentId ?? null;
    const list = children.get(key) ?? [];
    list.push(c);
    children.set(key, list);
  }
  for (const list of children.values()) list.sort((a, b) => a.sort - b.sort || a.id - b.id);

  const out: { category: Category; depth: number }[] = [];
  const visit = (parentId: number | null, depth: number) => {
    if (depth > 8) return;
    for (const c of children.get(parentId) ?? []) {
      out.push({ category: c, depth });
      visit(c.id, depth + 1);
    }
  };
  visit(null, 0);
  return out;
}

function useCategoryMutation<TArgs, TResult>(fn: (args: TArgs) => Promise<TResult>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.categories() });
      // 상품 목록의 카테고리 필터도 함께 낡는다.
      void queryClient.invalidateQueries({ queryKey: adminKeys.products() });
    },
  });
}

export function useCreateCategory() {
  return useCategoryMutation(categoriesApi.createCategory);
}

export function useUpdateCategory() {
  return useCategoryMutation(({ id, input }: { id: number; input: categoriesApi.CategoryInput }) =>
    categoriesApi.updateCategory(id, input),
  );
}

export function useDeleteCategory() {
  return useCategoryMutation((id: number) => categoriesApi.deleteCategory(id));
}
