'use client';

import { useMemo, useState } from 'react';

import { Alert } from '@/components/admin/Alert';
import { Button } from '@/components/admin/Button';
import { Field, SelectInput, TextInput } from '@/components/admin/Field';
import { Modal } from '@/components/admin/Modal';
import { Panel } from '@/components/admin/Panel';
import { StatusBadge } from '@/components/ui/StatusBadge';
import {
  flattenCategories,
  useAdminCategories,
  useCreateCategory,
  useDeleteCategory,
  useUpdateCategory,
} from '@/hooks/admin/useCategories';
import { useAdminSession } from '@/hooks/admin/useAdminSession';
import { formatDateTime } from '@/utils/format';
import type { Category } from '@/types/product';
import { ApiError } from '@/types/api';
import styles from '@/components/admin/admin.module.css';

/**
 * 카테고리 관리.
 *
 * 여기서 만든 트리가 쇼핑몰 상단 메뉴(GNB)와 드롭다운이 된다 — 이름을 바꾸면 메뉴가
 * 바로 바뀐다. 상위 카테고리로 검색하면 하위 카테고리 상품까지 함께 나온다.
 */
export function AdminCategoriesView() {
  const { can } = useAdminSession();
  const canEdit = can('MANAGER');

  const { data, isLoading, error } = useAdminCategories();
  const [editing, setEditing] = useState<Category | 'new' | null>(null);
  const [deleting, setDeleting] = useState<Category | null>(null);

  const rows = useMemo(() => flattenCategories(data ?? []), [data]);

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <div>
          <h1 className={styles.title}>카테고리</h1>
          <p className={styles.subtitle}>
            이 트리가 쇼핑몰 상단 메뉴가 된다. 상위 카테고리를 고르면 하위 상품까지 함께
            조회된다.
          </p>
        </div>
        {canEdit && (
          <div className={styles.headActions}>
            <Button variant="gold" onClick={() => setEditing('new')}>
              카테고리 등록
            </Button>
          </div>
        )}
      </header>

      {!canEdit && (
        <Alert tone="info" title="조회 권한으로 보고 있습니다">
          카테고리 등록·수정·삭제는 매니저(MANAGER) 이상만 할 수 있습니다.
        </Alert>
      )}

      {error && (
        <Alert tone="danger">
          {error instanceof ApiError ? error.message : '카테고리를 불러오지 못했습니다.'}
        </Alert>
      )}

      <Panel flush>
        {isLoading ? (
          <p className={styles.loading}>불러오는 중…</p>
        ) : rows.length === 0 ? (
          <p className={styles.empty}>등록된 카테고리가 없습니다.</p>
        ) : (
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th className={styles.num}>ID</th>
                  <th>이름</th>
                  <th className={styles.num}>정렬</th>
                  <th>노출</th>
                  <th>수정 시각</th>
                  {canEdit && <th aria-label="작업" />}
                </tr>
              </thead>
              <tbody>
                {rows.map(({ category, depth }) => (
                  <tr key={category.id}>
                    <td className={`${styles.num} ${styles.muted}`}>{category.id}</td>
                    <td>
                      <span style={{ paddingLeft: depth * 18 }}>
                        {depth > 0 && <span className={styles.muted}>└ </span>}
                        <span className={depth === 0 ? styles.strong : undefined}>
                          {category.name}
                        </span>
                      </span>
                    </td>
                    <td className={styles.num}>{category.sort}</td>
                    <td>
                      <StatusBadge tone={category.active ? 'success' : 'neutral'}>
                        {category.active ? '노출' : '숨김'}
                      </StatusBadge>
                    </td>
                    <td className={styles.muted}>{formatDateTime(category.updatedAt)}</td>
                    {canEdit && (
                      <td>
                        <div className={styles.rowActions}>
                          <Button small onClick={() => setEditing(category)}>
                            수정
                          </Button>
                          <Button small variant="danger" onClick={() => setDeleting(category)}>
                            삭제
                          </Button>
                        </div>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Panel>

      {editing && (
        <CategoryDialog
          key={editing === 'new' ? 'new' : editing.id}
          target={editing === 'new' ? null : editing}
          all={data ?? []}
          onClose={() => setEditing(null)}
        />
      )}

      {deleting && (
        <DeleteDialog key={deleting.id} target={deleting} onClose={() => setDeleting(null)} />
      )}
    </div>
  );
}

/** 자기 자신과 그 후손은 부모가 될 수 없다 — 순환 참조를 만들면 서버가 400 을 준다. */
function forbiddenParents(all: Category[], selfId: number): Set<number> {
  const forbidden = new Set<number>([selfId]);
  let grew = true;
  let guard = 0;
  while (grew && guard < 16) {
    grew = false;
    guard += 1;
    for (const c of all) {
      if (c.parentId != null && forbidden.has(c.parentId) && !forbidden.has(c.id)) {
        forbidden.add(c.id);
        grew = true;
      }
    }
  }
  return forbidden;
}

function CategoryDialog({
  target,
  all,
  onClose,
}: {
  target: Category | null;
  all: Category[];
  onClose: () => void;
}) {
  const create = useCreateCategory();
  const update = useUpdateCategory();
  const mutation = target ? update : create;

  const [name, setName] = useState(target?.name ?? '');
  const [parentId, setParentId] = useState<string>(
    target?.parentId == null ? '' : String(target.parentId),
  );
  const [sort, setSort] = useState(String(target?.sort ?? 0));
  const [active, setActive] = useState(target?.active ?? true);

  const forbidden = target ? forbiddenParents(all, target.id) : new Set<number>();
  const options = flattenCategories(all).filter(({ category }) => !forbidden.has(category.id));

  const sortInvalid = !/^-?\d{1,6}$/.test(sort);
  const canSubmit = name.trim().length > 0 && !sortInvalid && !mutation.isPending;

  const submit = () => {
    if (!canSubmit) return;
    const input = {
      parentId: parentId === '' ? null : Number(parentId),
      name: name.trim(),
      sort: Number(sort),
      active,
    };
    if (target) update.mutate({ id: target.id, input }, { onSuccess: onClose });
    else create.mutate(input, { onSuccess: onClose });
  };

  return (
    <Modal
      open
      title={target ? `카테고리 수정 · ${target.name}` : '카테고리 등록'}
      onClose={onClose}
      footer={
        <>
          <Button onClick={onClose} disabled={mutation.isPending}>
            취소
          </Button>
          <Button variant="primary" disabled={!canSubmit} onClick={submit}>
            {mutation.isPending ? '저장 중…' : '저장'}
          </Button>
        </>
      }
    >
      <div className={styles.form}>
        {mutation.error && (
          <Alert tone="danger">
            {mutation.error instanceof ApiError
              ? mutation.error.message
              : '카테고리를 저장하지 못했습니다.'}
          </Alert>
        )}

        <Field label="이름" htmlFor="cat-name" required hint="쇼핑몰 메뉴에 그대로 노출된다.">
          <TextInput
            id="cat-name"
            value={name}
            maxLength={100}
            autoFocus
            onChange={(e) => setName(e.target.value)}
          />
        </Field>

        <Field
          label="상위 카테고리"
          htmlFor="cat-parent"
          hint="비우면 최상위(메뉴 1단)가 된다. 자기 자신과 하위는 고를 수 없다."
        >
          <SelectInput
            id="cat-parent"
            value={parentId}
            onChange={(e) => setParentId(e.target.value)}
          >
            <option value="">— 최상위 —</option>
            {options.map(({ category, depth }) => (
              <option key={category.id} value={category.id}>
                {' '.repeat(depth * 2)}
                {depth > 0 ? '└ ' : ''}
                {category.name}
              </option>
            ))}
          </SelectInput>
        </Field>

        <div className={styles.formGrid}>
          <Field
            label="정렬 순서"
            htmlFor="cat-sort"
            required
            hint="작을수록 앞에 온다."
            error={sortInvalid ? '정수를 입력해 주세요.' : null}
          >
            <TextInput
              id="cat-sort"
              value={sort}
              inputMode="numeric"
              invalid={sortInvalid}
              onChange={(e) => setSort(e.target.value)}
            />
          </Field>

          <Field label="노출" hint="숨기면 쇼핑몰 메뉴와 목록에서 빠진다. 데이터는 남는다.">
            <label className={styles.checkRow}>
              <input
                type="checkbox"
                checked={active}
                onChange={(e) => setActive(e.target.checked)}
              />
              쇼핑몰에 노출
            </label>
          </Field>
        </div>
      </div>
    </Modal>
  );
}

function DeleteDialog({ target, onClose }: { target: Category; onClose: () => void }) {
  const remove = useDeleteCategory();
  const conflict = remove.error instanceof ApiError && remove.error.status === 409;

  return (
    <Modal
      open
      title={`카테고리 삭제 · ${target.name}`}
      onClose={onClose}
      footer={
        <>
          <Button onClick={onClose} disabled={remove.isPending}>
            취소
          </Button>
          <Button
            variant="danger"
            disabled={remove.isPending}
            onClick={() => remove.mutate(target.id, { onSuccess: onClose })}
          >
            {remove.isPending ? '삭제 중…' : '삭제'}
          </Button>
        </>
      }
    >
      <div className={styles.form}>
        {conflict ? (
          <Alert tone="warning" title="삭제할 수 없습니다">
            {(remove.error as ApiError).message}
            <br />이 경우 삭제 대신 <strong>수정 → 노출 해제</strong> 로 숨기면 된다. 기존
            상품의 소속은 그대로 유지된다.
          </Alert>
        ) : remove.error ? (
          <Alert tone="danger">
            {remove.error instanceof ApiError
              ? remove.error.message
              : '삭제하지 못했습니다.'}
          </Alert>
        ) : (
          <Alert tone="warning">
            <strong>{target.name}</strong> 카테고리를 삭제한다. 상품이나 하위 카테고리가
            있으면 삭제되지 않는다.
          </Alert>
        )}
      </div>
    </Modal>
  );
}
