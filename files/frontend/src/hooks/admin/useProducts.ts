'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import * as productsApi from '@/api/admin/products';
import { adminKeys } from './keys';
import type { ProductStatus } from '@/types/product';

export function useAdminProducts(search: productsApi.ProductSearch) {
  return useQuery({
    queryKey: adminKeys.productList(search),
    queryFn: () => productsApi.listProducts(search),
    // 목록에는 가격이 없으므로(=상태·재고만) 조금 길게 잡아도 오조작 위험이 없다.
    staleTime: 15_000,
  });
}

export function useAdminProduct(id: number | null) {
  return useQuery({
    queryKey: adminKeys.product(id ?? 0),
    queryFn: () => productsApi.getProduct(id as number),
    enabled: id != null,
  });
}

export function useProductOptions(id: number | null) {
  return useQuery({
    queryKey: adminKeys.productOptions(id ?? 0),
    queryFn: () => productsApi.listOptions(id as number),
    enabled: id != null,
  });
}

/**
 * 옵션별 가격 미리보기.
 *
 * 저장 직후 값이 낡으면 관리자가 "공임을 고쳤는데 가격이 그대로"라고 오해한다.
 * 상품/옵션 변경 뮤테이션이 이 키를 무효화한다.
 */
export function useProductPricePreview(id: number | null) {
  return useQuery({
    queryKey: adminKeys.productPreview(id ?? 0),
    queryFn: () => productsApi.previewAllOptions(id as number),
    enabled: id != null,
    staleTime: 0,
  });
}

export function useProductImages(id: number | null) {
  return useQuery({
    queryKey: adminKeys.productImages(id ?? 0),
    queryFn: () => productsApi.listImages(id as number),
    enabled: id != null,
  });
}

/** 상품 관련 캐시를 한 번에 무효화한다. 가격은 옵션·공임 어느 쪽이 바뀌어도 낡는다. */
function useProductMutation<TArgs, TResult>(fn: (args: TArgs) => Promise<TResult>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.products() });
    },
  });
}

export function useCreateProduct() {
  return useProductMutation(productsApi.createProduct);
}

export function useUpdateProduct() {
  return useProductMutation(({ id, input }: { id: number; input: productsApi.ProductInput }) =>
    productsApi.updateProduct(id, input),
  );
}

export function useChangeProductStatus() {
  return useProductMutation(
    ({
      id,
      status,
      active,
      reason,
    }: {
      id: number;
      status: ProductStatus;
      active: boolean;
      reason: string;
    }) => productsApi.changeStatus(id, status, active, reason),
  );
}

export function useAddOption() {
  return useProductMutation(
    ({ productId, input }: { productId: number; input: productsApi.OptionInput }) =>
      productsApi.addOption(productId, input),
  );
}

export function useUpdateOption() {
  return useProductMutation(
    ({
      productId,
      optionId,
      input,
    }: {
      productId: number;
      optionId: number;
      input: productsApi.OptionInput;
    }) => productsApi.updateOption(productId, optionId, input),
  );
}

export function useAdjustStock() {
  return useProductMutation(
    ({
      productId,
      optionId,
      stock,
      reason,
    }: {
      productId: number;
      optionId: number;
      stock: number;
      reason: string;
    }) => productsApi.adjustStock(productId, optionId, stock, reason),
  );
}

export function useReplaceImages() {
  return useProductMutation(({ id, keys }: { id: number; keys: string[] }) =>
    productsApi.replaceImages(id, keys),
  );
}
