import { useCallback, useEffect, useRef, useState } from 'react';

interface PollingState<T> {
  data: T | undefined;
  error: unknown;
  isLoading: boolean;
  isRefreshing: boolean;
  lastUpdatedAt?: Date;
}

interface PollingResource<T> extends PollingState<T> {
  retry: () => void;
}

function structurallyEqual<T>(left: T | undefined, right: T): boolean {
  if (left === undefined) return false;
  try {
    return JSON.stringify(left) === JSON.stringify(right);
  } catch {
    return Object.is(left, right);
  }
}

export function usePollingResource<T>(
  request: (signal: AbortSignal) => Promise<T>,
  intervalMs: number,
  enabled = true,
  initialData?: T,
): PollingResource<T> {
  const [retryKey, setRetryKey] = useState(0);
  const [state, setState] = useState<PollingState<T>>({
    data: initialData,
    error: undefined,
    isLoading: enabled && initialData === undefined,
    isRefreshing: false,
  });
  const runIdRef = useRef(0);

  useEffect(() => {
    if (!enabled) {
      setState((current) => ({ ...current, isLoading: false, isRefreshing: false }));
      return undefined;
    }

    let active = true;
    let timerId: number | undefined;
    let controller: AbortController | undefined;
    const runId = ++runIdRef.current;

    const poll = async () => {
      controller?.abort();
      controller = new AbortController();
      setState((current) => ({
        ...current,
        isLoading: current.data === undefined,
        isRefreshing: current.data !== undefined,
      }));

      try {
        const nextData = await request(controller.signal);
        if (!active || runIdRef.current !== runId) return;
        setState((current) => ({
          data: structurallyEqual(current.data, nextData) ? current.data : nextData,
          error: undefined,
          isLoading: false,
          isRefreshing: false,
          lastUpdatedAt: new Date(),
        }));
      } catch (error) {
        if (!active || runIdRef.current !== runId || controller.signal.aborted) return;
        setState((current) => ({
          ...current,
          error,
          isLoading: false,
          isRefreshing: false,
        }));
      } finally {
        if (active && runIdRef.current === runId) {
          timerId = window.setTimeout(poll, intervalMs);
        }
      }
    };

    void poll();
    return () => {
      active = false;
      if (timerId !== undefined) window.clearTimeout(timerId);
      controller?.abort();
    };
  }, [enabled, intervalMs, request, retryKey]);

  const retry = useCallback(() => setRetryKey((key) => key + 1), []);
  return { ...state, retry };
}

export function getPollingInterval(): number {
  const configured = Number(import.meta.env.VITE_POLL_INTERVAL_MS ?? 1500);
  if (!Number.isFinite(configured)) return 1500;
  return Math.min(2000, Math.max(1000, configured));
}
