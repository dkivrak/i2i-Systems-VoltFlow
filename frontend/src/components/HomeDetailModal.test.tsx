import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, api } from '../api/client';
import type { ApplianceStatus, HomeStatus } from '../types';
import { ToastProvider } from './ToastProvider';
import { HomeDetailModal } from './HomeDetailModal';

const emptyHome: HomeStatus = {
  homeId: 42,
  homeName: 'Yeni Ev',
  city: 'İstanbul',
  currentPowerWatts: 0,
  accumulatedEnergyKwh: 0,
  currentCost: 0,
  monthlyBudget: 1000,
  budgetUsagePercent: 0,
  tariffState: 'NORMAL',
  anomalyCount: 0,
  appliances: [],
};

const pendingDevice: ApplianceStatus = {
  applianceId: 99,
  name: 'Salon Televizyonu',
  type: 'TELEVISION',
  currentPowerWatts: 0,
  accumulatedEnergyKwh: 0,
  accumulatedCost: 0,
  operatingState: 'OFF',
  safePowerLimitWatts: 450,
  consecutiveBreachCount: 0,
  healthStatus: 'NORMAL',
};

function renderModal(overrides: Partial<{
  onChanged: () => void;
}> = {}) {
  return render(
    <ToastProvider>
      <HomeDetailModal
        summary={emptyHome}
        onClose={vi.fn()}
        onChanged={overrides.onChanged}
      />
    </ToastProvider>,
  );
}

async function openAddForm(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('tab', { name: 'Cihazlar' }));
  await user.click(screen.getByRole('button', { name: 'Cihaz ekle' }));
}

beforeEach(() => {
  vi.spyOn(api, 'getHomeStatus').mockResolvedValue(emptyHome);
  vi.spyOn(api, 'getHistory').mockResolvedValue([]);
  vi.spyOn(api, 'getEvents').mockResolvedValue([]);
  vi.spyOn(api, 'getRecommendations').mockResolvedValue([]);
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('existing-home appliance registration', () => {
  it('displays the empty device state and opens the registration form', async () => {
    const user = userEvent.setup();
    renderModal();

    await user.click(screen.getByRole('tab', { name: 'Cihazlar' }));
    expect(
      screen.getByRole('heading', { name: 'Henüz cihaz eklenmemiş' }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/cihaz kaydetmeniz gerekir/i),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Cihaz ekle' }));
    expect(screen.getByTestId('add-appliance-form')).toBeInTheDocument();
    expect(screen.getByLabelText('Cihaz adı')).toHaveFocus();
  });

  it('blocks invalid values before making a request', async () => {
    const addAppliance = vi.spyOn(api, 'addAppliance');
    const user = userEvent.setup();
    renderModal();
    await openAddForm(user);

    await user.type(
      screen.getByLabelText('Güvenli maksimum güç (W)'),
      'geçersiz',
    );
    await user.click(screen.getByRole('button', { name: 'Cihazı kaydet' }));

    expect(screen.getByText('Cihaz adı zorunludur.')).toBeInTheDocument();
    expect(
      screen.getByText('Geçerli bir güç değeri girin.'),
    ).toBeInTheDocument();
    expect(addAppliance).not.toHaveBeenCalled();
  });

  it('shows the new device as waiting for telemetry and refreshes parent data', async () => {
    vi.spyOn(api, 'addAppliance').mockResolvedValue(pendingDevice);
    const onChanged = vi.fn();
    const user = userEvent.setup();
    renderModal({ onChanged });
    await openAddForm(user);

    await user.type(screen.getByLabelText('Cihaz adı'), 'Salon Televizyonu');
    await user.selectOptions(screen.getByLabelText('Cihaz türü'), 'TELEVISION');
    await user.type(screen.getByLabelText('Güvenli maksimum güç (W)'), '450');
    await user.click(screen.getByRole('button', { name: 'Cihazı kaydet' }));

    expect(
      await screen.findByText('Salon Televizyonu'),
    ).toBeInTheDocument();
    expect(screen.getAllByText('Telemetri bekleniyor').length).toBeGreaterThan(0);
    expect(screen.getByText('Cihaz eklendi')).toBeInTheDocument();
    expect(onChanged).toHaveBeenCalledOnce();
  });

  it('renders backend failures as a human-readable Turkish message', async () => {
    vi.spyOn(api, 'addAppliance').mockRejectedValue(
      new ApiError('Cihaz şu anda kaydedilemedi. Lütfen yeniden deneyin.', 500),
    );
    const user = userEvent.setup();
    renderModal();
    await openAddForm(user);

    await user.type(screen.getByLabelText('Cihaz adı'), 'Masa Lambası');
    await user.type(screen.getByLabelText('Güvenli maksimum güç (W)'), '60');
    await user.click(screen.getByRole('button', { name: 'Cihazı kaydet' }));

    expect(
      await screen.findByText(
        'Cihaz şu anda kaydedilemedi. Lütfen yeniden deneyin.',
      ),
    ).toBeInTheDocument();
    expect(screen.queryByText(/exception|stack trace|hibernate/i)).not.toBeInTheDocument();
  });

  it('prevents duplicate submissions while the first request is pending', async () => {
    let resolveRequest: ((value: ApplianceStatus) => void) | undefined;
    const addAppliance = vi.spyOn(api, 'addAppliance').mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveRequest = resolve;
        }),
    );
    const user = userEvent.setup();
    renderModal();
    await openAddForm(user);

    await user.type(screen.getByLabelText('Cihaz adı'), 'Masa Lambası');
    await user.type(screen.getByLabelText('Güvenli maksimum güç (W)'), '60');
    const form = screen.getByTestId('add-appliance-form').querySelector('form');
    expect(form).not.toBeNull();
    fireEvent.submit(form!);
    fireEvent.submit(form!);

    expect(addAppliance).toHaveBeenCalledOnce();
    expect(
      screen.getByRole('button', { name: /Cihaz kaydediliyor/ }),
    ).toBeDisabled();

    resolveRequest?.(pendingDevice);
    await waitFor(() => expect(screen.getByText('Cihaz eklendi')).toBeInTheDocument());
  });
});
