import Link from 'next/link';

import { SectionHead } from '@/components/ui/SectionHead';
import { FAQS, ITEM_GROUPS, NOTES, STEPS, type Step } from './purchase-guide-content';
import styles from './PurchaseGuideView.module.css';

/**
 * 매입절차 (`/purchase-guide`).
 *
 * <p>레퍼런스(한국공인금거래소 매입절차)의 흐름을 따른다.
 *
 * <pre>
 * 01. 매입 품목          ← 금 / 은 / 세공품
 * 02. 매입 절차          ← 방문부터 대금 지급까지 5단계
 * 03. 준비물과 개인정보   ← 신분증, 목적 외 미사용 고지
 *     자주 묻는 것
 * </pre>
 *
 * <h2>접수 폼이 아니다</h2>
 * 설계서 §범위에서 <b>온라인 매입 접수는 제외</b>로 정해져 있다. 이 페이지의 목표는
 * 신청을 받는 것이 아니라 <b>매장에 오시게 만드는 것</b>이라, 모든 안내가 「가까운 지점
 * 찾기」로 수렴한다.
 *
 * <h2>서버 컴포넌트다</h2>
 * 검색·필터 같은 상태가 없어서 클라이언트로 만들 이유가 없다. 그래서 절차 문구가 전부
 * 초기 HTML 에 실린다 — <b>"금 매입", "돌반지 파는 곳"은 이 업종에서 실제로 검색되는
 * 말이다.</b>
 *
 * <h2>시세를 여기서 다시 그리지 않는다</h2>
 * 매입 페이지에 온 사람의 첫 질문은 "얼마 쳐주나"지만, 그 답은 `/price` 가 이미 하고
 * 있다. 같은 값을 화면마다 다른 모양으로 보여주면 어느 쪽이 맞는지 고객이 판단해야
 * 한다. 4단계에서 링크로 보낸다.
 *
 * <h2>단계를 가로 카드로 늘어놓지 않는다</h2>
 * 카드 다섯 장을 나란히 두면 <b>고를 수 있는 선택지</b>처럼 읽힌다. 절차는 순서지
 * 선택지가 아니라 세로로 쌓고 번호를 붙였다 — `PriceView` 의 「시세 안내」가 카드를
 * 쓰지 않는 것과 같은 이유다.
 */
export function PurchaseGuideView() {
  return (
    <>
      <div className={styles.page}>
        <div className="wrap">
          <SectionHead
            as="h1"
            title="매입절차"
            desc="보유하신 금·은 제품을 당일 고시 시세로 매입해 드립니다"
          />

          {/* ---------- 01. 매입 품목 ---------- */}
          <section className={styles.block} aria-labelledby="pg-items">
            <h2 id="pg-items" className={styles.blockTitle}>
              <span className={styles.num}>01</span>
              매입 품목
            </h2>

            <dl className={styles.groups}>
              {ITEM_GROUPS.map((group) => (
                <div key={group.name} className={styles.group}>
                  <dt className={styles.groupName}>{group.name}</dt>
                  <dd>
                    <ul className={styles.items}>
                      {group.items.map((item) => (
                        <li key={item} className={styles.item}>
                          {item}
                        </li>
                      ))}
                    </ul>
                  </dd>
                </div>
              ))}
            </dl>

            <p className={styles.groupNote}>
              위 목록은 예시입니다. 금·은이 포함된 제품이라면 대부분 매입 가능하니, 판단이
              어려우시면 방문 전에 지점으로 문의해 주세요.
            </p>
          </section>

          {/* ---------- 02. 매입 절차 ---------- */}
          <section className={styles.block} aria-labelledby="pg-steps">
            <h2 id="pg-steps" className={styles.blockTitle}>
              <span className={styles.num}>02</span>
              매입 절차
            </h2>

            {/* ol 이라 번호가 의미로도 남는다. 화면의 배지는 장식이므로 aria-hidden 이다 */}
            <ol className={styles.steps}>
              {STEPS.map((step, i) => (
                <StepRow key={step.title} n={i + 1} step={step} />
              ))}
            </ol>
          </section>

          {/* ---------- 03. 준비물 ---------- */}
          <section className={styles.block} aria-labelledby="pg-prepare">
            <h2 id="pg-prepare" className={styles.blockTitle}>
              <span className={styles.num}>03</span>
              준비물과 개인정보
            </h2>

            <div className={styles.prepare}>
              <p className={styles.prepareLead}>
                <strong>신분증</strong>을 지참해 주세요.
              </p>
              <p className={styles.prepareBody}>
                귀금속 거래는 본인 확인이 필요합니다. 확인한 정보는 거래 목적 외의 어떤
                용도로도 사용하지 않으며, 관련 법령이 정한 기간이 지나면 폐기합니다.
              </p>
            </div>
          </section>
        </div>
      </div>

      {/* ---------- 자주 묻는 것 + 다음 행동 ---------- */}
      <section className={styles.faqSection} aria-labelledby="pg-faq">
        <div className="wrap">
          <SectionHead title="자주 묻는 것" />

          <dl className={styles.faqs}>
            {FAQS.map((faq) => (
              <div key={faq.q} className={styles.faq}>
                <dt className={styles.faqQ}>{faq.q}</dt>
                <dd className={styles.faqA}>{faq.a}</dd>
              </div>
            ))}
          </dl>

          <p className={styles.notes}>
            {NOTES.map((note) => (
              <span key={note}>· {note}</span>
            ))}
          </p>

          <div className={styles.actions}>
            <Link className={styles.primaryBtn} href="/store">
              가까운 지점 찾기
            </Link>
            <Link className={styles.ghostBtn} href="/price">
              오늘 시세 보기
            </Link>
          </div>
        </div>
      </section>
    </>
  );
}

function StepRow({ n, step }: { n: number; step: Step }) {
  return (
    <li className={styles.step}>
      <span className={`${styles.stepNum} tabular`} aria-hidden="true">
        {n}
      </span>
      <div className={styles.stepBody}>
        <p className={styles.stepTitle}>{step.title}</p>
        <p className={styles.stepDesc}>{step.body}</p>
        {step.link && (
          <Link className={styles.stepLink} href={step.link.href}>
            {step.link.label}
          </Link>
        )}
      </div>
    </li>
  );
}
