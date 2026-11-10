'use client';

import { useState } from 'react';

import { Alert } from '@/components/admin/Alert';
import { Button } from '@/components/admin/Button';
import { Field, TextInput } from '@/components/admin/Field';
import { ImageUploader } from '@/components/admin/ImageUploader';
import { BannerPreviewModal } from '@/components/admin/banners/BannerPreviewModal';
import { Panel } from '@/components/admin/Panel';
import { useAdminBanners, useReplaceBanners } from '@/hooks/admin/useBanners';
import { useAdminSession } from '@/hooks/admin/useAdminSession';
import { mediaUrl } from '@/utils/media';
import { ApiError } from '@/types/api';
import styles from '@/components/admin/admin.module.css';

/** 넣을 수 있는 장수. 서버도 같은 값으로 막는다. */
const MAX_BANNERS = 10;

/** 권장 크기 (데스크톱). 화면의 배너 높이(560px)를 2배로 잡은 값이다. */
const RECOMMENDED = '2560 × 1120 px';

/**
 * 권장 크기 (휴대폰).
 *
 * 휴대폰에서 배너 자리는 390×460 — <b>세로가 더 길다.</b> 데스크톱(2.3:1)과 비율이
 * 아예 뒤집혀 있어서, 가로용 한 장을 그대로 쓰면 가로가 3분의 2쯤 잘려 나간다.
 */
const RECOMMENDED_MOBILE = '1080 × 1280 px';

interface Row {
  imageKey: string;
  mobileImageKey: string;
  linkHref: string;
  altText: string;
}

/**
 * 메인 배너 관리.
 *
 * <h2>이미지 한 장이 배너 하나다</h2>
 * 문구도 버튼도 이미지 안에 들어간다. 글자를 밖에 두면 사진의 어느 자리에 글이
 * 올라갈지를 화면 코드가 정하게 되고, 사진이 바뀔 때마다 그 자리가 어긋난다.
 *
 * <h2>휴대폰용은 따로 올린다</h2>
 * 배너 자리의 비율이 폭에 따라 뒤집힌다 — 데스크톱은 가로로 길고 휴대폰은 세로로
 * 길다. 한 장으로 둘 다 덮으면 가로가 크게 잘려 이미지 안의 문구가 화면 밖으로
 * 밀린다. 비워 두면 가로용 한 장이 두 폭을 다 맡는다 — 급할 때 한 장만 올리는
 * 길은 막지 않는다.
 *
 * <h2>넣은 수만큼 슬라이드가 생긴다</h2>
 * 한 장이면 넘어가지 않고, 여러 장이면 그 수만큼 돌아간다. 점과 번호도 따라 늘어난다.
 *
 * <h2>통째로 갈아 끼운다</h2>
 * 저장하면 지금 화면에 있는 것이 곧 전체가 된다. 한 장씩 더하고 지우는 길을 두지
 * 않는 이유는, 봄 배너를 여름 배너로 바꾸는 동안 반쯤 섞인 배너가 손님 화면에
 * 걸리지 않게 하기 위해서다.
 */
export function AdminBannersView() {
  const { can } = useAdminSession();
  const canEdit = can('MANAGER');

  const { data, isLoading, error } = useAdminBanners();
  const replace = useReplaceBanners();

  /** 손대기 전에는 null 이고, 그때는 서버가 준 것을 그대로 쓴다. */
  const [draft, setDraft] = useState<Row[] | null>(null);
  const [saved, setSaved] = useState(false);
  const [previewing, setPreviewing] = useState(false);

  const rows: Row[] =
    draft ??
    (data ?? []).map((b) => ({
      imageKey: b.imageKey,
      mobileImageKey: b.mobileImageKey ?? '',
      linkHref: b.linkHref ?? '',
      altText: b.altText ?? '',
    }));

  /**
   * 사진 목록이 바뀌었다 — 순서를 바꿨거나, 넣었거나, 뺐다.
   *
   * <p>주소·설명은 사진에 붙어 있다. 그래서 키로 찾아 옮긴다. 자리로 옮기면 가운데
   * 한 장을 뺐을 때 그 뒤의 주소가 한 칸씩 당겨져 엉뚱한 사진에 붙는다.
   */
  const onImagesChange = (keys: string[]) => {
    const byKey = new Map(rows.map((r) => [r.imageKey, r]));
    setDraft(
      keys.map(
        (key) =>
          byKey.get(key) ?? { imageKey: key, mobileImageKey: '', linkHref: '', altText: '' },
      ),
    );
    setSaved(false);
  };

  const edit = (imageKey: string, patch: Partial<Row>) => {
    setDraft(rows.map((r) => (r.imageKey === imageKey ? { ...r, ...patch } : r)));
    setSaved(false);
  };

  const save = () => {
    setSaved(false);
    replace.mutate(
      rows.map((r) => ({
        imageKey: r.imageKey,
        mobileImageKey: r.mobileImageKey || null,
        linkHref: r.linkHref.trim() || null,
        altText: r.altText.trim() || null,
      })),
      {
        onSuccess: () => {
          setDraft(null);
          setSaved(true);
        },
      },
    );
  };

  const changed = draft != null;

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <div>
          <h1 className={styles.title}>메인 배너</h1>
          <p className={styles.subtitle}>
            메인 화면 맨 위에서 돌아가는 배너다. 넣은 장수만큼 슬라이드가 생긴다.
          </p>
        </div>
      </header>

      {error && (
        <Alert tone="danger" title="불러오지 못했습니다">
          {error instanceof ApiError ? error.message : '잠시 후 다시 시도해 주세요.'}
        </Alert>
      )}
      {replace.error && (
        <Alert tone="danger" title="저장하지 못했습니다">
          {replace.error instanceof ApiError
            ? replace.error.message
            : '잠시 후 다시 시도해 주세요.'}
        </Alert>
      )}
      {saved && !changed && (
        <Alert tone="success" title="저장했습니다">
          메인 화면에 바로 반영됩니다.
        </Alert>
      )}

      <Panel
        title="홈페이지 배너 이미지"
        note={`권장 ${RECOMMENDED} (가로 : 세로 = 16 : 7). 최대 ${MAX_BANNERS}장. 왼쪽부터 차례로 돌아간다. 여기 올린 장수가 슬라이드 수다.`}
        actions={
          <>
            {/* 저장하지 않아도 눌린다 — 저장한 뒤에만 볼 수 있으면 "일단 저장하고
                확인하고 되돌리는" 절차가 되고, 그 사이 확인되지 않은 배너가 메인에 걸린다. */}
            <Button onClick={() => setPreviewing(true)} disabled={rows.length === 0}>
              미리보기
            </Button>
            {canEdit && (
              <Button variant="gold" onClick={save} disabled={!changed || replace.isPending}>
                {replace.isPending ? '저장 중…' : '저장'}
              </Button>
            )}
          </>
        }
      >
        {/* 크기를 여기에 적는 이유 — 아래 「휴대폰 배너 이미지」 칸은 홈페이지용을 한 장
            올린 뒤에야 생긴다. 사진을 준비하는 사람은 그 전에 두 크기를 다 알아야 한다. */}
        <Alert tone="info" title="두 가지 크기로 준비해 주세요">
          <ul className={styles.bannerSizes}>
            <li>
              <strong>홈페이지 배너 {RECOMMENDED}</strong> (가로 : 세로 = 16 : 7) — 컴퓨터 화면
            </li>
            <li>
              <strong>휴대폰 배너 {RECOMMENDED_MOBILE}</strong> (세로가 더 긴 사진) — 휴대폰
            </li>
          </ul>
          휴대폰 배너 자리는 <strong>세로가 더 깁니다.</strong> 홈페이지용 한 장만 올리면
          휴대폰에서 좌우가 3분의 2쯤 잘려 나갑니다.
          <br />
          문구와 버튼까지 이미지 안에 들어갑니다 — 글자를 따로 넣는 칸은 없고,{' '}
          <strong>같은 문구를 두 장에 각각</strong> 넣으셔야 합니다.
        </Alert>

        {isLoading ? (
          <p className={styles.hint}>불러오는 중…</p>
        ) : (
          <ImageUploader
            value={rows.map((r) => r.imageKey)}
            onChange={onImagesChange}
            folder="banners"
            max={MAX_BANNERS}
            disabled={!canEdit}
            emptyLabel="홈페이지 배너 이미지를 끌어다 놓거나 클릭해서 선택하세요"
            preview="tall"
          />
        )}
      </Panel>

      {/* 휴대폰용을 제 패널로 떼어낸다. 홈페이지용 옆에 썸네일을 함께 두는 이유는
          이 칸이 「몇 번째 배너의 휴대폰 사진」인지가 보여야 하기 때문이다 — 두 목록을
          따로 세우면 세 장씩 올렸을 때 어느 것이 어느 것의 짝인지 알 수 없다. */}
      {rows.length > 0 && (
        <Panel
          title="휴대폰 배너 이미지"
          note={`권장 ${RECOMMENDED_MOBILE} (세로가 더 긴 사진). 비워 두면 왼쪽의 홈페이지 배너를 그대로 쓴다 — 좌우가 크게 잘린다.`}
        >
          <ul className={styles.bannerRows}>
            {rows.map((row, index) => (
              <li key={row.imageKey} className={styles.bannerRow}>
                <span className={styles.bannerNo}>{index + 1}</span>
                {/* 관리자 화면이고 수가 적다. next/image 로 최적화할 것이 없다. */}
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img className={styles.bannerThumb} src={mediaUrl(row.imageKey) ?? ''} alt="" />
                <div className={styles.bannerMobileSlot}>
                  <ImageUploader
                    value={row.mobileImageKey ? [row.mobileImageKey] : []}
                    onChange={(keys) => edit(row.imageKey, { mobileImageKey: keys[0] ?? '' })}
                    folder="banners"
                    max={1}
                    disabled={!canEdit}
                    emptyLabel={`${index + 1}번 배너의 휴대폰 이미지를 올려 주세요`}
                    preview="tall"
                  />
                </div>
              </li>
            ))}
          </ul>
        </Panel>
      )}

      <BannerPreviewModal
        open={previewing}
        onClose={() => setPreviewing(false)}
        imageKeys={rows.map((r) => r.imageKey)}
        mobileImageKeys={rows.map((r) => r.mobileImageKey)}
      />

      {rows.length > 0 && (
        <Panel
          title="배너별 설정"
          note="누르면 갈 곳과, 눈으로 보지 않는 분에게 읽어 줄 설명."
        >
          <ul className={styles.bannerRows}>
            {rows.map((row, index) => (
              <li key={row.imageKey} className={styles.bannerRow}>
                <span className={styles.bannerNo}>{index + 1}</span>
                {/* 관리자 화면이고 수가 적다. next/image 로 최적화할 것이 없다. */}
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img className={styles.bannerThumb} src={mediaUrl(row.imageKey) ?? ''} alt="" />
                <div className={styles.bannerFields}>
                  <Field
                    label="누르면 갈 곳"
                    htmlFor={`href-${index}`}
                    hint="비우면 누를 수 없는 배너가 된다. 예: /shop?categoryId=1"
                  >
                    <TextInput
                      id={`href-${index}`}
                      value={row.linkHref}
                      disabled={!canEdit}
                      placeholder="/shop"
                      onChange={(e) => edit(row.imageKey, { linkHref: e.target.value })}
                    />
                  </Field>
                  <Field
                    label="설명 (대체 텍스트)"
                    htmlFor={`alt-${index}`}
                    hint="이미지 안의 글자는 읽어 주는 도구에 잡히지 않는다. 같은 내용을 적어 둔다."
                  >
                    <TextInput
                      id={`alt-${index}`}
                      value={row.altText}
                      disabled={!canEdit}
                      placeholder="예: 순금 골드바 기획전"
                      onChange={(e) => edit(row.imageKey, { altText: e.target.value })}
                    />
                  </Field>
                </div>
              </li>
            ))}
          </ul>
        </Panel>
      )}
    </div>
  );
}
