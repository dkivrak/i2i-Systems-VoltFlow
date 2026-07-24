import {
  forwardRef,
  memo,
  useCallback,
  useEffect,
  useRef,
  type CSSProperties,
  type HTMLAttributes,
  type PointerEvent as ReactPointerEvent,
} from 'react';
import { usePrefersReducedMotion } from './useReducedMotion';

type GroupCssProperties = CSSProperties & Record<`--vw-group-${string}`, string | number>;

export interface CharacterGroupProps extends HTMLAttributes<HTMLDivElement> {
  gazeEnabled?: boolean;
  /**
   * Tracks the pointer across the viewport instead of only inside the group.
   * Useful for split-screen scenes where characters react to nearby controls.
   */
  trackViewport?: boolean;
  /**
   * Scales the normalized pointer vector. Clamped to 0..1.
   */
  gazeStrength?: number;
  /**
   * Maximum inherited pupil travel in CSS pixels. Clamped to 0..8 px.
   */
  gazeLimit?: number;
  reducedMotion?: boolean;
  accessibleLabel?: string;
}

interface PointerCoordinates {
  clientX: number;
  clientY: number;
}

function finiteOr(value: number, fallback: number): number {
  return Number.isFinite(value) ? value : fallback;
}

function clamp(value: number, minimum: number, maximum: number): number {
  return Math.min(maximum, Math.max(minimum, value));
}

const CharacterGroupComponent = forwardRef<HTMLDivElement, CharacterGroupProps>(
  function CharacterGroup(
    {
      children,
      gazeEnabled = true,
      trackViewport = false,
      gazeStrength = 0.72,
      gazeLimit = 4,
      reducedMotion,
      accessibleLabel,
      'aria-label': ariaLabel,
      className,
      style,
      onPointerMove,
      onPointerLeave,
      onPointerCancel,
      ...rest
    },
    forwardedRef,
  ) {
    const rootRef = useRef<HTMLDivElement | null>(null);
    const animationFrameRef = useRef<number | null>(null);
    const pendingPointerRef = useRef<PointerCoordinates | null | undefined>();
    const systemReducedMotion = usePrefersReducedMotion();
    const shouldReduceMotion = reducedMotion ?? systemReducedMotion;
    const resolvedAccessibleLabel = accessibleLabel ?? ariaLabel;
    const resolvedStrength = clamp(finiteOr(gazeStrength, 0.72), 0, 1);
    const resolvedLimit = clamp(finiteOr(gazeLimit, 4), 0, 8);

    const setRootRef = useCallback(
      (node: HTMLDivElement | null) => {
        rootRef.current = node;
        if (typeof forwardedRef === 'function') forwardedRef(node);
        else if (forwardedRef) forwardedRef.current = node;
      },
      [forwardedRef],
    );

    const writeGaze = useCallback((x: number, y: number) => {
      const root = rootRef.current;
      if (!root) return;
      root.style.setProperty('--vw-group-gaze-x', `${x}px`);
      root.style.setProperty('--vw-group-gaze-y', `${y}px`);
    }, []);

    const flushPendingPointer = useCallback(() => {
      animationFrameRef.current = null;
      const pendingPointer = pendingPointerRef.current;
      pendingPointerRef.current = undefined;
      const root = rootRef.current;

      if (
        !root ||
        pendingPointer == null ||
        !Number.isFinite(pendingPointer.clientX) ||
        !Number.isFinite(pendingPointer.clientY) ||
        !gazeEnabled ||
        shouldReduceMotion ||
        resolvedLimit === 0
      ) {
        writeGaze(0, 0);
        return;
      }

      const bounds = root.getBoundingClientRect();
      if (bounds.width <= 0 || bounds.height <= 0) {
        writeGaze(0, 0);
        return;
      }

      let x = (pendingPointer.clientX - (bounds.left + bounds.width / 2)) / (bounds.width / 2);
      let y = (pendingPointer.clientY - (bounds.top + bounds.height / 2)) / (bounds.height / 2);
      const magnitude = Math.hypot(x, y);
      if (magnitude > 1) {
        x /= magnitude;
        y /= magnitude;
      }

      writeGaze(
        x * resolvedStrength * resolvedLimit,
        y * resolvedStrength * resolvedLimit,
      );
    }, [
      gazeEnabled,
      resolvedLimit,
      resolvedStrength,
      shouldReduceMotion,
      writeGaze,
    ]);

    const schedulePointer = useCallback(
      (pointer: PointerCoordinates | null) => {
        pendingPointerRef.current = pointer;
        if (animationFrameRef.current !== null) return;
        animationFrameRef.current = window.requestAnimationFrame(flushPendingPointer);
      },
      [flushPendingPointer],
    );

    useEffect(() => {
      if (!gazeEnabled || shouldReduceMotion) {
        pendingPointerRef.current = undefined;
        if (animationFrameRef.current !== null) {
          window.cancelAnimationFrame(animationFrameRef.current);
          animationFrameRef.current = null;
        }
        writeGaze(0, 0);
      }
    }, [gazeEnabled, shouldReduceMotion, writeGaze]);

    useEffect(() => {
      if (!trackViewport || !gazeEnabled || shouldReduceMotion) return undefined;
      const handleViewportPointer = (event: PointerEvent) => {
        schedulePointer({ clientX: event.clientX, clientY: event.clientY });
      };
      const resetPointer = () => schedulePointer(null);
      window.addEventListener('pointermove', handleViewportPointer, {
        passive: true,
      });
      window.addEventListener('blur', resetPointer);
      document.documentElement.addEventListener('pointerleave', resetPointer);
      return () => {
        window.removeEventListener('pointermove', handleViewportPointer);
        window.removeEventListener('blur', resetPointer);
        document.documentElement.removeEventListener(
          'pointerleave',
          resetPointer,
        );
      };
    }, [
      gazeEnabled,
      schedulePointer,
      shouldReduceMotion,
      trackViewport,
    ]);

    useEffect(
      () => () => {
        if (animationFrameRef.current !== null) {
          window.cancelAnimationFrame(animationFrameRef.current);
        }
      },
      [],
    );

    const handlePointerMove = (event: ReactPointerEvent<HTMLDivElement>) => {
      onPointerMove?.(event);
      if (
        event.defaultPrevented ||
        !gazeEnabled ||
        shouldReduceMotion ||
        trackViewport
      ) {
        return;
      }
      schedulePointer({ clientX: event.clientX, clientY: event.clientY });
    };

    const handlePointerLeave = (event: ReactPointerEvent<HTMLDivElement>) => {
      onPointerLeave?.(event);
      if (!gazeEnabled || shouldReduceMotion || trackViewport) {
        writeGaze(0, 0);
        return;
      }
      schedulePointer(null);
    };

    const handlePointerCancel = (event: ReactPointerEvent<HTMLDivElement>) => {
      onPointerCancel?.(event);
      if (!gazeEnabled || shouldReduceMotion || trackViewport) {
        writeGaze(0, 0);
        return;
      }
      schedulePointer(null);
    };

    const groupStyle: GroupCssProperties = {
      ...style,
      '--vw-group-gaze-x': '0px',
      '--vw-group-gaze-y': '0px',
      '--vw-group-gaze-limit': `${resolvedLimit}px`,
    };
    const classes = [
      'vw-character-group',
      gazeEnabled ? 'is-gaze-enabled' : '',
      shouldReduceMotion ? 'is-reduced-motion' : '',
      className ?? '',
    ]
      .filter(Boolean)
      .join(' ');

    return (
      <div
        {...rest}
        aria-label={resolvedAccessibleLabel}
        className={classes}
        data-gaze-enabled={gazeEnabled && !shouldReduceMotion ? 'true' : 'false'}
        data-reduced-motion={shouldReduceMotion ? 'true' : 'false'}
        onPointerCancel={handlePointerCancel}
        onPointerLeave={handlePointerLeave}
        onPointerMove={handlePointerMove}
        ref={setRootRef}
        role={resolvedAccessibleLabel ? 'group' : rest.role}
        style={groupStyle}
      >
        {children}
      </div>
    );
  },
);

export const CharacterGroup = memo(CharacterGroupComponent);
CharacterGroup.displayName = 'CharacterGroup';
