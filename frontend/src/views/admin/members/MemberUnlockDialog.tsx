'use client';

import { ReasonDialog } from '@/components/admin/ReasonDialog';
import { useUnlockMember } from '@/hooks/admin/useMembers';
import { ApiError } from '@/types/api';
import type { AdminMemberSummary } from '@/types/member';

/**
 * 로그인 잠금 해제.
 *
 * <p>이용정지와 <b>다르다.</b> 잠금은 연속 로그인 실패로 자동으로 걸리고 30분 뒤 스스로
 * 풀리며, 그동안에도 상태는 「정상」이다. 이 창은 그 30분을 기다리지 않게 해 줄 뿐이라
 * 되돌릴 수 없는 조치가 아니다 — 그래서 위험 표시({@code danger})를 하지 않는다.
 *
 * <p>그래도 사유는 받는다. 계정을 열어 준 뒤 그 계정에서 사고가 나면 "누가 왜 열었나"가
 * 첫 질문이고, 그 답이 감사로그에 없으면 기록이 있으나 마나다.
 */
export function MemberUnlockDialog({
  member,
  onClose,
}: {
  member: AdminMemberSummary;
  onClose: () => void;
}) {
  const unlock = useUnlockMember();

  return (
    <ReasonDialog
      open
      title={`잠금 해제 · ${member.name}`}
      confirmLabel="잠금 해제"
      placeholder="예: 고객센터 유선 본인 확인 후 해제"
      pending={unlock.isPending}
      error={unlock.error instanceof ApiError ? unlock.error.message : null}
      description={
        <>
          연속 로그인 실패 {member.loginFailCount}회로 잠긴 계정이다. 실패 횟수를 0 으로
          되돌리고 즉시 로그인할 수 있게 한다.
          <br />
          <br />
          이용정지와는 별개다 — 이 조치는 회원 상태를 바꾸지 않는다.
        </>
      }
      onConfirm={(reason) => unlock.mutate({ id: member.id, reason }, { onSuccess: onClose })}
      onClose={onClose}
    />
  );
}
