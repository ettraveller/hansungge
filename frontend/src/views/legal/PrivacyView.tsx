import { SectionHead } from '@/components/ui/SectionHead';
import {
  PRIVACY_EFFECTIVE_DATE,
  PRIVACY_HISTORY,
  PRIVACY_SECTIONS,
  TODO_PLACEHOLDERS,
  type PolicySection,
} from './privacy-content';
import styles from './PrivacyView.module.css';

/**
 * 개인정보처리방침 (`/privacy`).
 *
 * <h2>이 주소가 이미 쓰이고 있었다</h2>
 * 푸터가 진작 {@code /privacy} 를 링크하고 있어서, 라우트가 없는 동안 <b>운영 사이트에서
 * 404 가 나고 있었다.</b> 소셜 로그인 3사 검수도 모두 이 URL 을 요구한다.
 *
 * <h2>서버 컴포넌트다</h2>
 * 상태가 없다. 그리고 방침은 <b>검수자와 검색엔진이 읽어야 하는 문서</b>라 초기 HTML 에
 * 전문이 실려야 한다 — 자바스크립트로 그리면 검수 도구가 빈 페이지를 본다.
 *
 * <h2>미완성 상태를 숨기지 않는다</h2>
 * 자리표시자가 남아 있으면 화면 맨 위에 <b>눈에 띄는 경고</b>를 띄운다. 방침은 사실과
 * 달라지는 순간 그 자체로 법 위반이라, 초안이 조용히 공개되는 것이 가장 나쁘다.
 * 값을 채우면 경고는 저절로 사라진다.
 */
export function PrivacyView() {
  const draft = hasPlaceholders();

  return (
    <div className={styles.page}>
      <div className="wrap">
        <SectionHead title="개인정보처리방침" />

        {draft && (
          <div className={styles.draft} role="alert">
            <p className={styles.draftTitle}>초안입니다 — 아직 공개용이 아닙니다</p>
            <p>
              상호·사업자등록번호·개인정보 보호책임자 등 회사 정보가 채워지지 않았습니다.
              개인정보처리방침은 회사가 실제로 무엇을 하는지에 대한 법적 선언이라, 사실과
              다른 내용이 공개되면 그 자체가 문제가 됩니다.
            </p>
            <p className={styles.draftFile}>
              채울 곳 : frontend/src/views/legal/privacy-content.ts
            </p>
          </div>
        )}

        <p className={styles.effective}>시행일 : {PRIVACY_EFFECTIVE_DATE}</p>

        <nav className={styles.toc} aria-label="목차">
          <ol>
            {PRIVACY_SECTIONS.map((s) => (
              <li key={s.title}>
                <a href={`#${slug(s.title)}`}>{s.title}</a>
              </li>
            ))}
          </ol>
        </nav>

        {PRIVACY_SECTIONS.map((section) => (
          <Section key={section.title} section={section} />
        ))}

        <section className={styles.section}>
          <h2 className={styles.heading}>부칙</h2>
          <ul className={styles.list}>
            {PRIVACY_HISTORY.map((h) => (
              <li key={h.version}>
                제{h.version}판 ({h.date}) — {h.note}
              </li>
            ))}
          </ul>
        </section>
      </div>
    </div>
  );
}

function Section({ section }: { section: PolicySection }) {
  return (
    <section className={styles.section} id={slug(section.title)}>
      <h2 className={styles.heading}>{section.title}</h2>

      {/* 본문에 <b> 를 쓰는 문장이 있어 그대로 렌더한다. 값은 전부 이 저장소 안의
          상수라 외부 입력이 섞이지 않는다 — 사용자 입력이었다면 쓰지 않았을 방법이다. */}
      {section.body?.map((p, i) => (
        <p key={i} className={styles.para} dangerouslySetInnerHTML={{ __html: p }} />
      ))}

      {section.list && (
        <ul className={styles.list}>
          {section.list.map((item, i) => (
            <li key={i}>{item}</li>
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
          <p key={text} className={styles.callout}>
            {text}
          </p>
        ))}
    </section>
  );
}

/** 목차 앵커. 제목이 한글이라 번호만 뽑아 쓴다 — URL 에 한글이 인코딩되어 길어진다. */
function slug(title: string): string {
  const num = title.match(/^\d+/);
  return num ? `s${num[0]}` : 'appendix';
}

function hasPlaceholders(): boolean {
  const text = JSON.stringify(PRIVACY_SECTIONS) + PRIVACY_EFFECTIVE_DATE;
  return TODO_PLACEHOLDERS.some((p) => text.includes(p));
}
