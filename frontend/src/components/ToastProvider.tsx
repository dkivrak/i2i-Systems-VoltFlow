import { AlertTriangle, CheckCircle2, Info, X } from 'lucide-react';
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type PropsWithChildren,
} from 'react';

type ToastTone = 'success' | 'error' | 'info';

interface ToastInput {
  title: string;
  message?: string;
  tone?: ToastTone;
}

interface ToastItem extends ToastInput {
  id: number;
  tone: ToastTone;
}

interface ToastContextValue {
  showToast: (toast: ToastInput) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

export function ToastProvider({ children }: PropsWithChildren) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const nextId = useRef(1);
  const timers = useRef(new Map<number, number>());

  const dismiss = useCallback((id: number) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
    const timer = timers.current.get(id);
    if (timer !== undefined) window.clearTimeout(timer);
    timers.current.delete(id);
  }, []);

  const showToast = useCallback(
    (toast: ToastInput) => {
      const id = nextId.current++;
      const item: ToastItem = { ...toast, id, tone: toast.tone ?? 'info' };
      setToasts((current) => [...current.slice(-2), item]);
      timers.current.set(id, window.setTimeout(() => dismiss(id), 4500));
    },
    [dismiss],
  );

  useEffect(
    () => () => {
      timers.current.forEach((timer) => window.clearTimeout(timer));
      timers.current.clear();
    },
    [],
  );

  const contextValue = useMemo(() => ({ showToast }), [showToast]);
  return (
    <ToastContext.Provider value={contextValue}>
      {children}
      <div className="toast-region" aria-live="polite" aria-label="Bildirimler">
        {toasts.map((toast) => {
          const Icon = toast.tone === 'success' ? CheckCircle2 : toast.tone === 'error' ? AlertTriangle : Info;
          return (
            <div className={`toast toast--${toast.tone}`} role="status" key={toast.id}>
              <Icon aria-hidden="true" size={19} />
              <div className="toast__copy">
                <strong>{toast.title}</strong>
                {toast.message && <span>{toast.message}</span>}
              </div>
              <button
                className="icon-button icon-button--small"
                type="button"
                onClick={() => dismiss(toast.id)}
                aria-label="Bildirimi kapat"
              >
                <X aria-hidden="true" size={16} />
              </button>
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastContextValue {
  const context = useContext(ToastContext);
  if (!context) throw new Error('useToast must be used within ToastProvider');
  return context;
}
