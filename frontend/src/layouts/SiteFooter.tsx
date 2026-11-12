import Image from 'next/image';
import Link from 'next/link';

import type { ReactNode } from 'react';

import type { FooterInfo } from '@/api/footer';
import styles from './ShopLayout.module.css';

interface Props {
  info: FooterInfo;
  /**
   * 글자 한 칸을 대신 그린다.
   *
   * <p>관리자 화면이 이 자리에 <b>그 자리에서 고치는 입력칸</b>을 꽂는다. 손님 화면은
   * 넘기지 않으므로 글자 그대로 나간다.
   *
   * <p>편집용 푸터를 따로 만들지 않는 이유 — 모양을 두 벌로 두면 바탕색·줄 간격·
   * 줄바꿈이 조금씩 달라지고, 결국 "고칠 때 본 모양"과 "손님이 보는 모양"이 달라진다.
   * 고치는 자리가 곧 손님이 보는 자리여야 한다.
   */
  renderText?: (name: keyof FooterInfo, value: string) => ReactNode;
}

/**
 * 쇼핑몰 푸터.
 *
 * <h2>글자는 관리자가 고친다</h2>
 * 상호·주소·대표전화·운영시간은 {@code GET /api/v1/footer} 가 준다 (관리자 › 메인
 * 배너 & 푸터). 주소나 영업시간은 가게 사정으로 바뀌는 값이라, 바꿀 때 배포가
 * 필요하면 안 된다. 기본값은 {@code data/company.ts} 에 있다.
 *
 * <p>⚠️ 사업장 소재지는 개인정보처리방침 §13 · 이용약관과 한 쌍이다. 이 칸만 고치면
 * 같은 회사 주소가 화면마다 달라진다.
 *
 * <h2>왜 레이아웃에서 떼어 놓았는가</h2>
 * 배너 미리보기가 저장하지 않은 푸터까지 보여 줘야 한다. 레이아웃은 쿼리를 읽지
 * 못하므로, 미리보기는 레이아웃 밖에서 이 컴포넌트에 <b>고치는 중인 값</b>을 넣어
 * 그린다 — 손님이 보는 것과 같은 코드다.
 */
export function SiteFooter({ info, renderText }: Props) {
  /** 글자 한 칸. 고치는 중이면 그 자리를 편집기에 내준다. */
  const t = (name: keyof FooterInfo) =>
    renderText ? renderText(name, info[name]) : info[name];

  /** 비어 있으면 손님 화면에서는 줄을 그리지 않지만, 고치는 중에는 그려야 적을 수 있다. */
  const show = (name: keyof FooterInfo) => Boolean(renderText) || Boolean(info[name]);

  return (
  <footer className={styles.footer}>
    <div className={styles.footTop}>
      <div className="wrap">
        <Link href="/about">회사소개</Link>
        <Link href="/terms">이용약관</Link>
        <Link href="/privacy">개인정보처리방침</Link>
        <Link href="/guide">이용안내</Link>
        <Link href="/refund">교환·반품·환불</Link>
        <Link href="/store">지점안내</Link>
      </div>
    </div>

    <div className={styles.footMain}>
      <div className="wrap">
        <div className={styles.footInfo}>
          {/* 푸터는 검정 바탕이다. 여기가 logo-dark(밝은 글자)를 쓸 자리다 */}
          <Image
            src="/logo-dark.svg"
            alt="한성금거래소"
            width={560}
            height={104}
            className={styles.footLogo}
          />
          {/* 글자는 관리자 › 메인 배너 & 푸터에서 고친다. 기본값은 data/company.ts.

              상호는 법인명(주식회사 금한성)이다. 로고의 「한성금거래소」는 브랜드명이라
              법적 표시가 되지 않는다 — 전자상거래법상 표시해야 하는 것은 상호다.

              ⚠️ 사업장 소재지는 개인정보처리방침 §13 · 이용약관과 한 쌍이다. 이 칸만
              고치면 같은 회사 주소가 화면마다 달라진다. */}
          <p>
            상호 : {t('companyName')} &nbsp;|&nbsp; 대표자 : {t('ceo')}
          </p>
          <p>사업자등록번호 : {t('bizNo')}</p>
          {/* 통신판매업신고번호는 아직 받지 못했다 (TODO A-12). 비어 있으면 줄 자체를
              그리지 않는다 — 고객이 읽는 자리에 틀린 신고번호를 두는 것이 비워 두는
              것보다 나쁘다. 받는 날 관리자 화면에 적으면 되고, 배포가 필요 없다. */}
          {show('mailOrderNo') && <p>통신판매업신고번호 : {t('mailOrderNo')}</p>}
          <p>본사 : {t('address')}</p>
          <p>이메일 : {t('email')}</p>
          {show('tagline') && <p className={styles.footNote}>{t('tagline')}</p>}
        </div>

        <div className={styles.footCs}>
          <p className={styles.csLabel}>대표전화</p>
          <p>{t('tel')}</p>
          <p className={styles.csLabel}>운영시간</p>
          <p>{t('hours')}</p>
          {show('holiday') && <p>{t('holiday')}</p>}
        </div>
      </div>
    </div>

    <div className={styles.copyright}>
      COPYRIGHT © HANSEONG GOLD EXCHANGE. ALL RIGHTS RESERVED.
    </div>
  </footer>
  );
}
