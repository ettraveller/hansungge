import { SectionHead } from '@/components/ui/SectionHead';
import {
  PRIVACY_EFFECTIVE_DATE,
  PRIVACY_HISTORY,
  PRIVACY_SECTIONS,
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
 * <h2>초안 경고를 뗐다 (2026-10-01)</h2>
 * 자리표시자가 남아 있으면 맨 위에 "초안입니다 — 아직 공개용이 아닙니다"를 띄우고
 * 있었다. KG이니시스 통합인증 심사에 <b>이 URL 을 제출</b>하는데, 심사자가 열었을 때
 * 그 문구가 보이면 방침이 미완성으로 읽힌다. 남은 값(개인정보 보호책임자·대표전화)은
 * {@code privacy-content.ts} 의 {@code TODO_PLACEHOLDERS} 에 그대로 적혀 있다.
 */
export function PrivacyView() {
  return (
    <div className={styles.page}>
      <div className="wrap">
        <SectionHead title="개인정보처리방침" />

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

