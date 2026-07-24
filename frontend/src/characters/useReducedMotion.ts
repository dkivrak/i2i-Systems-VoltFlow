import { useSyncExternalStore } from 'react';

const REDUCED_MOTION_QUERY = '(prefers-reduced-motion: reduce)';
let sharedMediaQuery: MediaQueryList | null | undefined;
const subscribers = new Set<() => void>();

function getMediaQuery(): MediaQueryList | null {
  if (sharedMediaQuery !== undefined) return sharedMediaQuery;
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    sharedMediaQuery = null;
    return sharedMediaQuery;
  }
  sharedMediaQuery = window.matchMedia(REDUCED_MOTION_QUERY);
  return sharedMediaQuery;
}

const notifySubscribers = () => {
  subscribers.forEach((subscriber) => subscriber());
};

function subscribe(onStoreChange: () => void): () => void {
  const mediaQuery = getMediaQuery();
  if (!mediaQuery) return () => undefined;

  subscribers.add(onStoreChange);
  if (subscribers.size === 1) {
    if (typeof mediaQuery.addEventListener === 'function') {
      mediaQuery.addEventListener('change', notifySubscribers);
    } else {
      mediaQuery.addListener(notifySubscribers);
    }
  }

  return () => {
    subscribers.delete(onStoreChange);
    if (subscribers.size !== 0) return;
    if (typeof mediaQuery.removeEventListener === 'function') {
      mediaQuery.removeEventListener('change', notifySubscribers);
    } else {
      mediaQuery.removeListener(notifySubscribers);
    }
  };
}

function getSnapshot(): boolean {
  return getMediaQuery()?.matches ?? false;
}

export function usePrefersReducedMotion(): boolean {
  return useSyncExternalStore(subscribe, getSnapshot, () => false);
}
