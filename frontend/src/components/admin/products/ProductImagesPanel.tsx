'use client';

import { useState } from 'react';

import { Alert } from '../Alert';
import { Button } from '../Button';
import { Field, TextArea } from '../Field';
import { Panel } from '../Panel';
import { useProductImages, useReplaceImages } from '@/hooks/admin/useProducts';
import { ApiError } from '@/types/api';
import styles from '../admin.module.css';

/**
 * 상세 이미지 키.
 *
 * ⚠️ 파일 업로드는 아직 없다 (STEP 10, NCP Object Storage). 지금은 이미 업로드된
 * <b>객체 키</b>만 연결한다. URL 을 넣으면 그대로 저장되지만 화면에서는 깨진다.
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
  // 서버 값을 effect 로 상태에 복사하면 응답이 늦게 도착했을 때 입력 중인 내용을 덮어쓴다.
  const [draft, setDraft] = useState<string | null>(null);
  const touched = draft != null;
  const text = draft ?? (data ?? []).join('\n');

  const keys = text
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line.length > 0);

  return (
    <Panel
      title="상세 이미지"
      note="줄 하나에 키 하나. 보낸 순서대로 정렬된다. 업로드 기능은 STEP 10 에서 붙는다."
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
          <Field
            label="Object Storage 키 목록"
            htmlFor="img-keys"
            hint={`현재 ${keys.length}개. 저장하면 목록 전체가 이 내용으로 교체된다.`}
          >
            <TextArea
              id="img-keys"
              value={text}
              disabled={!canEdit}
              placeholder={'products/2026/goldbar-375-1.jpg\nproducts/2026/goldbar-375-2.jpg'}
              onChange={(e) => setDraft(e.target.value)}
            />
          </Field>
        )}

        {canEdit && (
          <div className={styles.formActions}>
            <Button
              variant="primary"
              disabled={replace.isPending}
              onClick={() =>
                // 저장에 성공하면 편집 상태를 버린다 — 이후에는 서버가 준 값이 그대로 보인다.
                replace.mutate({ id: productId, keys }, { onSuccess: () => setDraft(null) })
              }
            >
              {replace.isPending ? '저장 중…' : '이미지 목록 저장'}
            </Button>
            <Button
              disabled={replace.isPending || !touched}
              onClick={() => setDraft(null)}
            >
              되돌리기
            </Button>
          </div>
        )}
      </div>
    </Panel>
  );
}
