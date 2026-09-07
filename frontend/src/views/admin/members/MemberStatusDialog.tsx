'use client';

import { useState } from 'react';

import { Field, SelectInput } from '@/components/admin/Field';
import { ReasonDialog } from '@/components/admin/ReasonDialog';
import { useChangeMemberStatus } from '@/hooks/admin/useMembers';
import { ApiError } from '@/types/api';
import {
  MEMBER_ASSIGNABLE_STATUS,
  MEMBER_STATUS_LABEL,
  type AdminMemberSummary,
  type MemberStatus,
} from '@/types/member';

const EFFECT: Record<MemberStatus, string> = {
  ACTIVE: '로그인과 주문이 다시 가능해집니다. 로그인 잠금도 함께 풀립니다.',
  BLOCKED: '로그인이 막히고, 회원에게는 이용이 제한되었다는 안내가 보입니다.',
  DORMANT: '로그인이 막힙니다. 휴면 해제 절차를 거쳐야 다시 이용할 수 있습니다.',
  WITHDRAWN: '',
};

/**
 * 회원 상태 변경 — 이용정지 · 정지 해제 · 휴면 해제.
 *
 * <h2>탈퇴는 고를 수 없다</h2>
 * 탈퇴는 본인의 의사표시라 관리자가 대신 하지 않는다. 이용을 막아야 하면 정지가 맞고,
 * 서버도 탈퇴 지정을 거절한다.
 *
 * <h2>무슨 일이 일어나는지 먼저 보여준다</h2>
 * 「BLOCKED」라는 단어만으로는 고객이 무엇을 못 하게 되는지 알 수 없다. 고르는 순간
 * 그 결과를 문장으로 보여줘야, 정지시킬 생각이 없었던 사람이 멈출 수 있다.
 */
export function MemberStatusDialog({
  member,
  onClose,
}: {
  member: AdminMemberSummary;
  onClose: () => void;
}) {
  const change = useChangeMemberStatus();

  // 정지된 회원을 열었으면 되돌리려는 것이고, 정상 회원을 열었으면 막으려는 것이다.
  // 가장 흔한 의도를 미리 골라 둔다.
  const [status, setStatus] = useState<MemberStatus>(
    member.status === 'ACTIVE' ? 'BLOCKED' : 'ACTIVE',
  );

  const dangerous = status === 'BLOCKED' || status === 'DORMANT';

  return (
    <ReasonDialog
      open
      title={`상태 변경 · ${member.name}`}
      confirmLabel="변경"
      danger={dangerous}
      placeholder="예: 허위 주문 반복으로 이용정지"
      pending={change.isPending}
      error={change.error instanceof ApiError ? change.error.message : null}
      description={
        <>
          현재 상태는 <strong>{member.statusLabel}</strong> 이다.
          <div style={{ marginTop: 12 }}>
            <Field label="변경할 상태" htmlFor="member-status">
              <SelectInput
                id="member-status"
                value={status}
                onChange={(e) => setStatus(e.target.value as MemberStatus)}
              >
                {MEMBER_ASSIGNABLE_STATUS.map((s) => (
                  <option key={s} value={s}>
                    {MEMBER_STATUS_LABEL[s]}
                  </option>
                ))}
              </SelectInput>
            </Field>
          </div>
          {EFFECT[status]}
        </>
      }
      onConfirm={(reason) =>
        change.mutate({ id: member.id, status, reason }, { onSuccess: onClose })
      }
      onClose={onClose}
    />
  );
}
