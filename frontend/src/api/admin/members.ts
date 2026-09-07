import { adminApi } from '@/api/admin/client';
import type { PageResponse } from '@/types/api';
import type {
  AdminMemberDetail,
  AdminMemberSummary,
  MemberSearch,
  MemberStatus,
} from '@/types/member';

/**
 * 회원 관리 API.
 *
 * <h2>목록과 상세가 다른 것을 준다</h2>
 * 목록은 이메일·이름·휴대폰이 <b>가려진 채로</b> 오고, 상세는 원본이 온다. 대신 상세
 * 조회는 서버에서 감사로그에 남고 더 높은 권한을 요구한다.
 *
 * <p>그래서 화면은 "목록에서 대충 훑고, 꼭 필요할 때만 상세를 연다"가 된다 — 그것이
 * 의도한 사용 방식이다.
 */

export function listMembers(search: MemberSearch) {
  return adminApi.get<PageResponse<AdminMemberSummary>>('/api/admin/members', {
    query: {
      keyword: search.keyword?.trim() || undefined,
      status: search.status ?? undefined,
      page: search.page ?? 0,
      size: 20,
    },
  });
}

/** 상태별 회원 수. 목록 위 요약 줄에 쓴다. */
export function memberCounts() {
  return adminApi.get<Record<MemberStatus, number>>('/api/admin/members/counts');
}

/** ⚠️ 개인정보 원본이 온다. 이 호출은 감사로그에 남는다 (MANAGER 이상). */
export function memberDetail(id: number) {
  return adminApi.get<AdminMemberDetail>(`/api/admin/members/${id}`);
}

/**
 * 이용정지 · 정지 해제 · 휴면 해제 (ADMIN 이상).
 *
 * <p>`ACTIVE` 로 되돌리면 서버가 로그인 잠금도 함께 푼다 — 풀어 줬는데 로그인이 안 되는
 * 상태를 만들지 않기 위해서다.
 */
export function changeMemberStatus(id: number, status: MemberStatus, reason: string) {
  return adminApi.patch<AdminMemberDetail>(`/api/admin/members/${id}/status`, {
    body: { status, reason },
  });
}

/**
 * 로그인 실패 잠금 해제 (MANAGER 이상).
 *
 * <p>이용정지와 <b>다르다.</b> 잠금은 상태가 정상인 채로 걸리고 30분 뒤 스스로 풀린다.
 * 이 호출은 그 30분을 기다리지 않게 해 줄 뿐이고, 정지된 회원을 되살리지는 않는다.
 */
export function unlockMember(id: number, reason: string) {
  return adminApi.patch<AdminMemberDetail>(`/api/admin/members/${id}/unlock`, {
    body: { reason },
  });
}
