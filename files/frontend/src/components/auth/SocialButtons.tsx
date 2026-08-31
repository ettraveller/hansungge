'use client';

import { PROVIDER_STYLE, type AuthProviderItem, type OAuthProviderType } from '@/types/user';
import styles from './auth.module.css';

/**
 * 버튼 앞 글자 마크.
 *
 * <p>카카오만 이모지(💬)였는데 셋을 나란히 두면 하나만 그림이라 크기와 색이 따로 논다.
 * 세 개 다 글자로 맞춘다. 각 사의 공식 로고 이미지는 브랜드 가이드에 따라 앱 등록
 * (TODO A-4)이 끝난 뒤 받아서 교체한다 — 비슷하게 그린 로고를 쓰는 것이 더 문제다.
 */
const MARK: Record<OAuthProviderType, string> = {
  KAKAO: 'K',
  NAVER: 'N',
  GOOGLE: 'G',
};

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
            <span className={styles.socialMark} aria-hidden="true">
              {MARK[provider.code]}
            </span>
            {pending ? '이동 중…' : style.label}
          </button>
        );
      })}
    </div>
  );
}
