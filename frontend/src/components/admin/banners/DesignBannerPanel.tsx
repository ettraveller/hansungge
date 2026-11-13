'use client';

import { useState } from 'react';

import type {
  BannerBlock,
  BannerContent,
  BannerOverlay,
  BannerTextStyle,
} from '@/api/banners';
import { HeroSlider } from '@/components/home/HeroSlider';
import { Button } from '../Button';
import { Field, SelectInput, TextInput } from '../Field';
import { ImageUploader } from '../ImageUploader';
import { LinkTargetPicker } from '../LinkTargetPicker';
import { Panel } from '../Panel';
import { EditableText } from './EditableText';
import styles from '../admin.module.css';

/** 한 배너에 쌓을 수 있는 요소 수. 서버도 같은 값으로 막는다. */
const MAX_BLOCKS = 8;

/** 고를 수 있는 흐림. 자유 숫자를 받지 않는 이유는 blur(9999px) 로 화면이 지워지기 때문이다. */
const BLUR_STEPS = [0, 4, 8, 16] as const;

const TEXT_STYLES: { value: BannerTextStyle; label: string }[] = [
  { value: 'EYEBROW', label: '작은 머리말' },
  { value: 'TITLE', label: '제목' },
  { value: 'HIGHLIGHT', label: '강조 제목' },
  { value: 'DESC', label: '설명' },
];

const OVERLAYS: { value: BannerOverlay; label: string }[] = [
  { value: 'GRADIENT', label: '왼쪽부터' },
  { value: 'SOLID', label: '고르게' },
  { value: 'NONE', label: '없음' },
];

/** 배경색을 고르지 않은 배너의 바탕. 손님 화면의 히어로 바탕과 같은 값이다. */
const DEFAULT_BG = '#0f1b2d';

export interface DesignRow {
  content: BannerContent;
  altText: string;
}

/** 새 배너 한 장. 빈 화면에서 시작하면 무엇을 해야 할지 보이지 않는다. */
function emptyBanner(): DesignRow {
  return {
    altText: '',
    content: {
      background: { color: DEFAULT_BG, blur: 0, overlay: 'GRADIENT' },
      align: 'LEFT',
      blocks: [
        { id: 'b1', type: 'TEXT', style: 'TITLE', text: '제목을 적어 주세요' },
        { id: 'b2', type: 'TEXT', style: 'DESC', text: '설명을 적어 주세요' },
      ],
    },
  };
}

/** 겹치지 않는 이름. 숨기기가 이 이름으로 블록을 가리킨다. */
function nextId(blocks: BannerBlock[]): string {
  const used = new Set(blocks.map((b) => b.id));
  for (let i = 1; i <= MAX_BLOCKS + 1; i += 1) {
    if (!used.has(`b${i}`)) return `b${i}`;
  }
  return `b${Date.now()}`;
}

/**
 * 화면에서 배너를 꾸민다.
 *
 * <h2>진짜 배너를 띄워 놓고 그 위에서 고친다</h2>
 * 입력칸을 늘어놓은 폼은 <b>고칠 때 보는 모양</b>과 <b>손님이 보는 모양</b>이 다르다.
 * 제목을 한 줄 늘렸을 때 어디서 줄이 접히는지, 배경을 흐리면 글자가 읽히는지는
 * 배너 안에서만 보인다. 손님 화면과 <b>같은 컴포넌트</b>({@link HeroSlider})를 그대로
 * 그리고 글자 자리만 편집기로 바꿔 끼운다.
 *
 * <h2>왜 끌어다 놓지 않는가</h2>
 * 배너의 비율이 폭에 따라 뒤집힌다 (PC 2.3:1, 휴대폰 0.85:1). 좌표로 놓게 하면
 * PC 에서 오른쪽 끝에 둔 글자가 휴대폰에서는 화면 밖이라, 배너 한 장마다 배치를
 * 두 번 잡아야 한다. 위에서 아래로 쌓고 정렬만 고르면 두 폭이 알아서 맞는다.
 *
 * <h2>크기를 숫자로 고르지 않는다</h2>
 * 48px 을 고르면 휴대폰에서도 48px 이라 제목 한 줄이 배너를 덮는다. 역할(제목·강조·
 * 설명)만 고르면 두 폭의 크기는 CSS 가 안다 — 지금 쓰는 배너와 같은 값이다.
 */
export function DesignBannerPanel({
  rows,
  onChange,
  canEdit,
}: {
  rows: DesignRow[];
  onChange: (rows: DesignRow[]) => void;
  canEdit: boolean;
}) {
  const [selected, setSelected] = useState(0);

  const index = Math.min(selected, Math.max(rows.length - 1, 0));
  const row = rows[index];

  const patchContent = (patch: Partial<BannerContent>) => {
    onChange(rows.map((r, i) => (i === index ? { ...r, content: { ...r.content, ...patch } } : r)));
  };

  const patchBackground = (patch: Partial<BannerContent['background']>) => {
    patchContent({ background: { ...row.content.background, ...patch } });
  };

  const patchBlock = (blockId: string, patch: Partial<BannerBlock>) => {
    patchContent({
      blocks: row.content.blocks.map((b) => (b.id === blockId ? { ...b, ...patch } : b)),
    });
  };

  const addBlock = (type: BannerBlock['type']) => {
    const blocks = row.content.blocks;
    if (blocks.length >= MAX_BLOCKS) return;
    const block: BannerBlock =
      type === 'BUTTON'
        ? { id: nextId(blocks), type, text: '자세히 보기', href: '/shop', look: 'GOLD' }
        : { id: nextId(blocks), type, style: 'DESC', text: '새 글자' };
    patchContent({ blocks: [...blocks, block] });
  };

  const removeBlock = (blockId: string) => {
    patchContent({ blocks: row.content.blocks.filter((b) => b.id !== blockId) });
  };

  /** 자리 바꾸기. 쌓이는 차례가 곧 보이는 차례다. */
  const moveBlock = (from: number, to: number) => {
    if (to < 0 || to >= row.content.blocks.length) return;
    const blocks = [...row.content.blocks];
    const [moved] = blocks.splice(from, 1);
    blocks.splice(to, 0, moved);
    patchContent({ blocks });
  };

  const addBanner = () => {
    onChange([...rows, emptyBanner()]);
    setSelected(rows.length);
  };

  const removeBanner = () => {
    onChange(rows.filter((_, i) => i !== index));
    setSelected(Math.max(index - 1, 0));
  };

  return (
    <Panel
      title="꾸민 배너"
      note="배경 위에 글자와 버튼을 쌓는다. 사진 배너보다 먼저 나간다."
      actions={
        canEdit && (
          <Button onClick={addBanner} disabled={rows.length >= 10}>
            + 배너 추가
          </Button>
        )
      }
    >
      {rows.length === 0 ? (
        <p className={styles.hint}>
          꾸민 배너가 없습니다. 「+ 배너 추가」로 만들거나, 아래에서 사진 배너를 올리세요.
        </p>
      ) : (
        <>
          {/* 한 장씩 고친다. 세 장을 한 화면에 늘어놓으면 각각이 작아져서,
              정작 확인해야 할 "손님이 보는 크기"가 아니게 된다. */}
          <div className={styles.previewDevices} role="tablist" aria-label="배너 고르기">
            {rows.map((_, i) => (
              <button
                key={i}
                type="button"
                role="tab"
                aria-selected={i === index}
                className={i === index ? styles.previewDeviceOn : styles.previewDevice}
                onClick={() => setSelected(i)}
              >
                {i + 1}번
              </button>
            ))}
          </div>

          {/* 손님이 보는 그 컴포넌트다. 줄이지 않는다 — 줄이면 글자 크기가 실제와
              달라져서, 제목이 두 줄로 접히는지를 여기서 확인할 수 없다. */}
          <div className={styles.designStage}>
            <HeroSlider
              slides={[]}
              banners={[{ id: index, design: row.content, alt: row.altText }]}
              renderText={
                canEdit
                  ? (_bannerId, blockId, value) => (
                      <EditableText
                        value={value}
                        label="글자"
                        maxLength={200}
                        onChange={(text) => patchBlock(blockId, { text })}
                      />
                    )
                  : undefined
              }
            />
          </div>

          <div className={styles.designControls}>
            {/* ---------- 배경 ---------- */}
            <section className={styles.designGroup}>
              <h3 className={styles.designGroupTitle}>배경</h3>

              <Field label="배경색" hint="사진이 없으면 이 색만 보인다.">
                <div className={styles.designColorRow}>
                  <input
                    type="color"
                    className={styles.designColor}
                    value={row.content.background.color ?? DEFAULT_BG}
                    disabled={!canEdit}
                    aria-label="배경색"
                    onChange={(e) => patchBackground({ color: e.target.value })}
                  />
                  <span className={styles.designColorValue}>
                    {row.content.background.color ?? DEFAULT_BG}
                  </span>
                </div>
              </Field>

              <Field
                label="배경 사진"
                hint="권장 2560 × 1120 px. 비우면 색만 쓴다. 글자가 위에 올라가므로 가운데가 비어 있는 사진이 좋다."
              >
                <ImageUploader
                  value={row.content.background.imageKey ? [row.content.background.imageKey] : []}
                  onChange={(keys) => patchBackground({ imageKey: keys[0] ?? null })}
                  folder="banners"
                  max={1}
                  disabled={!canEdit}
                  emptyLabel="배경 사진을 끌어다 놓거나 클릭해서 선택하세요"
                  preview="tall"
                />
              </Field>

              <Field
                label="휴대폰 배경 사진"
                hint="권장 1080 × 1280 px (세로가 더 긴 사진). 비우면 위 사진을 잘라서 쓴다."
              >
                <ImageUploader
                  value={
                    row.content.background.mobileImageKey
                      ? [row.content.background.mobileImageKey]
                      : []
                  }
                  onChange={(keys) => patchBackground({ mobileImageKey: keys[0] ?? null })}
                  folder="banners"
                  max={1}
                  disabled={!canEdit}
                  emptyLabel="휴대폰 배경 사진을 끌어다 놓거나 클릭해서 선택하세요"
                  preview="tall"
                />
              </Field>

              <Field label="사진 흐리게" hint="사진이 복잡하면 흐릴수록 글자가 읽힌다.">
                <div className={styles.previewDevices}>
                  {BLUR_STEPS.map((blur) => (
                    <button
                      key={blur}
                      type="button"
                      disabled={!canEdit}
                      aria-pressed={row.content.background.blur === blur}
                      className={
                        row.content.background.blur === blur
                          ? styles.previewDeviceOn
                          : styles.previewDevice
                      }
                      onClick={() => patchBackground({ blur })}
                    >
                      {blur === 0 ? '없음' : `${blur}px`}
                    </button>
                  ))}
                </div>
              </Field>

              <Field
                label="어둠막"
                hint="사진 위에 깔아 글자를 읽히게 한다. 사진이 어두우면 「없음」도 된다."
              >
                <div className={styles.previewDevices}>
                  {OVERLAYS.map((overlay) => (
                    <button
                      key={overlay.value}
                      type="button"
                      disabled={!canEdit}
                      aria-pressed={row.content.background.overlay === overlay.value}
                      className={
                        row.content.background.overlay === overlay.value
                          ? styles.previewDeviceOn
                          : styles.previewDevice
                      }
                      onClick={() => patchBackground({ overlay: overlay.value })}
                    >
                      {overlay.label}
                    </button>
                  ))}
                </div>
              </Field>
            </section>

            {/* ---------- 쌓인 것들 ---------- */}
            <section className={styles.designGroup}>
              <h3 className={styles.designGroupTitle}>쌓인 것들</h3>
              <p className={styles.hint}>
                글자는 <strong>위 배너에서 직접</strong> 고칩니다. 여기서는 역할·색·차례를
                바꿉니다. 위에 있을수록 먼저 나옵니다.
              </p>

              <ul className={styles.designBlocks}>
                {row.content.blocks.map((block, i) => (
                  <li key={block.id} className={styles.designBlock}>
                    <div className={styles.designBlockHead}>
                      <span className={styles.designBlockKind}>
                        {block.type === 'BUTTON' ? '버튼' : '글자'}
                      </span>
                      <span className={styles.designBlockText}>{block.text}</span>
                      {canEdit && (
                        <span className={styles.designBlockActions}>
                          <button
                            type="button"
                            aria-label="위로"
                            className={styles.designIconBtn}
                            disabled={i === 0}
                            onClick={() => moveBlock(i, i - 1)}
                          >
                            ↑
                          </button>
                          <button
                            type="button"
                            aria-label="아래로"
                            className={styles.designIconBtn}
                            disabled={i === row.content.blocks.length - 1}
                            onClick={() => moveBlock(i, i + 1)}
                          >
                            ↓
                          </button>
                          <button
                            type="button"
                            aria-label="삭제"
                            className={styles.designIconBtn}
                            onClick={() => removeBlock(block.id)}
                          >
                            ×
                          </button>
                        </span>
                      )}
                    </div>

                    <div className={styles.designBlockFields}>
                      {block.type === 'TEXT' ? (
                        <Field label="역할" htmlFor={`style-${block.id}`}>
                          <SelectInput
                            id={`style-${block.id}`}
                            value={block.style ?? 'DESC'}
                            disabled={!canEdit}
                            onChange={(e) =>
                              patchBlock(block.id, {
                                style: e.target.value as BannerTextStyle,
                              })
                            }
                          >
                            {TEXT_STYLES.map((s) => (
                              <option key={s.value} value={s.value}>
                                {s.label}
                              </option>
                            ))}
                          </SelectInput>
                        </Field>
                      ) : (
                        <Field label="누르면 갈 곳" htmlFor={`href-${block.id}`}>
                          <LinkTargetPicker
                            id={`href-${block.id}`}
                            value={block.href ?? ''}
                            disabled={!canEdit}
                            onChange={(href) => patchBlock(block.id, { href })}
                          />
                        </Field>
                      )}

                      <Field label="색" hint="비우면 그 역할의 기본색">
                        <div className={styles.designColorRow}>
                          <input
                            type="color"
                            className={styles.designColor}
                            value={block.color ?? '#ffffff'}
                            disabled={!canEdit}
                            aria-label="글자색"
                            onChange={(e) => patchBlock(block.id, { color: e.target.value })}
                          />
                          {block.color && canEdit && (
                            <button
                              type="button"
                              className={styles.designClearColor}
                              onClick={() => patchBlock(block.id, { color: null })}
                            >
                              기본색으로
                            </button>
                          )}
                        </div>
                      </Field>
                    </div>
                  </li>
                ))}
              </ul>

              {canEdit && (
                <div className={styles.designAddRow}>
                  <Button
                    onClick={() => addBlock('TEXT')}
                    disabled={row.content.blocks.length >= MAX_BLOCKS}
                  >
                    + 글자
                  </Button>
                  <Button
                    onClick={() => addBlock('BUTTON')}
                    disabled={row.content.blocks.length >= MAX_BLOCKS}
                  >
                    + 버튼
                  </Button>
                </div>
              )}
            </section>

            {/* ---------- 배너 전체 ---------- */}
            <section className={styles.designGroup}>
              <h3 className={styles.designGroupTitle}>배너 전체</h3>

              <Field label="글자 붙는 쪽" hint="휴대폰에서는 가운데가 보기 좋을 때가 많다.">
                <div className={styles.previewDevices}>
                  {(['LEFT', 'CENTER', 'RIGHT'] as const).map((align) => (
                    <button
                      key={align}
                      type="button"
                      disabled={!canEdit}
                      aria-pressed={row.content.align === align}
                      className={
                        row.content.align === align
                          ? styles.previewDeviceOn
                          : styles.previewDevice
                      }
                      onClick={() => patchContent({ align })}
                    >
                      {align === 'LEFT' ? '왼쪽' : align === 'CENTER' ? '가운데' : '오른쪽'}
                    </button>
                  ))}
                </div>
              </Field>

              <Field
                label="설명 (대체 텍스트)"
                htmlFor={`alt-${index}`}
                hint="눈으로 보지 않는 분에게 이 배너가 무엇인지 읽어 준다."
              >
                <TextInput
                  id={`alt-${index}`}
                  value={row.altText}
                  disabled={!canEdit}
                  placeholder="예: 순금 골드바 기획전"
                  onChange={(e) =>
                    onChange(
                      rows.map((r, i) => (i === index ? { ...r, altText: e.target.value } : r)),
                    )
                  }
                />
              </Field>

              {canEdit && (
                <Button variant="danger" onClick={removeBanner}>
                  이 배너 삭제
                </Button>
              )}
            </section>
          </div>
        </>
      )}
    </Panel>
  );
}
