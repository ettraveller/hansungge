/**
 * 백엔드 응답 계약.
 *
 * 성공 응답은 DTO 를 그대로 돌려준다 (래핑 없음).
 * 실패 응답만 아래 표준 형태를 갖는다 — 백엔드
 * com.hanseongge.global.error.ErrorResponse 와 1:1 대응한다.
 *
 *   { "code": "PRICE_UNAVAILABLE",
 *     "message": "현재 시세를 확인할 수 없습니다.",
 *     "timestamp": "2026-08-21T14:00:00+09:00" }
 */
export interface ErrorResponse {
  /** 프론트엔드 분기에 사용하는 안정 계약. 예: PRICE_UNAVAILABLE */
  code: string;
  /** 사용자에게 그대로 보여줘도 되는 문구 */
  message: string;
  /** 발생 시각 (ISO-8601, KST) */
  timestamp: string;
  /** 입력값 오류일 때만 채워지는 필드별 메시지 */
  errors?: Record<string, string>;
}

/** 백엔드 ErrorCode 중 화면 분기가 필요한 것들 */
export type KnownErrorCode =
  | 'INVALID_INPUT'
  | 'UNAUTHORIZED'
  | 'FORBIDDEN'
  | 'NOT_FOUND'
  | 'INTERNAL_ERROR'
  | 'SETTING_NOT_FOUND'
  | 'PRICE_UNAVAILABLE'
  | 'PRICE_CHANGE_NEEDS_CONFIRM'
  | 'PRICE_SPREAD_INVERTED'
  | 'PRICE_METAL_DERIVED'
  | 'PRICE_CHANGED'
  | 'PRODUCT_NOT_FOUND'
  | 'PRODUCT_NOT_ON_SALE'
  | 'OUT_OF_STOCK'
  | 'PAYMENT_AMOUNT_MISMATCH'
  | 'TOKEN_EXPIRED'
  // 가입 · 본인확인 · 로그인
  | 'SIGNUP_UNAVAILABLE'
  | 'SIGNUP_TOKEN_INVALID'
  | 'AGREEMENT_REQUIRED'
  | 'VERIFICATION_CODE_INVALID'
  | 'VERIFICATION_ATTEMPTS_EXCEEDED'
  | 'EMAIL_OWNED_BY_ANOTHER'
  | 'IDENTITY_PROVIDER_UNAVAILABLE'
  | 'IDENTITY_VERIFICATION_FAILED'
  | 'IDENTITY_VERIFICATION_INVALID'
  | 'IDENTITY_ALREADY_REGISTERED'
  | 'IDENTITY_NOT_REGISTERED'
  | 'UNDER_MINIMUM_AGE'
  | 'PASSWORD_NOT_SET'
  | 'PASSWORD_RESET_TOKEN_INVALID'
  | 'CURRENT_PASSWORD_MISMATCH'
  | 'USER_ACCOUNT_LOCKED'
  | 'LOGIN_FAILED'
  | 'MAIL_SEND_FAILED'
  | 'SMS_UNAVAILABLE'
  | 'SMS_SEND_FAILED'
  | 'SOCIAL_ALREADY_LINKED'
  | 'TOO_MANY_REQUESTS';

/** API 호출 실패를 표현하는 예외. 화면은 code 로 분기한다. */
export class ApiError extends Error {
  constructor(
    readonly code: string,
    message: string,
    readonly status: number,
    readonly timestamp?: string,
    readonly fieldErrors?: Record<string, string>,
  ) {
    super(message);
    this.name = 'ApiError';
  }

  is(code: KnownErrorCode): boolean {
    return this.code === code;
  }
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}
