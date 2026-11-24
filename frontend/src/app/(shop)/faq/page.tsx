import { redirect } from 'next/navigation';

/** `/faq` 로 찾아오는 사람이 많다. 고객센터 아래의 진짜 주소로 보낸다. */
export default function FaqShortcut() {
  redirect('/support/faq');
}
