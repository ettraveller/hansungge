import type { PolicySection } from './privacy-content';
import styles from './PrivacyView.module.css';

/**
 * 조문 한 절 — 개인정보처리방침과 이용약관이 함께 쓴다.
 *
 * <h2>왜 공유하는가</h2>
 * 두 문서가 같은 {@link PolicySection} 모양이고 같은 틀로 보여야 한다. 각자 그리면
 * 한쪽만 고치게 되는데, 실제로 그런 일이 있었다 — callout 을 평문으로 그려서
 * {@code <b>} 가 글자 그대로 보였다 (2026-10-29). 그릴 곳이 하나면 그 일이 없다.
 *
 * <h2>HTML 을 그대로 그린다</h2>
 * 본문에 {@code <b>}·{@code <a>} 를 쓰는 문장이 있다. 값은 전부 이 저장소 안의 상수라
 * 외부 입력이 섞이지 않는다 — 사용자 입력이었다면 쓰지 않았을 방법이다.
 */
export function PolicySectionBlock({ section, id }: { section: PolicySection; id: string }) {
  return (
    <section className={styles.section} id={id}>
      <h2 className={styles.heading}>{section.title}</h2>

      {section.body?.map((p, i) => (
        <p key={i} className={styles.para} dangerouslySetInnerHTML={{ __html: p }} />
      ))}

      {section.list && (
        <ul className={styles.list}>
          {section.list.map((item, i) => (
            <li key={i} dangerouslySetInnerHTML={{ __html: item }} />
          ))}
        </ul>
      )}

      {section.table && (
        <div className={styles.tableWrap}>
          <table className={styles.table}>
            <thead>
              <tr>
                {section.table.head.map((h) => (
                  <th key={h} scope="col">
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {section.table.rows.map((row, i) => (
                <tr key={i}>
                  {row.map((cell, j) => (
                    <td key={j}>{cell}</td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {section.callout &&
        (Array.isArray(section.callout) ? section.callout : [section.callout]).map((text) => (
          <p
            key={text}
            className={styles.callout}
            dangerouslySetInnerHTML={{ __html: text }}
          />
        ))}
    </section>
  );
}
