import styles from './admin.module.css';

type Variant = 'default' | 'primary' | 'gold' | 'danger';

const VARIANT_CLASS: Record<Variant, string> = {
  default: '',
  primary: styles.btnPrimary,
  gold: styles.btnGold,
  danger: styles.btnDanger,
};

export function Button({
  variant = 'default',
  small,
  className,
  type = 'button',
  children,
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; small?: boolean }) {
  return (
    <button
      {...props}
      type={type}
      className={[styles.btn, VARIANT_CLASS[variant], small ? styles.btnSm : '', className]
        .filter(Boolean)
        .join(' ')}
    >
      {children}
    </button>
  );
}
