import type { CSSProperties } from 'react';
import type { ApplianceType } from '../types';

export const GENERIC_CHARACTER_TYPES = [
  'DISHWASHER',
  'HEATER',
  'VACUUM_CLEANER',
  'SMART_PLUG',
  'IOT_SENSOR',
] as const;

export type GenericCharacterType = (typeof GENERIC_CHARACTER_TYPES)[number];
export type CharacterType = ApplianceType | GenericCharacterType;

export const CHARACTER_STATES = [
  'idle',
  'observing',
  'active',
  'sleeping',
  'happy',
  'approved',
  'warning',
  'anomalous',
  'error',
  'disconnected',
  'loading',
  'success',
  'privacy',
] as const;

export type CharacterState = (typeof CHARACTER_STATES)[number];
export type CharacterSize = 'sm' | 'md' | 'lg' | number;

export interface CharacterGaze {
  /**
   * Normalized horizontal gaze. Values outside -1..1 are safely clamped.
   */
  x: number;
  /**
   * Normalized vertical gaze. Values outside -1..1 are safely clamped.
   */
  y: number;
}

export type CharacterSilhouette =
  | 'tall'
  | 'box'
  | 'screen'
  | 'round'
  | 'slim'
  | 'plug'
  | 'sensor';

export type CharacterExpression =
  | 'neutral'
  | 'curious'
  | 'focused'
  | 'sleeping'
  | 'smile'
  | 'proud'
  | 'concerned'
  | 'alarmed'
  | 'sad'
  | 'offline'
  | 'waiting'
  | 'celebrating'
  | 'hidden';

export type CharacterMotion =
  | 'breathe'
  | 'observe'
  | 'work'
  | 'sleep'
  | 'bounce'
  | 'nod'
  | 'worry'
  | 'alert'
  | 'shake'
  | 'dim'
  | 'wait'
  | 'celebrate'
  | 'cover';

export type CharacterFeature =
  | 'split-door'
  | 'handle'
  | 'drum'
  | 'dial'
  | 'screen'
  | 'stand'
  | 'vent'
  | 'knobs'
  | 'oven-door'
  | 'spout'
  | 'kettle-handle'
  | 'keypad'
  | 'microwave-door'
  | 'shade'
  | 'bulb'
  | 'rays'
  | 'keyboard'
  | 'rack'
  | 'grill'
  | 'hose'
  | 'prongs'
  | 'socket'
  | 'antenna'
  | 'signal';

export interface CharacterConfig {
  type: CharacterType;
  slug: string;
  label: string;
  silhouette: CharacterSilhouette;
  primaryColor: string;
  accentColor: string;
  outlineColor: string;
  highlightColor: string;
  aspectRatio: number;
  gazeLimit: number;
  features: readonly CharacterFeature[];
}

export interface CharacterStateConfig {
  state: CharacterState;
  label: string;
  expression: CharacterExpression;
  motion: CharacterMotion;
  urgency: 'none' | 'info' | 'positive' | 'warning' | 'critical';
}

export interface ApplianceCharacterProps {
  type: CharacterType;
  state?: CharacterState;
  /**
   * A normalized, presentation-only gaze vector. It is not telemetry state.
   */
  gaze?: CharacterGaze;
  /**
   * Maximum pupil travel in CSS pixels. Clamped to a safe 0..8 px range.
   */
  gazeLimit?: number;
  size?: CharacterSize;
  primaryColor?: string;
  accentColor?: string;
  outlineColor?: string;
  selected?: boolean;
  interactive?: boolean;
  disabled?: boolean;
  /**
   * Overrides the generated accessible name. Interactive characters always
   * retain an accessible name even when this is omitted.
   */
  accessibleLabel?: string;
  accessibleDescriptionId?: string;
  accessibleControlsId?: string;
  /**
   * Unlabelled, non-interactive characters are decorative by default.
   */
  decorative?: boolean;
  onActivate?: () => void;
  className?: string;
  style?: CSSProperties;
  /**
   * Overrides the operating-system preference for isolated previews/tests.
   */
  reducedMotion?: boolean;
}
