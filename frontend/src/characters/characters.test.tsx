import { act, fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ApplianceCharacter, clampCharacterGaze } from './ApplianceCharacter';
import { CharacterGroup } from './CharacterGroup';

describe('ApplianceCharacter', () => {
  it('provides semantic selection and activation without consuming telemetry data', () => {
    const onActivate = vi.fn();

    render(
      <ApplianceCharacter
        accessibleLabel="Mutfak buzdolabı, normal çalışıyor"
        interactive
        onActivate={onActivate}
        selected
        state="active"
        type="REFRIGERATOR"
      />,
    );

    const character = screen.getByRole('button', {
      name: 'Mutfak buzdolabı, normal çalışıyor',
    });
    expect(character).toHaveAttribute('aria-pressed', 'true');
    expect(character).toHaveAttribute('data-character-state', 'active');
    fireEvent.click(character);
    expect(onActivate).toHaveBeenCalledOnce();
  });

  it('bounds diagonal gaze to a unit circle', () => {
    const gaze = clampCharacterGaze({ x: 8, y: 8 });
    expect(Math.hypot(gaze.x, gaze.y)).toBeCloseTo(1);
    expect(gaze.x).toBeCloseTo(Math.SQRT1_2);
    expect(gaze.y).toBeCloseTo(Math.SQRT1_2);
  });
});

describe('CharacterGroup', () => {
  it('coalesces pointer movement into one animation-frame gaze update', () => {
    let scheduledFrame: FrameRequestCallback | undefined;
    const requestAnimationFrame = vi
      .spyOn(window, 'requestAnimationFrame')
      .mockImplementation((callback) => {
        scheduledFrame = callback;
        return 1;
      });

    render(
      <CharacterGroup
        accessibleLabel="Enerji yardımcıları"
        gazeLimit={8}
        gazeStrength={1}
        reducedMotion={false}
      >
        <ApplianceCharacter type="TELEVISION" />
      </CharacterGroup>,
    );

    const group = screen.getByRole('group', { name: 'Enerji yardımcıları' });
    fireEvent(group, new MouseEvent('pointermove', { bubbles: true, clientX: 800, clientY: 200 }));
    fireEvent(group, new MouseEvent('pointermove', { bubbles: true, clientX: 800, clientY: 200 }));

    expect(requestAnimationFrame).toHaveBeenCalledOnce();
    act(() => scheduledFrame?.(0));
    expect(group.style.getPropertyValue('--vw-group-gaze-x')).toBe('8px');
    expect(group.style.getPropertyValue('--vw-group-gaze-y')).toBe('0px');
  });

  it('disables pointer tracking when reduced motion is requested', () => {
    const requestAnimationFrame = vi.spyOn(window, 'requestAnimationFrame');

    render(
      <CharacterGroup accessibleLabel="Sakin yardımcılar" reducedMotion>
        <ApplianceCharacter type="LAMP" />
      </CharacterGroup>,
    );

    const group = screen.getByRole('group', { name: 'Sakin yardımcılar' });
    fireEvent.pointerMove(group, { clientX: 800, clientY: 200 });
    expect(group).toHaveAttribute('data-gaze-enabled', 'false');
    expect(requestAnimationFrame).not.toHaveBeenCalled();
  });
});
