'use client';

import { useState } from 'react';

import { Alert } from '../Alert';
import { Button } from '../Button';
import { ImageUploader } from '../ImageUploader';
import { Panel } from '../Panel';
import { useProductImages, useReplaceImages } from '@/hooks/admin/useProducts';
import type { ProductImageKind } from '@/api/admin/products';
import { ApiError } from '@/types/api';
import styles from '../admin.module.css';

/** 갈래별 문구와 미리보기 모양. 두 패널이 같은 컴포넌트를 쓰되 설명만 달라진다. */
const SPEC: Record<
  ProductImageKind,
  { title: string; note: string; empty: string; preview: 'square' | 'tall'; max: number }
> = {
  GALLERY: {
    title: '대표 이미지',
    note:
      '상세 페이지 위쪽에서 눌러 넘겨 보는 사진이다. ' +
      '첫 번째 장이 목록 · 장바구니 · 주문서에 쓰이는 대표 사진이 된다 — 순서를 바꾸면 그것도 바뀐다.',
    empty: '상품 사진을 끌어다 놓거나 클릭해서 선택하세요',
    preview: 'square',
    max: 10,
  },
  DETAIL: {
    title: '상세 이미지',
    note:
      '상품 설명 아래에 올린 순서대로 전체 폭으로 쌓인다. ' +
      '860px 폭으로 만든 세로로 긴 상세페이지를 그대로 올리면 된다.',
    empty: '상세페이지 이미지를 끌어다 놓거나 클릭해서 선택하세요',
    preview: 'tall',
    max: 10,
  },
};

/**
 * 상품 이미지 관리 — 갈래 하나당 패널 하나.
 *
 * <p>두 갈래를 한 칸에 섞지 않는다. 대표이미지는 정사각형으로 잘려 쓰이고 상세
 * 이미지는 860×9000 처럼 세로로 길다. 한 목록에 두면 "앞쪽 몇 장이 대표"라는
 * 암묵적 규칙에 기대야 하고, 순서를 바꾸는 순간 상세페이지가 썸네일 자리로 올라간다.
 *
 * <p>저장은 <b>그 갈래 목록 전체 교체</b>다({@code PUT /images?kind=…}). 추가·삭제를
 * 따로 두면 순서 바꾸기가 여러 요청으로 쪼개지고, 중간에 하나가 실패했을 때 어떤
 * 순서로 남아 있는지 알 수 없게 된다. 다른 갈래는 서버가 손대지 않는다.
 */
export function ProductImagesPanel({
  productId,
  kind,
  canEdit,
}: {
  productId: number;
  kind: ProductImageKind;
  canEdit: boolean;
}) {
  const spec = SPEC[kind];
  const { data, isLoading, error } = useProductImages(productId, kind);
  const replace = useReplaceImages();

  // 편집 중인 내용만 상태로 들고, 손대지 않았을 때는 서버 값을 그대로 보여준다.
  // 서버 값을 effect 로 상태에 복사하면 응답이 늦게 도착했을 때 편집 중인 목록을 덮어쓴다.
  const [draft, setDraft] = useState<string[] | null>(null);
  const touched = draft != null;
  const keys = draft ?? data ?? [];

  return (
    <Panel title={spec.title} note={`${spec.note} 저장을 눌러야 반영된다.`}>
      <div className={styles.form}>
        {error && (
          <Alert tone="danger">
            {error instanceof ApiError ? error.message : '이미지를 불러오지 못했습니다.'}
          </Alert>
        )}
        {replace.error && (
          <Alert tone="danger">
            {replace.error instanceof ApiError
              ? replace.error.message
              : '이미지를 저장하지 못했습니다.'}
          </Alert>
        )}

        {isLoading ? (
          <p className={styles.loading}>불러오는 중…</p>
        ) : (
          <ImageUploader
            value={keys}
            onChange={setDraft}
            max={spec.max}
            disabled={!canEdit}
            emptyLabel={spec.empty}
            preview={spec.preview}
          />
        )}

        {canEdit && (
          <div className={styles.formActions}>
            <Button
              variant="primary"
              // 손대지 않았을 때 눌러도 같은 목록을 다시 쓰는 것뿐이지만, 눌릴 수 있으면
              // "저장했나?" 를 확인하려고 누르게 된다. 바뀐 게 있을 때만 열어 둔다.
              disabled={replace.isPending || !touched}
              onClick={() =>
                // 저장에 성공하면 편집 상태를 버린다 — 이후에는 서버가 준 값이 그대로 보인다.
                replace.mutate({ id: productId, kind, keys }, { onSuccess: () => setDraft(null) })
              }
            >
              {replace.isPending ? '저장 중…' : `${spec.title} ${keys.length}장 저장`}
            </Button>
            <Button disabled={replace.isPending || !touched} onClick={() => setDraft(null)}>
              되돌리기
            </Button>
          </div>
        )}
      </div>
    </Panel>
  );
}
