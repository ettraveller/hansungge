'use client';

import { useState } from 'react';


import type { BannerBlock, BannerContent, BannerTextStyle } from '@/api/banners';
import { HeroSlider } from '@/components/home/HeroSlider';
import { Button } from '../Button';
import { Field } from '../Field';
import { SelectInput } from '../Field';
import { ImageUploader } from '../ImageUploader';
import { LinkTargetPicker } from '../LinkTargetPicker';
import { Panel } from '../Panel';
import { ColorPalette } from './ColorPalette';
import { EditableText } from './EditableText';
import styles from '../admin.module.css';

/** 한 배너에 쌓을 수 있는 요소 수. 서버도 같은 값으로 막는다. */
const MAX_BLOCKS = 8;

/**
 * 고를 수 있는 글자 크기(px).
 *
 * <p>자유 입력을 받지 않는 이유는 한 배너 안에서 47px 과 48px 이 섞이면 아무도
 * 구분하지 못하면서 줄 높이만 어긋나기 때문이다. 서버는 10~120 으로 당긴다.
 */
const SIZES = [12, 14, 16, 18, 20, 24, 28, 32, 40, 48, 56, 64, 72, 88];

/** 고를 수 있는 흐림. 자유 숫자를 받지 않는 이유는 blur(9999px) 로 화면이 지워지기 때문이다. */
const BLURS = [
  { value: 0, label: '흐림 없음' },
  { value: 4, label: '흐림 4px' },
  { value: 8, label: '흐림 8px' },
  { value: 16, label: '흐림 16px' },
];

const OVERLAYS = [
  { value: 'GRADIENT', label: '어둠막 왼쪽부터' },
  { value: 'SOLID', label: '어둠막 고르게' },
  { value: 'NONE', label: '어둠막 없음' },
] as const;

const ALIGNS = [
  { value: 'LEFT', label: '왼쪽', glyph: '⬅' },
  { value: 'CENTER', label: '가운데', glyph: '↔' },
  { value: 'RIGHT', label: '오른쪽', glyph: '➡' },
] as const;

/**
 * 역할이 들고 있는 굵기.
 *
 * <p>「두껍게」단추가 눌린 상태를 여기서 가져온다. 이것 없이 {@code bold ?? false} 로
 * 두면 제목은 두껍게 보이는데 단추는 꺼져 있어서, 한 번 눌러야 비로소 꺼진다.
 */
const ROLE_BOLD: Record<BannerTextStyle, boolean> = {
  EYEBROW: false,
  TITLE: true,
  HIGHLIGHT: true,
  DESC: false,
};

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

const pct = (value: number) => Math.max(0, Math.min(100, Math.round(value)));

/**
 * 줄이 맞았다고 볼 거리(px).
 *
 * <p>좁게 잡으면 맞추려고 손을 떨게 되고, 넓게 잡으면 맞출 생각이 없었는데 자꾸
 * 끌려간다. PPT·피그마가 쓰는 값이 대체로 이 언저리다.
 */
const SNAP = 6;

/** 끄는 동안 띄울 줄. 무대 왼쪽 위에서의 px 다. */
interface Guides {
  v: number[];
  h: number[];
}

const NO_GUIDES: Guides = { v: [], h: [] };

/**
 * 한 축에서 가장 가까운 줄에 붙인다.
 *
 * @param edges  끄는 것의 변들 — 앞·가운데·끝
 * @param lines  붙을 수 있는 줄들 (다른 블록의 변, 상자 가운데)
 * @return 밀어야 할 거리와 걸린 줄. 아무 줄에도 닿지 않으면 거리 0.
 */
function snap(edges: number[], lines: number[]): { shift: number; hit: number | null } {
  let best: { shift: number; hit: number } | null = null;
  for (const edge of edges) {
    for (const line of lines) {
      const gap = line - edge;
      if (Math.abs(gap) <= SNAP && (best === null || Math.abs(gap) < Math.abs(best.shift))) {
        best = { shift: gap, hit: line };
      }
    }
  }
  return best ?? { shift: 0, hit: null };
}

/**
 * 화면에서 배너를 꾸민다.
 *
 * <h2>배너 위에서 고르고, 배너 위에서 더하고, 끌어서 놓는다</h2>
 * 고를 것을 아래 목록으로 늘어놓으면 <b>같은 것이 화면에 두 번</b> 있게 된다 — 배너
 * 안의 제목과 목록 안의 제목. 그래서 고르는 것도, 더하는 것도, 옮기는 것도 배너
 * 안에서 한다. 고른 것에 할 수 있는 일만 배너 바로 위 띠에 뜬다.
 *
 * <h2>자리는 끌어서 잡고, 좁은 화면에서는 버린다</h2>
 * 끌면 그 블록에 {@code x}·{@code y}(%)가 붙어 그 자리에 선다. 비율이 아니라 %라서
 * 창을 넓혀도 같은 자리다. 다만 <b>좁은 화면에서는 그 좌표를 쓰지 않는다</b> — 배너
 * 비율이 폭에 따라 뒤집혀서(PC 2.3:1, 휴대폰 0.85:1) PC 에서 오른쪽 끝에 둔 글자는
 * 휴대폰에서 화면 밖이다. 거기서는 다시 위에서 아래로 쌓인다. 배치를 배너마다 두 번
 * 잡게 하는 것보다, 한 번 잡고 좁은 화면은 쌓기에 맡기는 편이 낫다.
 *
 * <h2>크기는 px 로 고르고, 좁은 화면에서 줄어든다</h2>
 * 역할(제목·강조·설명)만 고르게 두면 "얼마나 큰지"가 이름 뒤에 숨는다. px 로 고르되
 * 좁은 화면에서는 CSS 가 0.6 배로 다시 잡는다 — 고른 48px 이 휴대폰에서도 48px 이면
 * 제목 한 줄이 배너를 덮는다. 고르지 않으면 역할의 기본 크기를 그대로 쓴다.
 *
 * <h2>진짜 배너를 띄워 놓고 그 위에서 고친다</h2>
 * 입력칸을 늘어놓은 폼은 <b>고칠 때 보는 모양</b>과 <b>손님이 보는 모양</b>이 다르다.
 * 손님 화면과 <b>같은 컴포넌트</b>({@link HeroSlider})를 그대로 그리고 글자 자리만
 * 편집기로 바꿔 끼운다. 배경·흐림·어둠막도 배너 바로 위 띠에 둔다 — 아래 폼에 두면
 * 흐림을 한 칸 올릴 때마다 결과를 보려고 화면을 위로 굴려야 한다.
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
  /** 끄는 동안만 보이는 줄 맞추기 선 */
  const [guides, setGuides] = useState<Guides>(NO_GUIDES);

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
    // 더한 것을 바로 고른 상태로 둔다. 안 그러면 띠가 빈 채라 크기를 바꾸려면 한 번 더 눌러야 한다.
    setActiveId(id);
  };

  const removeBlock = (blockId: string) => {
    patchContent({ blocks: row.content.blocks.filter((b) => b.id !== blockId) });
    setActiveId(null);
  };

  /**
   * 끌어서 자리 잡기.
   *
   * <p>자리는 끄는 상자({@code [data-stack]}) 안에서의 %다. px 로 담으면 창 폭이 바뀔
   * 때마다 글자가 배너 밖으로 밀려난다.
   *
   * <h3>잡은 자리를 유지한다</h3>
   * 포인터 자리를 그대로 좌표로 쓰면, 손잡이를 잡는 순간 블록의 왼쪽 위 모서리가
   * 포인터 밑으로 <b>순간이동</b>한다. 잡은 지점과 모서리의 거리를 빼 두면 집은 자리가
   * 손가락에 그대로 붙어 따라온다.
   *
   * <h3>줄 맞추기</h3>
   * 다른 블록의 앞·가운데·끝, 그리고 상자의 한가운데에 6px 안으로 들어오면 그 줄에
   * 붙이고 줄을 띄운다 — PPT 가 상자를 끌 때 보여주는 그 선이다. 눈대중으로 맞춘
   * 1px 어긋남은 저장하고 나서야 보인다.
   *
   * <p>포인터를 잡아 둔다({@code setPointerCapture}). 잡지 않으면 빠르게 끌 때 포인터가
   * 손잡이를 벗어나는 순간 {@code pointermove} 가 끊겨 글자가 중간에 멈춘다.
   */
  const startDrag = (blockId: string) => (e: React.PointerEvent<HTMLElement>) => {
    // contentEditable 이 포커스를 가져가는 것과, <a> 안에서 시작되는 브라우저 기본
    // 끌기(링크 끌어다 놓기)를 함께 막는다. 버튼 블록이 끌리지 않던 이유다.
    e.preventDefault();
    e.stopPropagation();

    const handle = e.currentTarget;
    const stack = handle.closest('[data-stack]');
    const self = handle.closest('[data-stack] > *');
    const stage = handle.closest('[data-stage]');
    if (!stack || !self || !stage) return;

    const box = stack.getBoundingClientRect();
    const stageBox = stage.getBoundingClientRect();
    const start = self.getBoundingClientRect();

    // 집은 지점과 블록 왼쪽 위 모서리의 거리. 끄는 내내 이 거리를 유지한다.
    const grabX = e.clientX - start.left;
    const grabY = e.clientY - start.top;
    const { width, height } = start;

    // 붙을 수 있는 줄. 끄는 자기 자신은 뺀다.
    const others = Array.from(stack.children).filter((el) => el !== self);
    const linesV = [box.left + box.width / 2];
    const linesH = [box.top + box.height / 2];
    for (const el of others) {
      const r = el.getBoundingClientRect();
      linesV.push(r.left, r.left + r.width / 2, r.right);
      linesH.push(r.top, r.top + r.height / 2, r.bottom);
    }

    handle.setPointerCapture(e.pointerId);

    const move = (ev: PointerEvent) => {
      let left = ev.clientX - grabX;
      let top = ev.clientY - grabY;

      const sv = snap([left, left + width / 2, left + width], linesV);
      const sh = snap([top, top + height / 2, top + height], linesH);
      left += sv.shift;
      top += sh.shift;

      setGuides({
        v: sv.hit === null ? [] : [sv.hit - stageBox.left],
        h: sh.hit === null ? [] : [sh.hit - stageBox.top],
      });

      patchBlock(blockId, {
        x: pct(((left - box.left) / box.width) * 100),
        y: pct(((top - box.top) / box.height) * 100),
      });
    };
    const stop = () => {
      setGuides(NO_GUIDES);
      handle.removeEventListener('pointermove', move);
      handle.removeEventListener('pointerup', stop);
      handle.removeEventListener('pointercancel', stop);
    };

    handle.addEventListener('pointermove', move);
    handle.addEventListener('pointerup', stop);
    handle.addEventListener('pointercancel', stop);
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

  const active = row ? row.content.blocks.find((b) => b.id === activeId) ?? null : null;
  const activeBold =
    active?.bold ?? (active ? ROLE_BOLD[active.style ?? 'DESC'] ?? false : false);
  const placed = active?.x != null && active?.y != null;

  return (
    <Panel
      title="꾸민 배너"
      note="배너 안의 글자를 눌러 고르고, 바로 위 띠에서 크기·색·배경을 바꾼다. 고른 글자의 ✥ 를 끌면 그 자리에 선다."
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
            {canEdit && (
              <div className={styles.designBar}>
                {/* --- 고른 것 --- */}
                <div className={styles.designBarRow}>
                  {active ? (
                    <>
                      <span className={styles.designBlockKind}>
                        {active.type === 'BUTTON' ? '버튼' : '글자'}
                      </span>

                      <SelectInput
                        value={active.size ?? ''}
                        aria-label="글자 크기"
                        onChange={(e) =>
                          patchBlock(active.id, {
                            size: e.target.value ? Number(e.target.value) : null,
                          })
                        }
                      >
                        <option value="">기본 크기</option>
                        {SIZES.map((s) => (
                          <option key={s} value={s}>
                            {s}px
                          </option>
                        ))}
                      </SelectInput>

                      <button
                        type="button"
                        aria-label="두껍게"
                        title="두껍게"
                        aria-pressed={activeBold}
                        className={activeBold ? styles.designIconBtnOn : styles.designIconBtn}
                        onClick={() => patchBlock(active.id, { bold: !activeBold })}
                      >
                        <b>가</b>
                      </button>

                      <ColorPalette
                        value={active.color}
                        label="글자색"
                        clearable
                        onChange={(color) => patchBlock(active.id, { color })}
                      />

                      {active.type === 'BUTTON' && (
                        <LinkTargetPicker
                          value={active.href ?? ''}
                          onChange={(href) => patchBlock(active.id, { href })}
                        />
                      )}

                      {/* 끌어다 둔 자리를 물린다. 이것이 없으면 한 번 끈 글자를
                          다시 줄 맞춰 세울 방법이 없다. */}
                      {placed && (
                        <button
                          type="button"
                          className={styles.designClearColor}
                          onClick={() => patchBlock(active.id, { x: null, y: null })}
                        >
                          자리 풀기
                        </button>
                      )}
                    </>
                  ) : (
                    <span className={styles.designBarHint}>
                      배너 안의 글자를 누르면 여기에서 크기·굵기·색을 바꿀 수 있습니다.
                      고른 글자의 ✥ 를 끌어 옮기고, × 로 지웁니다.
                    </span>
                  )}
                </div>

                {/* --- 배너 전체 ---
                    흐림·어둠막을 아래 폼에 두면 한 칸 올릴 때마다 결과를 보려고
                    화면을 위로 굴려야 한다. 결과가 보이는 자리에 둔다. */}
                <div className={styles.designBarRow}>
                  {/* 더하기는 띠에 둔다. 무대 위에 두면 배너의 일부처럼 보이고,
                      끌어다 둔 블록이 그 위에 겹치면 눌릴 수조차 없다. */}
                  <button
                    type="button"
                    className={styles.designAddBtn}
                    disabled={row.content.blocks.length >= MAX_BLOCKS}
                    onClick={() => addBlock('TEXT')}
                  >
                    + 글자
                  </button>
                  <button
                    type="button"
                    className={styles.designAddBtn}
                    disabled={row.content.blocks.length >= MAX_BLOCKS}
                    onClick={() => addBlock('BUTTON')}
                  >
                    + 버튼
                  </button>

                  <ColorPalette
                    value={row.content.background.color ?? DEFAULT_BG}
                    label="배경색"
                    onChange={(color) => patchBackground({ color: color ?? DEFAULT_BG })}
                  />

                  <SelectInput
                    value={row.content.background.blur}
                    aria-label="사진 흐리게"
                    onChange={(e) => patchBackground({ blur: Number(e.target.value) })}
                  >
                    {BLURS.map((b) => (
                      <option key={b.value} value={b.value}>
                        {b.label}
                      </option>
                    ))}
                  </SelectInput>

                  <SelectInput
                    value={row.content.background.overlay}
                    aria-label="어둠막"
                    onChange={(e) =>
                      patchBackground({
                        overlay: e.target.value as BannerContent['background']['overlay'],
                      })
                    }
                  >
                    {OVERLAYS.map((o) => (
                      <option key={o.value} value={o.value}>
                        {o.label}
                      </option>
                    ))}
                  </SelectInput>

                  <span className={styles.designBarGap} />

                  {/* 끌어다 두지 않은 블록이 쌓이는 쪽. 끈 블록은 제 자리를 쓴다 */}
                  <span className={styles.designBlockActions}>
                    {ALIGNS.map((a) => (
                      <button
                        key={a.value}
                        type="button"
                        aria-label={`쌓이는 글자 ${a.label}`}
                        title={`쌓이는 글자 ${a.label}`}
                        aria-pressed={row.content.align === a.value}
                        className={
                          row.content.align === a.value
                            ? styles.designIconBtnOn
                            : styles.designIconBtn
                        }
                        onClick={() => patchContent({ align: a.value })}
                      >
                        {a.glyph}
                      </button>
                    ))}
                  </span>
                </div>
              </div>
            )}

            {/* 손님이 보는 그 컴포넌트다. 줄이지 않는다 — 줄이면 글자 크기가 실제와
                달라져서, 제목이 두 줄로 접히는지를 여기서 확인할 수 없다. */}
            <div
              data-stage
              className={styles.designStage}
              onClickCapture={(e) => {
                // 배너 안의 버튼은 진짜 <a> 다. 고치는 중에 눌러서 화면이 넘어가면
                // 저장하지 않은 것이 전부 사라진다.
                if ((e.target as HTMLElement).closest('a')) e.preventDefault();
              }}
              // <a> 를 집으면 브라우저가 링크 끌어다 놓기를 시작한다. 그러면 우리
              // pointermove 가 끊겨서 버튼 블록만 끌리지 않는다.
              onDragStartCapture={(e) => e.preventDefault()}
              // 블록 아무 데나 누르면 그것이 골라진다. 글자 칸에만 걸어 두면 버튼은
              // 금색 안쪽 여백이 넓어서 "눌렀는데 아무 일도 없다" — 손잡이도 안 뜬다.
              onPointerDownCapture={(e) => {
                const block = (e.target as HTMLElement).closest<HTMLElement>('[data-block]');
                if (block?.dataset.block) setActiveId(block.dataset.block);
              }}
            >
              {/* PPT 의 맞춤선. 끄는 동안에만 뜬다 */}
              {(guides.v.length > 0 || guides.h.length > 0) && (
                <div className={styles.guideLayer} aria-hidden="true">
                  {guides.v.map((left) => (
                    <i key={`v${left}`} className={styles.guideV} style={{ left }} />
                  ))}
                  {guides.h.map((top) => (
                    <i key={`h${top}`} className={styles.guideH} style={{ top }} />
                  ))}
                </div>
              )}

              <HeroSlider
                slides={[]}
                banners={[{ id: index, design: row.content, alt: row.altText }]}
                renderText={
                  canEdit
                    ? (_bannerId, blockId, value) => (
                        <span
                          className={blockId === activeId ? styles.pickOn : styles.pick}
                          onFocusCapture={() => setActiveId(blockId)}
                        >
                          <EditableText
                            value={value}
                            label="글자"
                            maxLength={200}
                            onChange={(text) => patchBlock(blockId, { text })}
                          />
                          {blockId === activeId && (
                            // 둘 다 <a> 안에도 들어가므로 <button> 이 아니라 span 이다.
                            // 버튼 안의 버튼은 브라우저가 알아서 풀어 버린다.
                            <>
                              <span
                                role="button"
                                tabIndex={-1}
                                aria-label="끌어서 옮기기"
                                title="끌어서 옮기기"
                                className={styles.dragHandle}
                                onPointerDown={startDrag(blockId)}
                              >
                                ✥
                              </span>
                              {/* 지우기는 지울 것에 붙어 있어야 한다. 띠에 두면
                                  "무엇이 지워지는지"를 매번 눈으로 맞춰 봐야 한다. */}
                              <span
                                role="button"
                                tabIndex={-1}
                                aria-label="이 요소 삭제"
                                title="삭제"
                                className={styles.killHandle}
                                onPointerDown={(e) => {
                                  e.preventDefault();
                                  e.stopPropagation();
                                  removeBlock(blockId);
                                }}
                              >
                                ×
                              </span>
                            </>
                          )}
                        </span>
                      )
                    : undefined
                }
              />
            </div>
          </div>

          {/* ---------- 배경 사진 ----------
              색·흐림·어둠막과 달리 사진은 올리는 데 자리가 필요하다. 띠에 넣으면
              띠가 배너만큼 두꺼워진다. */}
          <section className={styles.designGroup}>
            <h3 className={styles.designGroupTitle}>배경 사진</h3>

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

            <p className={styles.hint}>
              끌어다 둔 자리와 px 크기는 <strong>넓은 화면에서만</strong> 쓰입니다. 휴대폰에서는
              다시 위에서 아래로 쌓이고 글자는 0.6 배로 줄어듭니다 — 배너 비율이 폭에 따라
              뒤집혀서, PC 에서 오른쪽 끝에 둔 글자는 휴대폰에서 화면 밖이기 때문입니다.
            </p>
          </section>
        </>
      )}
    </Panel>
  );
}
