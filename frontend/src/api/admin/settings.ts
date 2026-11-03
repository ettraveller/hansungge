import { adminApi } from '@/api/admin/client';

/**
 * 무통장입금 계좌 설정.
 *
 * <p>시스템 설정 전체를 다루지 않는다. 그 표에는 시세 자동적용 허용 변동률처럼
 * 잘못 건드리면 값이 조용히 틀어지는 값이 섞여 있어, 계좌를 고치러 들어온 사람이
 * 시세 안전망을 끌 수 있게 된다. 필요한 네 칸만 연다.
 */
export interface BankAccountSetting {
  bank: string;
  number: string;
  holder: string;
  /** 입금 안내 기한(시간). 안내용이며 자동취소하지 않는다 */
  depositDueHours: number;
}

export function getBankAccount() {
  return adminApi.get<BankAccountSetting>('/api/admin/settings/bank-account');
}

/** 셋 중 하나라도 비우면 무통장입금이 꺼진다 — 계좌를 내리는 길이 그것이다. */
export function saveBankAccount(body: BankAccountSetting) {
  return adminApi.put<{ bank: string; number: string; holder: string }>(
    '/api/admin/settings/bank-account',
    { body },
  );
}
