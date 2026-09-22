'use client';

import Image from 'next/image';

import { PROVIDER_STYLE, type AuthProviderItem, type OAuthProviderType } from '@/types/user';
import styles from './auth.module.css';

/**
 * 버튼 앞 마크.
 *
 * <p><b>네이버는 공식 로고를 쓴다.</b> 네이버 로그인 버튼 가이드가 로고의 변형·조합을
 * 금지하고, 검수에서 가장 많이 반려되는 지점이다. 그래서 글자로 흉내 내지 않고, 네이버가
 * 배포한 완성형 버튼(NAVER_login_Light_KR_green_center_H48.png)에서 N 로고 영역만 잘라
 * 흰색 투명 PNG 로 옮겼다 — 모양은 원본 픽셀 그대로다.
 * 출처: https://developers.naver.com/docs/login/bi/bi.md
 *
 * <p>잘라낸 이미지는 로고(64px) 둘레에 2px 씩 여유가 있어 68px 이다. 17px 로 그리면 로고가
 * 정확히 16px 이 된다 — 가이드의 완성형 최소 크기다. 로고와 문구 사이 8px 은
 * {@code .socialBtn} 의 gap 이 맞춘다.
 *
 * <p><b>구글도 공식 로고를 쓴다.</b> 구글 로그인 브랜드 가이드는 "단색 G 를 쓰지 말 것",
 * "직접 만든 아이콘을 쓰지 말 것", "G 의 크기·색을 바꾸지 말 것"을 명시한다 — 전에 쓰던
 * 회색 글자 'G' 는 셋 다 어겼다. 공식 에셋(signin-assets.zip)의 SVG 는 그라데이션 G 를
 * foreignObject·블러 필터로 그려서 <img> 로 불러오면 브라우저에 따라 빈 칸이 된다. 그래서
 * 같은 묶음의 @4x PNG(Light, 텍스트 없음)에서 로고 칸 80px 을 그대로 잘라 20px 로 그린다.
 * 20px 은 공식 SVG 의 로고 크기다. 흰 바탕째 잘랐는데, 버튼 바탕도 같은 흰색이라 경계가 없다.
 * 출처: https://developers.google.com/identity/branding-guidelines
 *
 * <p><b>카카오도 공식 심볼을 쓴다.</b> 카카오 로그인 디자인 가이드: "심볼 없이 카카오 로그인 버튼을 구성할 수
 * 없습니다", 심볼의 형태·비율·색 변경 불가, 심볼 #000000. 공식 리소스(login-complete-ko.svg)의 말풍선 경로를
 * 그대로 옮겨 여백 없이 잘랐다 — 전에 쓰던 글자 'K' 는 가이드 위반이었다.
 * 출처: https://developers.kakao.com/docs/ko/kakaologin/design-guide
 */
function Mark({ code }: { code: OAuthProviderType }) {
  if (code === 'NAVER') {
    return <Image src="/brand/naver/naver-n-white.png" alt="" width={17} height={17} />;
  }
  if (code === 'GOOGLE') {
    return <Image src="/brand/google/google-g.png" alt="" width={20} height={20} />;
  }
  return <Image src="/brand/kakao/kakao-symbol.svg" alt="" width={18} height={18} />;
}

/**
 * 소셜 로그인 버튼.
 *
 * <p>목록은 <b>서버가 준 것만</b> 그린다. 화면에 세 개를 하드코딩하면 앱 검수가 끝나지
 * 않은 제공자 버튼이 눌리고, 사용자는 이유를 알 수 없는 실패 화면을 본다.
 */
export function SocialButtons({
  providers,
  pendingProvider,
  onSelect,
}: {
  providers: AuthProviderItem[];
  pendingProvider: OAuthProviderType | null;
  onSelect: (provider: OAuthProviderType) => void;
}) {
  return (
    <div className={styles.socialList}>
      {providers.map((provider) => {
        const style = PROVIDER_STYLE[provider.code];
        const pending = pendingProvider === provider.code;
        return (
          <button
            key={provider.code}
            type="button"
            className={`${styles.socialBtn} ${provider.code === 'GOOGLE' ? styles.socialOutlined : ''}`}
            style={{ background: style.bg, color: style.fg }}
            disabled={pendingProvider != null}
            onClick={() => onSelect(provider.code)}
          >
            <Mark code={provider.code} />
            {pending ? '이동 중…' : style.label}
          </button>
        );
      })}
    </div>
  );
}
