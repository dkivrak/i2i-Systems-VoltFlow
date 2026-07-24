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
  orbitAnimation?: boolean;
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
      orbitAnimation = true,
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
    const orbitFrameRef = useRef<number | null>(null);
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
      
      const pupils = Array.from(root.querySelectorAll<HTMLElement>('.vw-character__pupil'));
      pupils.forEach((pupil) => {
        pupil.style.setProperty('--vw-character-gaze-x', `${x}px`);
        pupil.style.setProperty('--vw-character-gaze-y', `${y}px`);
      });
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
      if (bounds.width > 0 && bounds.height > 0) {
        let x =
          (pendingPointer.clientX - (bounds.left + bounds.width / 2)) /
          (bounds.width / 2);
        let y =
          (pendingPointer.clientY - (bounds.top + bounds.height / 2)) /
          (bounds.height / 2);
        const magnitude = Math.hypot(x, y);
        if (magnitude > 1) {
          x /= magnitude;
          y /= magnitude;
        }
        writeGaze(
          x * resolvedStrength * resolvedLimit,
          y * resolvedStrength * resolvedLimit,
        );
      }

      const eyes = Array.from(
        root.querySelectorAll<HTMLElement>('.vw-character__eye'),
      );
      if (eyes.length === 0) return;

      const mx = pendingPointer.clientX;
      const my = pendingPointer.clientY;

      eyes.forEach((eye) => {
        const eyeRect = eye.getBoundingClientRect();
        const ex = eyeRect.left + eyeRect.width / 2;
        const ey = eyeRect.top + eyeRect.height / 2;
        const dx = mx - ex;
        const dy = my - ey;
        
        const angle = Math.atan2(dy, dx);
        
        // Göz bebeğinin kayabileceği max px değeri (eski resolvedLimit = 4, maxOffset ~ 3px)
        const maxOffset = resolvedLimit * 1.5; 
        const dist = Math.min(Math.hypot(dx, dy) * resolvedStrength * 0.02, maxOffset);
        
        const offsetX = Math.cos(angle) * dist;
        const offsetY = Math.sin(angle) * dist;
        
        const pupil = eye.querySelector<HTMLElement>('.vw-character__pupil');
        if (pupil) {
          pupil.style.setProperty('--vw-character-gaze-x', `${offsetX}px`);
          pupil.style.setProperty('--vw-character-gaze-y', `${offsetY}px`);
        }
      });
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
      const handleViewportPointer = (event: MouseEvent) => {
        schedulePointer({ clientX: event.clientX, clientY: event.clientY });
      };
      const resetPointer = () => schedulePointer(null);
      window.addEventListener('pointermove', handleViewportPointer, {
        passive: true,
      });
      window.addEventListener('mousemove', handleViewportPointer, {
        passive: true,
      });
      window.addEventListener('blur', resetPointer);
      document.documentElement.addEventListener('pointerleave', resetPointer);
      return () => {
        window.removeEventListener('pointermove', handleViewportPointer);
        window.removeEventListener('mousemove', handleViewportPointer);
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

    useEffect(() => {
      const root = rootRef.current;
      if (!root || !orbitAnimation || shouldReduceMotion) return undefined;

      const charEls = Array.from(
        root.querySelectorAll<HTMLElement>('.auth-character, .character-scene__item')
      );
      if (charEls.length === 0) return undefined;

      // Dinamik faz ataması: Kaç karakter varsa yörüngeyi (2*PI radyan) o kadar eşit parçaya böl.
      // 4 karakter varsa 90 derece, 5 karakter varsa 72 derece vb.
      const chars = charEls.map((el, i) => {
        const phase = (Math.PI * 2 / charEls.length) * i;
        return { el, phase };
      });

      let t = 0;

      const tickOrbit = () => {
        const bounds = root.getBoundingClientRect();
        if (bounds.width > 0 && bounds.height > 0) {
          const cx = bounds.width / 2;
          const cy = bounds.height / 2;
          let Rx = bounds.width * 0.38;
          let Ry = bounds.height * 0.34;
          t += 0.006;

          // Tüm karakterler için SENKRONİZE zıplama (Math.sin(t * 10))
          const hop = Math.abs(Math.sin(t * 10)) * 12;
          const squash = 1 - Math.abs(Math.sin(t * 10)) * 0.12;

          // Olası pozisyonları hesapla
          const positions = chars.map((c) => {
            const ang = t + c.phase;
            const w = c.el.offsetWidth || 80;
            const h = c.el.offsetHeight || 80;
            return {
              c,
              ang,
              w,
              h,
              x: cx + Rx * Math.cos(ang),
              y: cy + Ry * Math.sin(ang) - hop,
            };
          });

          // Çakışma kontrolü (runtime overlap check)
          let hasOverlap = false;
          for (let i = 0; i < positions.length; i++) {
            for (let j = i + 1; j < positions.length; j++) {
              const p1 = positions[i];
              const p2 = positions[j];
              const dx = p1.x - p2.x;
              const dy = p1.y - p2.y;
              const dist = Math.sqrt(dx * dx + dy * dy);
              const minDist = (p1.w + p2.w) / 2 + 15; // 15px güvenlik payı
              if (dist < minDist) {
                hasOverlap = true;
                break;
              }
            }
            if (hasOverlap) break;
          }

          // Çakışma varsa R'yi dinamik olarak büyüt (itme uygula)
          if (hasOverlap) {
            Rx += 22;
            Ry += 22;
            positions.forEach((p) => {
              p.x = cx + Rx * Math.cos(p.ang);
              p.y = cy + Ry * Math.sin(p.ang) - hop;
            });
          }

          // Pozisyonları DOM'a uygula
          positions.forEach((p) => {
            p.c.el.style.left = `${p.x}px`;
            p.c.el.style.top = `${p.y}px`;
            p.c.el.style.marginLeft = `-${p.w / 2}px`;
            p.c.el.style.marginTop = `-${p.h / 2}px`;
            p.c.el.style.bottom = 'auto';
            p.c.el.style.right = 'auto';
            p.c.el.style.transform = `scaleY(${squash})`;
            p.c.el.style.transformOrigin = 'bottom center';
          });
        }
        orbitFrameRef.current = requestAnimationFrame(tickOrbit);
      };

      orbitFrameRef.current = requestAnimationFrame(tickOrbit);

      return () => {
        if (orbitFrameRef.current !== null) {
          cancelAnimationFrame(orbitFrameRef.current);
          orbitFrameRef.current = null;
        }
      };
    }, [orbitAnimation, shouldReduceMotion]);

    useEffect(
      () => () => {
        if (animationFrameRef.current !== null) {
          window.cancelAnimationFrame(animationFrameRef.current);
        }
        if (orbitFrameRef.current !== null) {
          window.cancelAnimationFrame(orbitFrameRef.current);
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
