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
import { Field, SelectInput } from '../Field';
import { ImageUploader } from '../ImageUploader';
import { LinkTargetPicker } from '../LinkTargetPicker';
import { Panel } from '../Panel';
import { ColorPalette } from './ColorPalette';
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

const ALIGNS = [
  { value: 'LEFT', label: '왼쪽', glyph: '⬅' },
  { value: 'CENTER', label: '가운데', glyph: '↔' },
  { value: 'RIGHT', label: '오른쪽', glyph: '➡' },
] as const;

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
 * <h2>배너 위에서 고르고, 배너 위에서 더한다</h2>
 * 고를 것을 아래 목록으로 늘어놓으면 <b>같은 것이 화면에 두 번</b> 있게 된다 — 배너
 * 안의 제목과 목록 안의 제목. 쓰는 사람은 둘이 같은 것인지 매번 확인해야 하고, 글자가
 * 길면 목록 쪽은 잘려서 어느 줄인지도 알 수 없다. 그래서 고르는 것도 더하는 것도
 * 배너 안에서 한다. 고른 것에 할 수 있는 일만 배너 바로 위 띠에 뜬다 — PPT 가 글자를
 * 끌어 고른 뒤 색을 칠하는 것과 같은 차례다.
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
 * 자리 바꾸기는 그래서 좌표가 아니라 <b>쌓이는 차례</b>(↑ ↓)다.
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
  /** 배너 안에서 지금 고른 것. 띠에 뜨는 모든 단추가 이것 하나에 걸린다. */
  const [activeId, setActiveId] = useState<string | null>(null);

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
    const id = nextId(blocks);
    const block: BannerBlock =
      type === 'BUTTON'
        ? { id, type, text: '자세히 보기', href: '/shop', look: 'GOLD' }
        : { id, type, style: 'DESC', text: '새 글자' };
    patchContent({ blocks: [...blocks, block] });
    // 더한 것을 바로 고른 상태로 둔다. 안 그러면 띠가 빈 채라 색을 칠하려면 한 번 더 눌러야 한다.
    setActiveId(id);
  };

  const removeBlock = (blockId: string) => {
    patchContent({ blocks: row.content.blocks.filter((b) => b.id !== blockId) });
    setActiveId(null);
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
    setActiveId(null);
  };

  const removeBanner = () => {
    onChange(rows.filter((_, i) => i !== index));
    setSelected(Math.max(index - 1, 0));
    setActiveId(null);
  };

  const activeIndex = row ? row.content.blocks.findIndex((b) => b.id === activeId) : -1;
  const active = activeIndex >= 0 ? row.content.blocks[activeIndex] : null;

  return (
    <Panel
      title="꾸민 배너"
      note="배너 안의 글자를 눌러 고르고, 바로 위 띠에서 색·역할·차례를 바꾼다. 사진 배너보다 먼저 나간다."
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
          <div className={styles.designTabs}>
            <div className={styles.previewDevices} role="tablist" aria-label="배너 고르기">
              {rows.map((_, i) => (
                <button
                  key={i}
                  type="button"
                  role="tab"
                  aria-selected={i === index}
                  className={i === index ? styles.previewDeviceOn : styles.previewDevice}
                  onClick={() => {
                    setSelected(i);
                    setActiveId(null);
                  }}
                >
                  {i + 1}번
                </button>
              ))}
            </div>
            {canEdit && (
              <button type="button" className={styles.designClearColor} onClick={removeBanner}>
                이 배너 삭제
              </button>
            )}
          </div>

          <div className={styles.designEditor}>
            {/* 고른 것에 할 수 있는 일만 뜬다. 아무것도 고르지 않았을 때도 띠는 남겨 둔다 —
                뜨고 사라지면 누를 때마다 아래 배너가 위아래로 튄다. */}
            {canEdit && (
              <div className={styles.designBar}>
                {active ? (
                  <>
                    <span className={styles.designBlockKind}>
                      {active.type === 'BUTTON' ? '버튼' : '글자'}
                    </span>

                    {active.type === 'TEXT' ? (
                      <SelectInput
                        value={active.style ?? 'DESC'}
                        aria-label="글자 역할"
                        onChange={(e) =>
                          patchBlock(active.id, { style: e.target.value as BannerTextStyle })
                        }
                      >
                        {TEXT_STYLES.map((s) => (
                          <option key={s.value} value={s.value}>
                            {s.label}
                          </option>
                        ))}
                      </SelectInput>
                    ) : (
                      <LinkTargetPicker
                        value={active.href ?? ''}
                        onChange={(href) => patchBlock(active.id, { href })}
                      />
                    )}

                    <ColorPalette
                      value={active.color}
                      label="글자색"
                      clearable
                      onChange={(color) => patchBlock(active.id, { color })}
                    />

                    <span className={styles.designBlockActions}>
                      <button
                        type="button"
                        aria-label="위로"
                        title="위로"
                        className={styles.designIconBtn}
                        disabled={activeIndex === 0}
                        onClick={() => moveBlock(activeIndex, activeIndex - 1)}
                      >
                        ↑
                      </button>
                      <button
                        type="button"
                        aria-label="아래로"
                        title="아래로"
                        className={styles.designIconBtn}
                        disabled={activeIndex === row.content.blocks.length - 1}
                        onClick={() => moveBlock(activeIndex, activeIndex + 1)}
                      >
                        ↓
                      </button>
                      <button
                        type="button"
                        aria-label="삭제"
                        title="삭제"
                        className={styles.designIconBtn}
                        onClick={() => removeBlock(active.id)}
                      >
                        ×
                      </button>
                    </span>
                  </>
                ) : (
                  <span className={styles.designBarHint}>
                    배너 안의 글자를 누르면 여기에서 색·역할·차례를 바꿀 수 있습니다.
                  </span>
                )}

                <span className={styles.designBarGap} />

                {/* 글자가 붙는 쪽은 블록 하나가 아니라 배너 전체의 것이다 */}
                <span className={styles.designBlockActions}>
                  {ALIGNS.map((a) => (
                    <button
                      key={a.value}
                      type="button"
                      aria-label={`글자 ${a.label}`}
                      title={`글자 ${a.label}`}
                      aria-pressed={row.content.align === a.value}
                      className={
                        row.content.align === a.value ? styles.designIconBtnOn : styles.designIconBtn
                      }
                      onClick={() => patchContent({ align: a.value })}
                    >
                      {a.glyph}
                    </button>
                  ))}
                </span>
              </div>
            )}

            {/* 손님이 보는 그 컴포넌트다. 줄이지 않는다 — 줄이면 글자 크기가 실제와
                달라져서, 제목이 두 줄로 접히는지를 여기서 확인할 수 없다. */}
            <div
              className={styles.designStage}
              onClickCapture={(e) => {
                // 배너 안의 버튼은 진짜 <a> 다. 고치는 중에 눌러서 화면이 넘어가면
                // 저장하지 않은 것이 전부 사라진다.
                if ((e.target as HTMLElement).closest('a')) e.preventDefault();
              }}
            >
              <HeroSlider
                slides={[]}
                banners={[{ id: index, design: row.content, alt: row.altText }]}
                renderText={
                  canEdit
                    ? (_bannerId, blockId, value) => (
                        <span
                          className={blockId === activeId ? styles.pickOn : styles.pick}
                          onFocusCapture={() => setActiveId(blockId)}
                          onPointerDownCapture={() => setActiveId(blockId)}
                        >
                          <EditableText
                            value={value}
                            label="글자"
                            maxLength={200}
                            onChange={(text) => patchBlock(blockId, { text })}
                          />
                        </span>
                      )
                    : undefined
                }
                trailing={
                  canEdit ? (
                    <span className={styles.designAddOnStage} contentEditable={false}>
                      <button
                        type="button"
                        className={styles.designAddChip}
                        disabled={row.content.blocks.length >= MAX_BLOCKS}
                        onClick={() => addBlock('TEXT')}
                      >
                        + 글자
                      </button>
                      <button
                        type="button"
                        className={styles.designAddChip}
                        disabled={row.content.blocks.length >= MAX_BLOCKS}
                        onClick={() => addBlock('BUTTON')}
                      >
                        + 버튼
                      </button>
                    </span>
                  ) : undefined
                }
              />
            </div>
          </div>

          {/* ---------- 배경 ---------- */}
          <section className={styles.designGroup}>
            <h3 className={styles.designGroupTitle}>배경</h3>

            <Field label="배경색" hint="사진이 없으면 이 색만 보인다.">
              <ColorPalette
                value={row.content.background.color ?? DEFAULT_BG}
                label="배경색"
                disabled={!canEdit}
                onChange={(color) => patchBackground({ color: color ?? DEFAULT_BG })}
              />
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
        </>
      )}
    </Panel>
  );
}
