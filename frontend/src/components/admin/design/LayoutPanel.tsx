'use client';

import { Button } from '../Button';
import { TextInput } from '../Field';
import { Panel } from '../Panel';
import {
  MAX_SECTION_DESC,
  MAX_SECTION_TITLE,
  SECTION_META,
  type DesignSection,
  type SiteDesign,
} from '@/api/siteDesign';
import styles from '../admin.module.css';

/**
 * 메인 구성 — 구역의 차례, 내걸기, 제목.
 *
 * <h2>구역 단위로만 옮긴다</h2>
 * 요소를 아무 데나 끌어다 놓게 하지 않는다. 시세판·상품처럼 금액이 걸린 구역은
 * 안쪽 모양이 틀어지면 안 되고, 휴대폰 화면에서의 배치까지 관리자가 챙기게 된다.
 * 차례와 내걸기, 제목만 열어 두면 실제로 바꾸고 싶은 것의 대부분이 덮인다.
 *
 * <h2>배너는 목록에 없다</h2>
 * 언제나 맨 위다. 「배너」 탭에서 따로 고친다.
 */
export function LayoutPanel({
  design,
  disabled,
  actions,
  onChange,
}: {
  design: SiteDesign;
  disabled: boolean;
  /** 저장 단추. 「색」 탭과 같은 것을 쓴다 — 둘이 한 덩어리로 저장된다 */
  actions: React.ReactNode;
  onChange: (next: SiteDesign) => void;
}) {
  const sections = design.sections;

  const patch = (index: number, change: Partial<DesignSection>) =>
    onChange({
      ...design,
      sections: sections.map((s, i) => (i === index ? { ...s, ...change } : s)),
    });

  const move = (index: number, by: -1 | 1) => {
    const target = index + by;
    if (target < 0 || target >= sections.length) return;
    const next = [...sections];
    [next[index], next[target]] = [next[target], next[index]];
    onChange({ ...design, sections: next });
  };

  return (
    <Panel
      title="메인 구성"
      note="메인 화면에 쌓이는 구역의 차례다. 배너는 언제나 맨 위에 있다."
      actions={actions}
    >
      <ol className={styles.sectionRows}>
        {sections.map((section, index) => {
          const meta = SECTION_META[section.key];
          return (
            <li
              key={section.key}
              className={section.on ? styles.sectionRow : styles.sectionRowOff}
            >
              <div className={styles.sectionRowHead}>
                <span className={styles.bannerNo}>{index + 1}</span>
                <div className={styles.sectionRowName}>
                  <strong>{meta.label}</strong>
                  <span className={styles.hint}>{meta.note}</span>
                </div>
                <label className={styles.sectionToggle}>
                  <input
                    type="checkbox"
                    checked={section.on}
                    disabled={disabled}
                    onChange={(e) => patch(index, { on: e.target.checked })}
                  />
                  {section.on ? '내거는 중' : '내림'}
                </label>
                {!disabled && (
                  <div className={styles.sectionRowOrder}>
                    <Button
                      small
                      aria-label={`${meta.label} 위로`}
                      onClick={() => move(index, -1)}
                      disabled={index === 0}
                    >
                      ↑
                    </Button>
                    <Button
                      small
                      aria-label={`${meta.label} 아래로`}
                      onClick={() => move(index, 1)}
                      disabled={index === sections.length - 1}
                    >
                      ↓
                    </Button>
                  </div>
                )}
              </div>

              {section.on && (
                <div className={styles.sectionRowFields}>
                  <TextInput
                    aria-label={`${meta.label} 제목`}
                    value={section.title}
                    maxLength={MAX_SECTION_TITLE}
                    disabled={disabled}
                    placeholder={`제목 (비우면: ${meta.title})`}
                    onChange={(e) => patch(index, { title: e.target.value })}
                  />
                  {meta.desc != null && (
                    <TextInput
                      aria-label={`${meta.label} 설명`}
                      value={section.desc}
                      maxLength={MAX_SECTION_DESC}
                      disabled={disabled}
                      placeholder={`설명 (비우면: ${meta.desc})`}
                      onChange={(e) => patch(index, { desc: e.target.value })}
                    />
                  )}
                </div>
              )}
            </li>
          );
        })}
      </ol>
    </Panel>
  );
}
