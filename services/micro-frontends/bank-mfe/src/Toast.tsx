import { useEffect, useState } from 'react';
import { overlay } from 'overlay-kit';
import { Toast } from '@itunda/design-system-web';

let activeToastId: string | null = null;

function ToastPill({
  message,
  actionLabel,
  onAction,
  isOpen,
  close,
}: {
  message: string;
  actionLabel?: string;
  onAction?: () => void;
  isOpen: boolean;
  close: () => void;
}) {
  const [entered, setEntered] = useState(false);
  useEffect(() => {
    const frame = requestAnimationFrame(() => setEntered(true));
    const timeout = setTimeout(close, actionLabel ? 5000 : 3000);
    return () => {
      cancelAnimationFrame(frame);
      clearTimeout(timeout);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className={`itunda-toast-host${isOpen && entered ? ' is-visible' : ''}`}>
      <Toast
        message={message}
        actionLabel={actionLabel}
        onAction={() => {
          onAction?.();
          close();
        }}
      />
    </div>
  );
}

export function showToast(message: string, options?: { actionLabel?: string; onAction?: () => void }) {
  if (activeToastId) {
    overlay.unmount(activeToastId);
  }
  activeToastId = overlay.open(({ isOpen, close, unmount }) => (
    <ToastPill
      message={message}
      actionLabel={options?.actionLabel}
      onAction={options?.onAction}
      isOpen={isOpen}
      close={() => {
        close();
        setTimeout(unmount, 220);
      }}
    />
  ));
}
