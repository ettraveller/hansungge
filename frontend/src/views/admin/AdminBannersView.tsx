'use client';

import { useState } from 'react';

import { Alert } from '@/components/admin/Alert';
import { Button } from '@/components/admin/Button';
import { Field, TextInput } from '@/components/admin/Field';
import { ImageUploader } from '@/components/admin/ImageUploader';
import { LinkTargetPicker } from '@/components/admin/LinkTargetPicker';
import { BannerPreviewModal } from '@/components/admin/banners/BannerPreviewModal';
import { FooterPanel } from '@/components/admin/banners/FooterPanel';
import type { FooterInfo } from '@/api/footer';
import { HERO_SLIDES } from '@/views/home-content';
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
 * <p>올리는 칸은 둘이고 <b>자리로 짝을 맞춘다</b> — 첫 장은 첫 장끼리, 둘째는
 * 둘째끼리. 휴대폰용을 배너마다 한 칸씩 끼워 넣으면 홈페이지용을 올리기 전에는
 * 그 칸이 아예 보이지 않는데, 사진을 준비하는 사람은 두 칸을 처음부터 함께 봐야 한다.
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

  /**
   * 고치는 중인 푸터.
   *
   * <p>푸터 패널이 아니라 여기가 들고 있다 — 「미리보기」가 저장하지 않은 푸터까지
   * 보여 줘야 하고, 그 창은 패널의 형제다. 패널 안에 갇힌 값은 거기까지 가지 못한다.
   */
  const [footerDraft, setFooterDraft] = useState<FooterInfo | null>(null);

  const rows: Row[] =
    draft ??
    (data ?? []).map((b) => ({
      imageKey: b.imageKey,
      linkHref: b.linkHref ?? '',
      altText: b.altText ?? '',
    }));

  /** 휴대폰용 목록. 자리로 짝을 맞춘다 — n번째가 n번째 배너의 휴대폰 사진이다. */
  const [mobileDraft, setMobileDraft] = useState<string[] | null>(null);
  const mobileKeys: string[] = mobileDraft ?? (data ?? []).map((b) => b.mobileImageKey ?? '');

  /**
   * 사진 목록이 바뀌었다 — 순서를 바꿨거나, 넣었거나, 뺐다.
   *
   * <p>주소·설명은 사진에 붙어 있다. 그래서 키로 찾아 옮긴다. 자리로 옮기면 가운데
   * 한 장을 뺐을 때 그 뒤의 주소가 한 칸씩 당겨져 엉뚱한 사진에 붙는다.
   */
  const onImagesChange = (keys: string[]) => {
    const byKey = new Map(rows.map((r) => [r.imageKey, r]));
    setDraft(
      keys.map((key) => byKey.get(key) ?? { imageKey: key, linkHref: '', altText: '' }),
    );
    setSaved(false);
  };

  const onMobileChange = (keys: string[]) => {
    setMobileDraft(keys);
    setSaved(false);
  };

  const edit = (imageKey: string, patch: Partial<Row>) => {
    setDraft(rows.map((r) => (r.imageKey === imageKey ? { ...r, ...patch } : r)));
    setSaved(false);
  };

  const save = () => {
    setSaved(false);
    replace.mutate(
      rows.map((r, index) => ({
        imageKey: r.imageKey,
        mobileImageKey: mobileKeys[index] || null,
        linkHref: r.linkHref.trim() || null,
        altText: r.altText.trim() || null,
      })),
      {
        onSuccess: () => {
          setDraft(null);
          setMobileDraft(null);
          setSaved(true);
        },
      },
    );
  };

  const changed = draft != null || mobileDraft != null;
  /** 휴대폰용을 올리긴 했는데 장수가 모자란 경우. 뒤쪽 배너는 홈페이지용으로 버틴다. */
  const shortOnMobile = mobileKeys.length > 0 && mobileKeys.length < rows.length;

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <div>
          <h1 className={styles.title}>메인 배너 &amp; 푸터</h1>
          <p className={styles.subtitle}>
            손님 화면의 맨 위(배너)와 맨 아래(푸터)다. 「미리보기」로 메인 한 화면을
            그대로 띄워 두 곳을 함께 확인할 수 있다.
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
        title="배너 이미지"
        note={`최대 ${MAX_BANNERS}장. 왼쪽부터 차례로 돌아간다. 홈페이지용에 올린 장수가 슬라이드 수다.`}
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

        {/* 올린 배너가 없으면 손님 화면에는 코드에 들어 있는 기본 배너가 나간다.
            그 사실을 여기 적지 않으면 "관리자 화면은 비어 있는데 메인에는 배너가
            돌아간다" 가 되고, 어디서 고치는 것인지 찾을 길이 없다. 기본 배너는
            사진 한 장이 아니라 글자와 버튼이 붙은 슬라이드라 이 칸에 올려 둘 수 없다 —
            사진을 한 장이라도 올리면 그 자리를 통째로 넘겨받는다. */}
        {!isLoading && rows.length === 0 && (
          <Alert tone="info" title="지금은 기본 배너가 나가고 있습니다">
            아직 올린 사진이 없어서, 메인에는 코드에 들어 있는 기본 배너 {HERO_SLIDES.length}
            장이 돌아갑니다.
            <ul className={styles.bannerSizes}>
              {HERO_SLIDES.map((slide) => (
                <li key={slide.title}>
                  {slide.title} {slide.highlight} — 「{slide.cta}」 → {slide.href}
                </li>
              ))}
            </ul>
            사진을 한 장이라도 올려 저장하면 기본 배너는 사라지고 <strong>올린 것만</strong>{' '}
            나갑니다. 기본 배너는 글자와 버튼이 붙은 슬라이드라 이 칸에 올려 둘 수 없습니다.
          </Alert>
        )}

        {isLoading ? (
          <p className={styles.hint}>불러오는 중…</p>
        ) : (
          <div className={styles.bannerUploads}>
            <Field
              label="홈페이지 배너 이미지"
              hint={`권장 ${RECOMMENDED} (가로 : 세로 = 16 : 7). 여기 올린 장수가 슬라이드 수다.`}
            >
              <ImageUploader
                value={rows.map((r) => r.imageKey)}
                onChange={onImagesChange}
                folder="banners"
                max={MAX_BANNERS}
                disabled={!canEdit}
                emptyLabel="홈페이지 배너 이미지를 끌어다 놓거나 클릭해서 선택하세요"
                preview="tall"
              />
            </Field>

            {/* 바로 아래에 같은 칸을 둔다. 배너마다 한 칸씩 끼워 넣으면 홈페이지용을
                올리기 전에는 이 칸이 아예 보이지 않아, 사진을 준비하는 사람이 두 크기를
                함께 보지 못한다. 짝은 자리로 맞춘다 — 첫 장은 첫 장끼리. */}
            <Field
              label="휴대폰 배너 이미지"
              hint={`권장 ${RECOMMENDED_MOBILE} (세로가 더 긴 사진). 위와 같은 차례로 짝이 된다 — 첫 장은 첫 장끼리. 비워 두면 그 자리는 홈페이지용을 잘라 쓴다.`}
            >
              <ImageUploader
                value={mobileKeys}
                onChange={onMobileChange}
                folder="banners"
                max={MAX_BANNERS}
                disabled={!canEdit}
                emptyLabel="휴대폰 배너 이미지를 끌어다 놓거나 클릭해서 선택하세요"
                preview="tall"
              />
            </Field>

            {shortOnMobile && (
              <p className={styles.hintWarn}>
                휴대폰 배너가 {mobileKeys.length}장, 홈페이지 배너가 {rows.length}장이다.{' '}
                {rows.length - mobileKeys.length}장은 휴대폰에서도 홈페이지용을 잘라서 쓴다.
              </p>
            )}
          </div>
        )}
      </Panel>

      <BannerPreviewModal
        open={previewing}
        onClose={() => setPreviewing(false)}
        imageKeys={rows.map((r) => r.imageKey)}
        mobileImageKeys={rows.map((_, index) => mobileKeys[index] ?? '')}
        linkHrefs={rows.map((r) => r.linkHref)}
        footer={footerDraft}
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
                    hint="메뉴나 상품에서 고른다. 비우면 누를 수 없는 배너가 된다."
                  >
                    <LinkTargetPicker
                      id={`href-${index}`}
                      value={row.linkHref}
                      disabled={!canEdit}
                      onChange={(linkHref) => edit(row.imageKey, { linkHref })}
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

      {/* 배너와 같은 화면에 둔다 — 둘 다 손님 화면에 그대로 나가는 겉모습이고,
          미리보기가 메인 한 화면을 통째로 띄우니 같은 그림 안에 있다. */}
      <FooterPanel draft={footerDraft} onDraftChange={setFooterDraft} />
    </div>
  );
}
