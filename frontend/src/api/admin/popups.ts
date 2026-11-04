import { adminApi } from '@/api/admin/client';
import type { AdminPopup, PopupInput } from '@/types/popup';

/**
 * 관리자 팝업.
 *
 * <p>페이지를 나누지 않는다 — 팝업은 많아야 서너 개이고, 차례(sort)를 보며 손보는
 * 화면이라 전부 한눈에 보이는 편이 낫다. 2페이지로 넘어간 팝업은 잊혀진다.
 */
export function listPopups() {
  return adminApi.get<AdminPopup[]>('/api/admin/popups');
}

export function createPopup(body: PopupInput) {
  return adminApi.post<AdminPopup>('/api/admin/popups', { body });
}

export function updatePopup(id: number, body: PopupInput) {
  return adminApi.put<AdminPopup>(`/api/admin/popups/${id}`, { body });
}

/** 기간을 지우지 않고 잠시 내릴 때. 지우는 것보다 먼저 권한다 */
export function setPopupActive(id: number, active: boolean) {
  return adminApi.put<AdminPopup>(`/api/admin/popups/${id}/active`, { query: { active } });
}

export function deletePopup(id: number) {
  return adminApi.delete<void>(`/api/admin/popups/${id}`);
}
