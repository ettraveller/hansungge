'use client';

import { Button } from '../Button';
import { Panel } from '../Panel';
import { ColorPalette } from '../banners/ColorPalette';
import { COLOR_META, DEFAULT_DESIGN, type ColorKey, type SiteDesign } from '@/api/siteDesign';
import styles from '../admin.module.css';

/**
 * 색 — 자리마다 따로 고른다.
 *
 * <h2>고르지 않은 자리는 기본색이다</h2>
 * 빈 값으로 둔다. 기본색을 값으로 박아 넣으면, 나중에 기본 디자인이 바뀌어도 그 자리만
 * 옛 색으로 남는다. 「기본색으로」는 값을 비우는 것이다.
 *
 * <h2>바탕과 글자를 같이 둔다</h2>
 * 바탕만 밝게 바꾸면 흰 글자가 사라진다. 한 묶음으로 나란히 두어 둘을 함께 보게 한다.
 * 오른쪽 미리보기에 바로 반영되므로 읽히는지는 거기서 확인한다.
 */
export function ColorsPanel({
  design,
  disabled,
  actions,
  onChange,
}: {
  design: SiteDesign;
  disabled: boolean;
  actions: React.ReactNode;
  onChange: (next: SiteDesign) => void;
}) {
  const set = (key: ColorKey, color: string | null) =>
    onChange({ ...design, colors: { ...design.colors, [key]: color ?? '' } });

  const groups = [...new Set(COLOR_META.map((c) => c.group))];
  const anyPicked = COLOR_META.some((c) => design.colors[c.key]);

  return (
    <Panel
      title="색"
      note="자리마다 따로 고른다. 고르지 않은 자리는 기본색이 나간다."
      actions={actions}
    >
      <div className={styles.colorGroups}>
        {groups.map((group) => (
          <section key={group} className={styles.colorGroup}>
            <h3 className={styles.colorGroupTitle}>{group}</h3>
            <ul className={styles.colorRows}>
              {COLOR_META.filter((c) => c.group === group).map((meta) => {
                const picked = design.colors[meta.key];
                return (
                  <li key={meta.key} className={styles.colorRow}>
                    {/* 고르지 않았을 때 지금 나가는 색을 보여 준다 — 빈 칸이면
                        "색이 없다" 로 읽힌다. */}
                    <span
                      className={styles.colorNow}
                      style={{ background: picked || meta.fallback }}
                      aria-hidden="true"
                    />
                    <ColorPalette
                      label={meta.label}
                      value={picked || null}
                      clearable
                      disabled={disabled}
                      onChange={(color) => set(meta.key, color)}
                    />
                    <span className={styles.hint}>
                      {meta.note}
                      {!picked && ' · 기본색'}
                    </span>
                  </li>
                );
              })}
            </ul>
          </section>
        ))}
      </div>

      {!disabled && (
        <div className={styles.topLinkAdd}>
          <Button
            onClick={() => onChange({ ...design, colors: DEFAULT_DESIGN.colors })}
            disabled={!anyPicked}
          >
            전부 기본색으로
          </Button>
          <span className={styles.hint}>
            고른 색을 모두 비운다. 「저장」을 눌러야 손님 화면에 걸린다.
          </span>
        </div>
      )}
    </Panel>
  );
}
