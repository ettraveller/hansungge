'use client';

import { useState } from 'react';

import { Alert } from '../Alert';
import { Button } from '../Button';
import { ImageUploader } from '../ImageUploader';
import { Panel } from '../Panel';
import { useProductImages, useReplaceImages } from '@/hooks/admin/useProducts';
import { ApiError } from '@/types/api';
import styles from '../admin.module.css';

/**
 * 상품 상세 이미지.
 *
 * <p>대표이미지와 다른 칸이다 — 대표는 상품 기본정보에 있고(한 장), 여기는 상세
 * 페이지에서 위에서 아래로 이어 보여주는 사진들이다. 순서가 곧 보이는 순서다.
 *
 * <p>저장은 <b>목록 전체 교체</b>다({@code PUT /images}). 추가·삭제 API 를 따로 두면
 * 순서 바꾸기가 여러 요청으로 쪼개지고, 중간에 하나가 실패했을 때 어떤 순서로
 * 남아 있는지 알 수 없게 된다.
 */
export function ProductImagesPanel({
  productId,
  canEdit,
}: {
  productId: number;
  canEdit: boolean;
}) {
  const { data, isLoading, error } = useProductImages(productId);
  const replace = useReplaceImages();

  // 편집 중인 내용만 상태로 들고, 손대지 않았을 때는 서버 값을 그대로 보여준다.
  // 서버 값을 effect 로 상태에 복사하면 응답이 늦게 도착했을 때 편집 중인 목록을 덮어쓴다.
  const [draft, setDraft] = useState<string[] | null>(null);
  const touched = draft != null;
  const keys = draft ?? data ?? [];

  return (
    <Panel
      title="상세 이미지"
      note="상세 페이지에 위에서 아래로 보이는 순서다. 올린 뒤 저장을 눌러야 반영된다."
    >
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
            max={10}
            disabled={!canEdit}
            emptyLabel="상세 사진을 끌어다 놓거나 클릭해서 선택하세요"
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
                replace.mutate({ id: productId, keys }, { onSuccess: () => setDraft(null) })
              }
            >
              {replace.isPending ? '저장 중…' : `상세 이미지 ${keys.length}장 저장`}
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
