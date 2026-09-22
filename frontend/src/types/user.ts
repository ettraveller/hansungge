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
  // 카카오 가이드의 국문 레이블은 「카카오 로그인」(완성형)·「로그인」(축약형)뿐이다 — 「카카오로 시작하기」는 없다.
  // 바탕 #FEE500, 레이블 #000000 85%.
  KAKAO: { bg: '#FEE500', fg: 'rgba(0, 0, 0, 0.85)', label: '카카오 로그인' },
  NAVER: { bg: '#03A94D', fg: '#ffffff', label: '네이버 로그인' },
  // 구글 가이드의 한국어 문구는 「Google 계정으로 로그인 / 가입 / 계속」, 「Google로 로그인」뿐이다.
  // 「Google로 시작하기」는 그 목록에 없다. 흰 바탕·#1F1F1F 글자는 가이드의 Light 테마 값이다.
  GOOGLE: { bg: '#FFFFFF', fg: '#1F1F1F', label: 'Google 계정으로 로그인' },
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
   * 휴대폰(자체) 가입을 지금 받는가.
   *
   * <p>자체 가입은 간편인증(PASS·토스 등)을 거친다. 본인인증(이니시스) 계약 전에는 false 다.
   * 소셜 가입은 본인인증이 없어 이 값과 무관하게 된다. 본인인증이 필요한 비밀번호 찾기·
   * 마이페이지 본인인증도 이 값을 따른다.
   */
  signUpAvailable: boolean;
  /** 인증번호(문자) 로그인을 지금 쓸 수 있는가. 문자 발송 설정이 없으면 false. */
  smsLoginAvailable: boolean;
}

export interface OAuthAuthorize {
  authorizeUrl: string;
  /** sessionStorage 에 보관했다가 콜백에서 대조한다 (인가코드 주입 방지). */
  state: string;
}

export interface User {
  id: number;
  email?: string | null;
  /** 본인인증을 마친 회원은 실명(고칠 수 없다). 본인인증 전 소셜 회원은 제공자가 준 이름이거나 없다 */
  name?: string | null;
  /** 로그인 ID. 본인인증으로 받은 번호, 숫자만(01012345678). 본인인증 전 소셜 회원은 없다. 표시는 formatPhone */
  phone?: string | null;
  status: UserStatus;
  createdAt: string;
  /** 비밀번호가 있는가 */
  hasPassword: boolean;
  /** 휴대폰번호+비밀번호로 로그인할 수 있는가. 비밀번호만 있고 번호가 없으면 false */
  canPasswordLogin: boolean;
  /** 본인인증(간편인증)을 마쳤는가. 소셜 가입자는 본인인증 전까지 false */
  identityVerified: boolean;
}

/**
 * 로그인 결과.
 *
 * <b>결말이 두 가지다.</b>
 *  - 로그인 완료: `signUpRequired=false`, `accessToken` 발급됨
 *  - 처음 오는 소셜: `signUpRequired=true`, 토큰 대신 `signUpToken` + 약관 목록.
 *    다음은 약관 동의 — 그것으로 가입이 끝난다(본인인증 없음)
 *
 * 신규일 때 계정은 <b>아직 만들어지지 않았다.</b>
 *
 * Refresh 토큰은 이 본문에 없다 — HttpOnly 쿠키로만 오간다.
 */
export interface SignInResult {
  signUpRequired: boolean;
  accessToken?: string | null;
  expiresIn?: number | null;
  user?: User | null;
  signUpToken?: string | null;
  agreements?: AgreementItem[] | null;
  suggestedEmail?: string | null;
}

/**
 * 본인확인 창을 여는 폼.
 *
 * <p>화면은 이 값을 해석하지 않는다 — hidden 필드로 옮겨 담아 그대로 전송한다. KG이니시스는
 * 인증창을 폼 POST 로 연다.
 */
export interface IdentityStartForm {
  action: string;
  method: 'POST' | 'GET';
  fields: Record<string, string>;
}

/** 본인확인 시작 응답. {@code token} 은 결과 제출 때 그대로 보낸다. */
export interface IdentityStart {
  form: IdentityStartForm;
  token: string;
}

/**
 * 자체 가입 중 간편인증을 마친 뒤 — 다음에 무엇을 할지. 서버가 정한다.
 *
 *  - SET_PASSWORD      새 회원 — 비밀번호와 선택 이메일
 *  - EXISTING_ACCOUNT  이미 가입한 사람 — 로그인하거나 바로 비밀번호 재설정
 */
export type IdentityStep = 'SET_PASSWORD' | 'EXISTING_ACCOUNT';

export interface IdentityResult {
  step: IdentityStep;
  signUpToken?: string | null;
  resetToken?: string | null;
  /** 이미 있는 계정의 이메일(가림) */
  accountEmail?: string | null;
  /** 이미 있는 계정의 휴대폰(가림) */
  accountPhone?: string | null;
  accountHasPassword?: boolean | null;
  /** SET_PASSWORD — 본인확인 이름 (본인 것) */
  name?: string | null;
  /** SET_PASSWORD — 본인확인 휴대폰 (본인 것, 숫자만) */
  phone?: string | null;
}

/** 비밀번호 찾기 — 본인확인 통과. 5분 안에 새 비밀번호를 정한다. */
export interface PasswordResetGrant {
  resetToken: string;
  accountEmail?: string | null;
  accountPhone?: string | null;
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
