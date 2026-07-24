import { memo, type CSSProperties } from 'react';
import { getCharacterConfig, getCharacterStateConfig } from './config';
import { usePrefersReducedMotion } from './useReducedMotion';
import type {
  ApplianceCharacterProps,
  CharacterFeature,
  CharacterGaze,
  CharacterSize,
} from './types';

type CharacterCssProperties = CSSProperties & Record<`--vw-character-${string}`, string | number>;

const sizeValues: Record<Exclude<CharacterSize, number>, string> = {
  sm: '4.5rem',
  md: '7rem',
  lg: '10rem',
};

function finiteOrZero(value: number): number {
  return Number.isFinite(value) ? value : 0;
}

function clamp(value: number, minimum: number, maximum: number): number {
  return Math.min(maximum, Math.max(minimum, value));
}

/**
 * Constrains gaze to a unit circle, preventing diagonal pupil travel from
 * exceeding the requested physical limit.
 */
export function clampCharacterGaze(gaze: CharacterGaze): CharacterGaze {
  let x = clamp(finiteOrZero(gaze.x), -1, 1);
  let y = clamp(finiteOrZero(gaze.y), -1, 1);
  const magnitude = Math.hypot(x, y);
  if (magnitude > 1) {
    x /= magnitude;
    y /= magnitude;
  }
  return { x, y };
}

function resolveSize(size: CharacterSize): string {
  if (typeof size !== 'number') return sizeValues[size];
  return `${clamp(finiteOrZero(size), 40, 320)}px`;
}

function CharacterDetails({ features }: { features: readonly CharacterFeature[] }) {
  return (
    <span className="vw-character__details">
      {features.map((feature) => (
        <span
          className={`vw-character__detail vw-character__detail--${feature}`}
          data-feature={feature}
          key={feature}
        />
      ))}
    </span>
  );
}

function CharacterArtwork({ features }: { features: readonly CharacterFeature[] }) {
  return (
    <span className="vw-character__artwork" aria-hidden="true">
      <span className="vw-character__ground-shadow" />
      <span className="vw-character__antenna-zone" />
      <span className="vw-character__body">
        <CharacterDetails features={features} />
        <span className="vw-character__face">
          <span className="vw-character__eye vw-character__eye--left">
            <span className="vw-character__pupil" />
            <span className="vw-character__eyelid" />
          </span>
          <span className="vw-character__eye vw-character__eye--right">
            <span className="vw-character__pupil" />
            <span className="vw-character__eyelid" />
          </span>
          <span className="vw-character__mouth" />
        </span>
        <span className="vw-character__arm vw-character__arm--left">
          <span className="vw-character__hand" />
        </span>
        <span className="vw-character__arm vw-character__arm--right">
          <span className="vw-character__hand" />
        </span>
        <span className="vw-character__privacy-hands">
          <span />
          <span />
        </span>
        <span className="vw-character__status-badge">
          <span className="vw-character__status-mark" />
        </span>
      </span>
      <span className="vw-character__feet">
        <span className="vw-character__foot vw-character__foot--left" />
        <span className="vw-character__foot vw-character__foot--right" />
      </span>
      <span className="vw-character__sleep-mark">z</span>
      <span className="vw-character__activity-mark" />
    </span>
  );
}

function ApplianceCharacterComponent({
  type,
  state = 'idle',
  gaze,
  gazeLimit,
  size = 'md',
  primaryColor,
  accentColor,
  outlineColor,
  selected = false,
  interactive = false,
  disabled = false,
  accessibleLabel,
  accessibleDescriptionId,
  accessibleControlsId,
  decorative,
  onActivate,
  className,
  style,
  reducedMotion,
}: ApplianceCharacterProps) {
  const config = getCharacterConfig(type);
  const stateConfig = getCharacterStateConfig(state);
  const systemReducedMotion = usePrefersReducedMotion();
  const shouldReduceMotion = reducedMotion ?? systemReducedMotion;
  const normalizedGaze = gaze ? clampCharacterGaze(gaze) : undefined;
  const resolvedGazeLimit = clamp(
    finiteOrZero(gazeLimit ?? config.gazeLimit),
    0,
    8,
  );
  const generatedLabel = `${config.label}, ${stateConfig.label}`;
  const resolvedAccessibleLabel = accessibleLabel?.trim() || generatedLabel;
  const isDecorative = !interactive && (decorative ?? !accessibleLabel);

  const characterStyle: CharacterCssProperties = {
    ...style,
    '--vw-character-size': resolveSize(size),
    '--vw-character-aspect-ratio': String(config.aspectRatio),
    '--vw-character-primary': primaryColor ?? config.primaryColor,
    '--vw-character-accent': accentColor ?? config.accentColor,
    '--vw-character-outline': outlineColor ?? config.outlineColor,
    '--vw-character-highlight': config.highlightColor,
    '--vw-character-gaze-limit': `${resolvedGazeLimit}px`,
    '--vw-character-gaze-x': normalizedGaze
      ? `${normalizedGaze.x * resolvedGazeLimit}px`
      : 'var(--vw-group-gaze-x, 0px)',
    '--vw-character-gaze-y': normalizedGaze
      ? `${normalizedGaze.y * resolvedGazeLimit}px`
      : 'var(--vw-group-gaze-y, 0px)',
  };

  const classes = [
    'vw-character',
    `vw-character--${config.slug}`,
    `vw-character--${config.silhouette}`,
    `vw-character--state-${state}`,
    selected ? 'is-selected' : '',
    interactive ? 'is-interactive' : '',
    disabled ? 'is-disabled' : '',
    shouldReduceMotion ? 'is-reduced-motion' : '',
    className ?? '',
  ]
    .filter(Boolean)
    .join(' ');

  const dataAttributes = {
    'data-character-type': type,
    'data-character-state': state,
    'data-character-expression': stateConfig.expression,
    'data-character-motion': stateConfig.motion,
    'data-character-urgency': stateConfig.urgency,
    'data-selected': selected ? 'true' : 'false',
    'data-reduced-motion': shouldReduceMotion ? 'true' : 'false',
  } as const;

  if (interactive) {
    return (
      <button
        {...dataAttributes}
        aria-controls={accessibleControlsId}
        aria-describedby={accessibleDescriptionId}
        aria-label={resolvedAccessibleLabel}
        aria-pressed={selected}
        className={classes}
        disabled={disabled}
        onClick={onActivate}
        style={characterStyle}
        type="button"
      >
        <CharacterArtwork features={config.features} />
      </button>
    );
  }

  return (
    <div
      {...dataAttributes}
      aria-describedby={isDecorative ? undefined : accessibleDescriptionId}
      aria-hidden={isDecorative || undefined}
      aria-label={isDecorative ? undefined : resolvedAccessibleLabel}
      className={classes}
      role={isDecorative ? undefined : 'img'}
      style={characterStyle}
    >
      <CharacterArtwork features={config.features} />
    </div>
  );
}

export const ApplianceCharacter = memo(ApplianceCharacterComponent);
ApplianceCharacter.displayName = 'ApplianceCharacter';
