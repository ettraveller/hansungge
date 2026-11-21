'use client';

import { useMemo, useRef, useState } from 'react';

import { Alert } from '../Alert';
import { Button } from '../Button';
import { TextInput } from '../Field';
import { Panel } from '../Panel';
import { EditableText } from './EditableText';
import { uploadMedia } from '@/api/admin/media';
import type { FooterInfo } from '@/api/footer';
import type { TopLink } from '@/api/topLinks';
import { useAdminSession } from '@/hooks/admin/useAdminSession';
import { useFooterInfo, useSaveFooter } from '@/hooks/admin/useFooter';
import { useReplaceTopLinks, useTopLinks } from '@/hooks/admin/useTopLinks';
import { TopBar } from '@/layouts/TopBar';
import { ApiError } from '@/types/api';
import { mediaUrl } from '@/utils/media';
import styles from '../admin.module.css';

/** 걸 수 있는 개수와 칸 길이. 서버(TopLinkReplaceRequest · TopLinkService)와 같은 값이다. */
const MAX_LINKS = 12;
const MAX_LABEL = 40;
const MAX_HREF = 500;
const MAX_TEL = 40;

/**
 * 고치는 중인 한 줄.
 *
 * <p>{@code uid} 는 화면이 줄을 알아보는 이름이다. 자리 번호를 key 로 쓰면 줄을
 * 위아래로 옮길 때 입력 중이던 칸의 커서가 엉뚱한 줄에 남는다.
 */
interface Row {
  uid: string;
  label: string;
  href: string;
  iconKey: string;
}

let nextUid = 0;
const newUid = () => `row-${nextUid++}`;

const toRow = (link: TopLink): Row => ({
  uid: newUid(),
  label: link.label,
  href: link.href,
  iconKey: link.iconKey ?? '',
});

/**
 * 주소를 받아 줄 수 있는 모양인가. 서버(TopLinkService)가 같은 규칙으로 다시 본다.
 *
 * <p>`https://…` 이거나, `/shop` 처럼 슬래시 하나로 시작하는 사이트 안 경로다.
 */
function isValidHref(href: string): boolean {
  return /^https?:\/\/[^\s/]+\S*$/i.test(href) || /^\/(?![/\\])\S*$/.test(href);
}

/**
 * 주소 앞머리를 채운다.
 *
 * <p>주소창에서 복사하면 `blog.naver.com/…` 처럼 앞이 빠져 오는 일이 잦다. 그대로
 * 두면 저장할 때 "https:// 로 시작해야 합니다" 로 돌려보내게 되는데, 무엇을 붙여야
 * 하는지는 우리가 이미 안다.
 */
function completeHref(raw: string): string {
  const href = raw.trim();
  if (href === '' || href.startsWith('/') || /^[a-z][a-z0-9+.-]*:/i.test(href)) {
    return href;
  }
  return /^[^\s/]+\.[^\s/]+/.test(href) ? `https://${href}` : href;
}

/** 저장을 막을 이유. 없으면 null. 첫 번째 것만 알린다 — 한 번에 하나씩 고치는 편이 빠르다. */
function problemOf(rows: Row[]): string | null {
  for (let i = 0; i < rows.length; i += 1) {
    const where = `${i + 1}번째 링크`;
    const label = rows[i].label.trim();
    const href = rows[i].href.trim();
    if (label === '') {
      return `${where}에 이름을 적어 주세요. 아이콘만 쓰는 링크에도 이름이 필요합니다 — 화면을 읽어 주는 도구가 그 이름을 읽습니다.`;
    }
    if (href === '') return `${where}(${label})에 주소를 적어 주세요.`;
    if (!isValidHref(href)) {
      return `${where}(${label})의 주소는 https:// 로 시작해야 합니다.`;
    }
  }
  return null;
}

/**
 * 상단 유틸 띠 편집 — 대표전화와 바로가기 링크.
 *
 * <h2>진짜 띠를 띄워 놓고 고친다</h2>
 * 맨 위에 손님 화면과 <b>같은 컴포넌트</b>({@link TopBar})를 그린다. 아래 목록에서
 * 아이콘을 올리거나 이름을 고치면 그 띠가 바로 따라 바뀐다 — 아이콘이 어두운 띠
 * 위에서 보이는지, 링크가 몇 개쯤부터 빽빽해지는지는 띠 안에서만 보인다.
 *
 * <h2>대표전화는 띠 위에서 바로 고친다</h2>
 * 그 번호는 푸터와 한 값이다({@code FooterInfo.tel}). 그래서 고치는 중인 값도 푸터
 * 패널과 함께 쓰고({@code footerDraft}), 여기 「저장」은 링크와 함께 그 값도 저장한다
 * — 띠에서 번호를 고친 사람이 저 아래 푸터 패널까지 내려가 저장을 한 번 더 눌러야
 * 한다면, 누르지 않고 떠난다.
 *
 * <h2>통째로 갈아 끼운다</h2>
 * 저장하면 지금 목록이 곧 전체다. 메인 배너와 같은 방식이다.
 */
export function TopBarPanel({
  footerDraft,
  onFooterDraftChange,
  onLinksDraftChange,
}: {
  /** 고치는 중인 푸터 값. 푸터 패널과 함께 쓴다 — 대표전화가 두 패널에 다 보인다 */
  footerDraft: FooterInfo | null;
  onFooterDraftChange: (draft: FooterInfo | null) => void;
  /** 고치는 중인 링크를 밖에 알린다(옆 미리보기용). 저장하면 null */
  onLinksDraftChange?: (links: TopLink[] | null) => void;
}) {
  const { can } = useAdminSession();
  const canEdit = can('MANAGER');

  const links = useTopLinks();
  const footer = useFooterInfo();
  const replace = useReplaceTopLinks();
  const saveFooter = useSaveFooter();

  /** 손대기 전에는 null 이고, 그때는 서버가 준 것을 그대로 쓴다. */
  const [draft, setDraft] = useState<Row[] | null>(null);
  const [saved, setSaved] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);

  // 서버가 준 목록에 화면용 이름(uid)을 붙여 둔다. 그릴 때마다 새로 만들면 key 가
  // 매번 바뀌어 입력칸이 통째로 다시 만들어진다 — 받은 목록이 바뀔 때만 새로 붙인다.
  const loadedRows = useMemo(() => (links.data ?? []).map(toRow), [links.data]);

  const rows: Row[] = draft ?? loadedRows;
  const info = footerDraft ?? footer.data ?? null;
  const changed = draft != null || footerDraft != null;
  const pending = replace.isPending || saveFooter.isPending;

  const update = (next: Row[]) => {
    setDraft(next);
    onLinksDraftChange?.(
      next.map((row) => ({
        label: row.label.trim() || '(이름 없음)',
        href: row.href.trim(),
        iconKey: row.iconKey || null,
      })),
    );
    setSaved(false);
    setProblem(null);
  };

  const edit = (uid: string, patch: Partial<Row>) =>
    update(rows.map((row) => (row.uid === uid ? { ...row, ...patch } : row)));

  const move = (index: number, by: -1 | 1) => {
    const target = index + by;
    if (target < 0 || target >= rows.length) return;
    const next = [...rows];
    [next[index], next[target]] = [next[target], next[index]];
    update(next);
  };

  const submit = async () => {
    setSaved(false);
    const found = problemOf(rows);
    setProblem(found);
    if (found) return;

    try {
      if (draft != null) {
        await replace.mutateAsync(
          rows.map((row) => ({
            label: row.label.trim(),
            href: row.href.trim(),
            iconKey: row.iconKey || null,
          })),
        );
        setDraft(null);
        onLinksDraftChange?.(null);
      }
      // 대표전화를 고쳤으면 같이 저장한다. 푸터 패널에서 고치던 다른 칸도 이 값에
      // 함께 들어 있다 — 하나의 푸터를 두 번에 나눠 저장할 수는 없다.
      if (footerDraft != null) {
        await saveFooter.mutateAsync(footerDraft);
        onFooterDraftChange(null);
      }
      setSaved(true);
    } catch {
      // 사유는 아래 replace.error · saveFooter.error 가 그린다.
    }
  };

  const failure = replace.error ?? saveFooter.error;
  const loadError = links.error ?? footer.error;

  return (
    <Panel
      title="상단 띠"
      note="모든 화면 맨 위, 헤더 위에 걸리는 가는 띠다. 대표전화와 바로가기 링크가 들어간다. 색은 「색」 탭에서 고른다."
      actions={
        canEdit && (
          <Button variant="gold" onClick={submit} disabled={!changed || pending}>
            {pending ? '저장 중…' : '저장'}
          </Button>
        )
      }
    >
      {loadError && (
        <Alert tone="danger" title="불러오지 못했습니다">
          {loadError instanceof ApiError ? loadError.message : '잠시 후 다시 시도해 주세요.'}
        </Alert>
      )}
      {problem && (
        <Alert tone="danger" title="저장하기 전에 고쳐 주세요">
          {problem}
        </Alert>
      )}
      {failure && (
        <Alert tone="danger" title="저장하지 못했습니다">
          {failure instanceof ApiError ? failure.message : '잠시 후 다시 시도해 주세요.'}
        </Alert>
      )}
      {saved && !changed && (
        <Alert tone="success" title="저장했습니다">
          손님 화면 맨 위 띠에 바로 반영됩니다.
        </Alert>
      )}

      {links.isLoading || !info ? (
        <p className={styles.hint}>불러오는 중…</p>
      ) : (
        <>
          <div className={styles.footerCanvas}>
            <TopBar
              info={info}
              util={
                <>
                  <span>로그인</span>
                  <span>회원가입</span>
                  <span>장바구니</span>
                </>
              }
              links={rows.map((row) => ({
                label: row.label || '(이름 없음)',
                href: row.href,
                iconKey: row.iconKey || null,
              }))}
              renderText={
                canEdit
                  ? (name, value) => (
                      <EditableText
                        value={value}
                        label="대표전화"
                        maxLength={MAX_TEL}
                        onChange={(next) => {
                          onFooterDraftChange({ ...info, [name]: next });
                          setSaved(false);
                        }}
                      />
                    )
                  : undefined
              }
            />
          </div>
          <p className={styles.hint}>
            위가 손님이 보는 띠 그대로다. <strong>대표전화는 띠 위의 번호를 눌러서</strong>{' '}
            고친다 — 푸터의 대표전화와 같은 값이라 함께 바뀐다. 링크는 아이콘이 있는
            것이 앞에, 글자만 있는 것이 뒤에 모여서 나간다.
          </p>

          {rows.length === 0 ? (
            <p className={styles.hint}>
              아직 건 링크가 없다. 블로그·유튜브·입점한 쇼핑몰 주소를 걸어 둘 수 있다.
            </p>
          ) : (
            <ul className={styles.topLinkRows}>
              {rows.map((row, index) => (
                <TopLinkRow
                  key={row.uid}
                  row={row}
                  index={index}
                  count={rows.length}
                  disabled={!canEdit}
                  onEdit={(patch) => edit(row.uid, patch)}
                  onMove={(by) => move(index, by)}
                  onRemove={() => update(rows.filter((r) => r.uid !== row.uid))}
                />
              ))}
            </ul>
          )}

          {canEdit && (
            <div className={styles.topLinkAdd}>
              <Button
                onClick={() =>
                  update([...rows, { uid: newUid(), label: '', href: '', iconKey: '' }])
                }
                disabled={rows.length >= MAX_LINKS}
              >
                + 링크 추가
              </Button>
              <span className={styles.hint}>
                {rows.length >= MAX_LINKS
                  ? `띠 한 줄에 ${MAX_LINKS}개까지 걸 수 있다.`
                  : '아이콘은 정사각형 PNG(배경 투명), 64×64px 이상을 권장한다. 띠가 어두운 색이라 밝은 아이콘이 잘 보인다.'}
              </span>
            </div>
          )}
        </>
      )}
    </Panel>
  );
}

/**
 * 링크 한 줄 — 아이콘 · 이름 · 주소 · 차례 · 삭제.
 *
 * <p>아이콘은 올리는 즉시 저장소에 들어가지만 <b>띠에 걸리는 것은 「저장」을 누른
 * 뒤</b>다. 잘못 올린 그림이 곧바로 모든 화면 맨 위에 걸리지 않는다.
 */
function TopLinkRow({
  row,
  index,
  count,
  disabled,
  onEdit,
  onMove,
  onRemove,
}: {
  row: Row;
  index: number;
  count: number;
  disabled: boolean;
  onEdit: (patch: Partial<Row>) => void;
  onMove: (by: -1 | 1) => void;
  onRemove: () => void;
}) {
  const fileRef = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const icon = mediaUrl(row.iconKey);
  const name = row.label.trim() || `${index + 1}번째 링크`;

  const upload = async (file: File | undefined) => {
    if (!file) return;
    setBusy(true);
    setError(null);
    try {
      const [uploaded] = await uploadMedia([file], 'icons');
      onEdit({ iconKey: uploaded.objectKey });
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '아이콘을 올리지 못했습니다.');
    } finally {
      setBusy(false);
      // 같은 파일을 다시 골라도 change 가 일어나게 비운다.
      if (fileRef.current) fileRef.current.value = '';
    }
  };

  return (
    <li className={styles.topLinkRow}>
      <span className={styles.bannerNo}>{index + 1}</span>

      <div className={styles.topLinkIconCol}>
        {/* 띠와 같은 어두운 바탕에 올려 보여준다. 흰 바탕에 두면 흰 아이콘이 안 보여
            "올라가지 않았다" 로 읽힌다. */}
        <div className={styles.topLinkIconBox}>
          {icon ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={icon} alt="" />
          ) : (
            <span>글자</span>
          )}
        </div>
        {!disabled && (
          <div className={styles.topLinkIconActions}>
            <input
              ref={fileRef}
              type="file"
              accept="image/png,image/jpeg,image/webp,image/gif"
              hidden
              onChange={(e) => void upload(e.target.files?.[0])}
            />
            <Button small onClick={() => fileRef.current?.click()} disabled={busy}>
              {busy ? '올리는 중…' : icon ? '아이콘 교체' : '아이콘 등록'}
            </Button>
            {icon && (
              <Button small onClick={() => onEdit({ iconKey: '' })} disabled={busy}>
                아이콘 빼기
              </Button>
            )}
          </div>
        )}
      </div>

      <div className={styles.topLinkFields}>
        <TextInput
          aria-label={`${name}의 이름`}
          value={row.label}
          maxLength={MAX_LABEL}
          disabled={disabled}
          placeholder="이름 (예: 네이버 블로그, 스마트스토어)"
          onChange={(e) => onEdit({ label: e.target.value })}
        />
        <TextInput
          aria-label={`${name}의 주소`}
          value={row.href}
          maxLength={MAX_HREF}
          disabled={disabled}
          inputMode="url"
          placeholder="주소 (예: https://blog.naver.com/…)"
          onChange={(e) => onEdit({ href: e.target.value })}
          onBlur={(e) => {
            const completed = completeHref(e.target.value);
            if (completed !== e.target.value) onEdit({ href: completed });
          }}
        />
        {error && <p className={styles.hintWarn}>{error}</p>}
        <p className={styles.hint}>
          {icon
            ? '아이콘 버튼으로 나간다. 이름은 화면에 보이지 않고, 마우스를 올렸을 때와 화면을 읽어 주는 도구에서 쓰인다.'
            : '아이콘이 없으면 이름이 글자 링크로 나간다.'}
        </p>
      </div>

      {!disabled && (
        <div className={styles.topLinkOrder}>
          <Button
            small
            aria-label={`${name}을 앞으로`}
            onClick={() => onMove(-1)}
            disabled={index === 0}
          >
            ↑
          </Button>
          <Button
            small
            aria-label={`${name}을 뒤로`}
            onClick={() => onMove(1)}
            disabled={index === count - 1}
          >
            ↓
          </Button>
          <Button small variant="danger" aria-label={`${name} 삭제`} onClick={onRemove}>
            삭제
          </Button>
        </div>
      )}
    </li>
  );
}
