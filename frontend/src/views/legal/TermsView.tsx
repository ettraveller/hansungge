import { SectionHead } from '@/components/ui/SectionHead';
import { PolicySectionBlock } from './PolicySectionBlock';
import { TERMS_EFFECTIVE_DATE, TERMS_SECTIONS } from './terms-content';
import styles from './PrivacyView.module.css';

/**
 * 이용약관 (`/terms`).
 *
 * <h2>서버 컴포넌트다</h2>
 * 상태가 없다. 그리고 약관은 <b>심사자와 검색엔진이 읽어야 하는 문서</b>라 초기 HTML 에
 * 전문이 실려야 한다 — 자바스크립트로 그리면 심사 도구가 빈 페이지를 본다.
 *
 * <h2>개인정보처리방침과 같은 틀을 쓴다</h2>
 * 조문 형식의 법적 문서라는 성격이 같고, 고객이 푸터에서 나란히 열어 본다. 따로 만들면
 * 글자 크기·여백이 조금씩 어긋나서 한쪽이 덜 다듬어진 것처럼 보인다.
 */
export function TermsView() {
  return (
    <div className={styles.page}>
      <div className="wrap">
        <SectionHead title="이용약관" />

        <p className={styles.effective}>시행일 : {TERMS_EFFECTIVE_DATE}</p>

        <nav className={styles.toc} aria-label="목차">
          <ol>
            {TERMS_SECTIONS.map((s) => (
              <li key={s.title}>
                <a href={`#${slug(s.title)}`}>{s.title}</a>
              </li>
            ))}
          </ol>
        </nav>

        {TERMS_SECTIONS.map((section) => (
          <PolicySectionBlock key={section.title} section={section} id={slug(section.title)} />
        ))}
      </div>
    </div>
  );
}

/** 목차 앵커. 제목이 한글이라 조문 번호만 뽑아 쓴다 — URL 에 한글이 인코딩되어 길어진다. */
function slug(title: string): string {
  const num = title.match(/^제(\d+)조/);
  return num ? `t${num[1]}` : 'terms';
}
