import { act, render, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { getPollingInterval, usePollingResource } from './usePollingResource';

function Probe({ request, interval = 1500 }: { request: (signal: AbortSignal) => Promise<string>; interval?: number }) {
  const resource = usePollingResource(request, interval);
  return <output>{resource.data ?? (resource.isLoading ? 'loading' : 'empty')}</output>;
}

describe('usePollingResource', () => {
  it('keeps the configured polling interval within the required 1–2 second bound', () => {
    expect(getPollingInterval()).toBeGreaterThanOrEqual(1000);
    expect(getPollingInterval()).toBeLessThanOrEqual(2000);
  });

  it('aborts an in-flight request when its component unmounts', async () => {
    let capturedSignal: AbortSignal | undefined;
    const request = vi.fn((signal: AbortSignal) => {
      capturedSignal = signal;
      return new Promise<string>(() => undefined);
    });
    const { unmount } = render(<Probe request={request} />);
    await waitFor(() => expect(request).toHaveBeenCalledOnce());

    unmount();

    expect(capturedSignal?.aborted).toBe(true);
  });

  it('clears the next scheduled poll during cleanup', async () => {
    vi.useFakeTimers();
    const request = vi.fn().mockResolvedValue('stable');
    const { unmount } = render(<Probe request={request} interval={1500} />);

    await act(async () => {
      await Promise.resolve();
    });
    expect(request).toHaveBeenCalledTimes(1);

    await act(async () => {
      vi.advanceTimersByTime(1500);
      await Promise.resolve();
    });
    expect(request).toHaveBeenCalledTimes(2);

    unmount();
    await act(async () => {
      vi.advanceTimersByTime(5000);
      await Promise.resolve();
    });
    expect(request).toHaveBeenCalledTimes(2);
  });
});
