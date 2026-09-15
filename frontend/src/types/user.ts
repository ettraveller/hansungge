/**
 * 회원 인증 타입. 백엔드 DTO 와 1:1 대응한다.
 *
 * ⚠️ 백엔드는 {@code non_null} 직렬화라 null 필드를 응답에서 생략한다.
 * {@code | null} 로 적힌 값은 런타임에 {@code undefined} 로 도착할 수 있으므로
 * 비교는 {@code == null} (느슨한 비교)로 한다.
 */

export type OAuthProviderType = 'GOOGLE' | 'NAVER' | 'KAKAO';

export type UserStatus = 'ACTIVE' | 'DORMANT' | 'WITHDRAWN' | 'BLOCKED';

export type AgreementCode = 'TERMS' | 'PRIVACY' | 'AGE_14' | 'MARKETING' | 'THIRD_PARTY';

export const USER_STATUS_LABEL: Record<UserStatus, string> = {
  ACTIVE: '정상',
  DORMANT: '휴면',
  WITHDRAWN: '탈퇴',
  BLOCKED: '이용정지',
};

/**
 * 제공자별 버튼 색. 각 사의 브랜드 가이드를 따른다.
 *
 * <p>네이버 색은 네이버가 배포한 공식 버튼 이미지(NAVER_login_Light_KR_green_*)의 픽셀에서
 * 뽑은 값이다. 흔히 알려진 브랜드 녹색 #03C75A 가 아니다 — 로그인 버튼 가이드는 "지정
 * 컬러는 변경할 수 없다"고 하고, 지정 컬러가 곧 그 에셋의 색이다. 밝은 바탕용(Light)을
 * 쓴다. 어두운 바탕용(Dark)은 #05AC4F 로 다르다.
 */
export const PROVIDER_STYLE: Record<OAuthProviderType, { bg: string; fg: string; label: string }> = {
  KAKAO: { bg: '#FEE500', fg: '#191600', label: '카카오로 시작하기' },
  NAVER: { bg: '#03A94D', fg: '#ffffff', label: '네이버 로그인' },
  GOOGLE: { bg: '#ffffff', fg: '#1f1f1f', label: 'Google로 시작하기' },
};

export interface AuthProviderItem {
  code: OAuthProviderType;
  name: string;
}

export interface AgreementItem {
  code: AgreementCode;
  name: string;
  required: boolean;
  version: string;
}

/** `GET /api/v1/auth/providers` — 로그인 화면 구성. */
export interface AuthProviders {
  /** 지금 실제로 쓸 수 있는 제공자만 온다. 화면에 버튼을 하드코딩하지 않는 이유다. */
  providers: AuthProviderItem[];
  agreements: AgreementItem[];
  /**
   * 이메일+비밀번호 가입을 지금 쓸 수 있는가.
   *
   * <p>자체 가입은 휴대폰 본인확인이 있어야 성립한다 — 이름도 생년월일도 그것으로만
   * 온다. 공급자 계약 전에는 false 이고, 그때 버튼을 그리면 <b>눌러야만 실패를 아는</b>
   * 화면이 된다. 제공자 목록을 서버가 알려주는 것과 같은 이유다.
   */
  emailSignUpAvailable: boolean;
}

export interface OAuthAuthorize {
  authorizeUrl: string;
  /** sessionStorage 에 보관했다가 콜백에서 대조한다 (인가코드 주입 방지). */
  state: string;
}

export interface User {
  id: number;
  email?: string | null;
  /** 본인확인 실명. 회원이 고칠 수 없다 */
  name?: string | null;
  /** 본인확인으로 확정된 번호. 회원이 고칠 수 없다 */
  phone?: string | null;
  status: UserStatus;
  createdAt: string;
}

/**
 * 로그인 결과.
 *
 * <b>결말이 두 가지다.</b>
 *  - 기존 회원: `signUpRequired=false`, `accessToken` 발급됨
 *  - 신규:      `signUpRequired=true`, 토큰 대신 `signUpToken` + 약관 목록
 *
 * 신규일 때 계정은 <b>아직 만들어지지 않았다.</b> 약관 동의를 마쳐야 생성된다.
 *
 * Refresh 토큰은 이 본문에 없다 — HttpOnly 쿠키로만 오간다.
 */
export interface SignInResult {
  signUpRequired: boolean;
  accessToken?: string | null;
  expiresIn?: number | null;
  user?: User | null;
  signUpToken?: string | null;
  /**
   * 휴대폰 본인확인이 아직 남았는가.
   *
   * <b>signUpRequired 와 다르다.</b> 화면이 갈 곳이 다르기 때문이다 —
   * 하나는 약관 화면, 하나는 본인확인 창이다. 한 값으로 뭉치면 프론트가 순서를
   * 스스로 추측해야 하고, 그 추측은 서버 정책이 바뀌면 조용히 어긋난다.
   */
  identityRequired?: boolean | null;
  agreements?: AgreementItem[] | null;
  suggestedName?: string | null;
  suggestedEmail?: string | null;
}

export interface AgreementStatus {
  type: AgreementCode;
  name: string;
  required: boolean;
  agreed: boolean;
  version: string;
  agreedAt?: string | null;
}

export interface SocialAccount {
  provider: OAuthProviderType;
  providerName: string;
  email?: string | null;
  connectedAt: string;
}
