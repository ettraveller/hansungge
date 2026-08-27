'use client';

import { PROVIDER_STYLE, type AuthProviderItem, type OAuthProviderType } from '@/types/user';
import styles from './auth.module.css';

const MARK: Record<OAuthProviderType, string> = {
  KAKAO: '💬',
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
