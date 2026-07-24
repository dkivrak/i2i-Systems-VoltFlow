import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from '../api/client';
import type { HomeRegistrationRequest } from '../types';
import { normalHome } from '../test/fixtures';
import { RegistrationModal } from './RegistrationModal';
import { ToastProvider } from './ToastProvider';

function renderModal(onCreated = vi.fn(), onClose = vi.fn()) {
  render(
    <ToastProvider>
      <RegistrationModal onCreated={onCreated} onClose={onClose} />
    </ToastProvider>,
  );
  return { onCreated, onClose };
}

describe('home registration', () => {
  afterEach(() => vi.restoreAllMocks());

  it('validates required home fields before making a request', async () => {
    const register = vi.spyOn(api, 'registerHome').mockResolvedValue(normalHome);
    const user = userEvent.setup();
    renderModal();

    await user.click(screen.getByRole('button', { name: 'Evi kaydet' }));

    expect(screen.getByText('Ev adı en az 2 karakter olmalıdır.')).toBeInTheDocument();
    expect(screen.getByText('Geçerli bir e-posta adresi girin.')).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('Lütfen işaretli alanları kontrol edin.');
    expect(register).not.toHaveBeenCalled();
  });

  it('expands quantities and supports duplicate appliance types', async () => {
    const register = vi.spyOn(api, 'registerHome').mockResolvedValue(normalHome);
    const user = userEvent.setup();
    const callbacks = renderModal();

    await user.type(screen.getByLabelText('Ev adı'), 'Ataşehir Evi');
    await user.type(screen.getByLabelText('İletişim e-postası'), 'enerji@example.com');
    await user.selectOptions(screen.getByLabelText('1. cihaz türü'), 'KETTLE');
    await user.type(screen.getByLabelText('1. cihaz adı'), 'Mutfak Isıtıcısı');
    await user.clear(screen.getByLabelText('1. cihaz adedi'));
    await user.type(screen.getByLabelText('1. cihaz adedi'), '2');
    await user.click(screen.getByRole('button', { name: 'Cihaz ekle' }));
    await user.selectOptions(screen.getByLabelText('2. cihaz türü'), 'KETTLE');
    await user.type(screen.getByLabelText('2. cihaz adı'), 'Ofis Isıtıcısı');
    await user.click(screen.getByRole('button', { name: 'Evi kaydet' }));

    await waitFor(() => expect(register).toHaveBeenCalledTimes(1));
    const payload = register.mock.calls[0][0] as HomeRegistrationRequest;
    expect(payload).toMatchObject({
      name: 'Ataşehir Evi',
      city: 'İstanbul',
      contactEmail: 'enerji@example.com',
      monthlyBudget: 1500,
      normalTariffPerKwh: 2.5,
      penaltyMultiplier: 1.5,
    });
    expect(payload.appliances).toEqual([
      { name: 'Mutfak Isıtıcısı 1', type: 'KETTLE', safePowerLimitWatts: 2400 },
      { name: 'Mutfak Isıtıcısı 2', type: 'KETTLE', safePowerLimitWatts: 2400 },
      { name: 'Ofis Isıtıcısı', type: 'KETTLE', safePowerLimitWatts: 2400 },
    ]);
    expect(callbacks.onCreated).toHaveBeenCalledOnce();
    expect(callbacks.onClose).toHaveBeenCalledOnce();
  });
});
