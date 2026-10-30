import { useEffect, useId, useRef, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';

const FOCUSABLE =
  'a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])';

const SIZES = { sm: 'max-w-md', md: 'max-w-lg', lg: 'max-w-2xl' } as const;

interface ModalProps {
  open: boolean;
  title: string;
  description?: string;
  onClose: () => void;
  children: ReactNode;
  /** Buttons for the sticky footer. A submit button can target a form in the body via its `form` attribute. */
  footer?: ReactNode;
  size?: keyof typeof SIZES;
  /** While true (a request is in flight) the dialog cannot be dismissed, so nothing is abandoned half-way. */
  busy?: boolean;
  /** Off by default: a stray click outside must not discard a half-typed form. */
  closeOnBackdrop?: boolean;
}

/**
 * Accessible modal dialog: role="dialog" + aria-modal, labelled and described, focus moves in on open, Tab is
 * trapped inside, Escape closes (unless busy), page scroll is locked, and focus returns to the trigger on close.
 * Mark an element with `data-autofocus` to choose which control receives initial focus.
 */
export function Modal({
  open,
  title,
  description,
  onClose,
  children,
  footer,
  size = 'md',
  busy = false,
  closeOnBackdrop = false,
}: ModalProps) {
  const titleId = useId();
  const descriptionId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);
  const onCloseRef = useRef(onClose);
  const busyRef = useRef(busy);

  useEffect(() => {
    onCloseRef.current = onClose;
    busyRef.current = busy;
  });

  useEffect(() => {
    if (!open) {
      return undefined;
    }
    const dialog = dialogRef.current;
    const previouslyFocused = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    const focusables = (): HTMLElement[] =>
      dialog ? Array.from(dialog.querySelectorAll<HTMLElement>(FOCUSABLE)) : [];

    const initial =
      dialog?.querySelector<HTMLElement>('[data-autofocus]') ??
      focusables().find((element) => element.dataset.modalClose === undefined) ??
      dialog;
    initial?.focus();

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        if (!busyRef.current) {
          event.stopPropagation();
          onCloseRef.current();
        }
        return;
      }
      if (event.key !== 'Tab') {
        return;
      }
      const items = focusables();
      if (items.length === 0) {
        event.preventDefault();
        dialog?.focus();
        return;
      }
      const first = items[0];
      const last = items[items.length - 1];
      const active = document.activeElement;
      if (event.shiftKey && (active === first || active === dialog)) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && active === last) {
        event.preventDefault();
        first.focus();
      } else if (dialog && active instanceof Node && !dialog.contains(active)) {
        event.preventDefault();
        first.focus();
      }
    };

    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      document.body.style.overflow = previousOverflow;
      previouslyFocused?.focus();
    };
  }, [open]);

  if (!open) {
    return null;
  }

  return createPortal(
    <div
      className="fixed inset-0 z-40 flex animate-fade-in items-start justify-center overflow-y-auto bg-midnight/60 p-4 sm:items-center"
      onMouseDown={(event) => {
        if (closeOnBackdrop && !busy && event.target === event.currentTarget) {
          onClose();
        }
      }}
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={description ? descriptionId : undefined}
        tabIndex={-1}
        className={`relative my-8 flex max-h-[90vh] w-full flex-col rounded-lg bg-white shadow-pop focus:outline-none ${SIZES[size]}`}
      >
        <div className="flex items-start justify-between gap-4 border-b border-line px-5 py-4">
          <div>
            <h2 id={titleId} className="text-base font-semibold text-ink">
              {title}
            </h2>
            {description && (
              <p id={descriptionId} className="mt-1 text-xs text-slate-600">
                {description}
              </p>
            )}
          </div>
          <button
            type="button"
            data-modal-close
            className="btn-icon -mr-2 -mt-1"
            onClick={onClose}
            disabled={busy}
            aria-label="Close dialog"
          >
            <X className="h-4 w-4" aria-hidden />
          </button>
        </div>
        <div className="overflow-y-auto px-5 py-4">{children}</div>
        {footer && (
          <div className="flex justify-end gap-2 rounded-b-lg border-t border-line bg-surface px-5 py-3">{footer}</div>
        )}
      </div>
    </div>,
    document.body,
  );
}
