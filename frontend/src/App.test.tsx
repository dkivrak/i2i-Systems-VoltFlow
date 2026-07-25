import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import App from './App';
import { ApiError, api } from './api/client';
import { HomeCard } from './components/HomeCard';
import { anomalousHome, normalHome, warningHome } from './test/fixtures';

import { getStoredToken, setStoredToken } from './api/client';
import { beforeEach } from 'vitest';

describe('VoltFlow dashboard', () => {
  beforeEach(() => {
    setStoredToken('mock-jwt-token-for-test');
    window.history.replaceState({}, '', '/dashboard');
  });

  afterEach(() => {
    vi.restoreAllMocks();
    window.history.replaceState({}, '', '/');
  });

  it('renders live home cards and overview values', async () => {
    vi.spyOn(api, 'getHomeStatuses').mockResolvedValue([normalHome]);

    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Kadıköy Evi' })).toBeInTheDocument();
    expect(screen.getByText('Anlık toplam güç')).toBeInTheDocument();
    expect(screen.getByText('42,75 kWh')).toBeInTheDocument();
    expect(screen.getByRole('progressbar', { name: 'Kadıköy Evi bütçe kullanımı' })).toHaveAttribute(
      'aria-valuenow',
      '28',
    );
  });

  it('keeps a selected home open and renders live appliance details', async () => {
    vi.spyOn(api, 'getHomeStatuses').mockResolvedValue([anomalousHome]);
    vi.spyOn(api, 'getHomeStatus').mockResolvedValue(anomalousHome);
    vi.spyOn(api, 'getHistory').mockResolvedValue([
      {
        id: 1,
        periodStart: '2026-07-21T10:00:00Z',
        energyKwh: 2.4,
        cost: 6,
      },
    ]);
    vi.spyOn(api, 'getEvents').mockResolvedValue([
      {
        id: 'evt-1',
        type: 'ANOMALY',
        title: 'Olağan dışı tüketim',
        description: 'Bilgisayar güvenli sınırı aştı.',
        occurredAt: '2026-07-21T10:00:00Z',
      },
    ]);
    vi.spyOn(api, 'getRecommendations').mockResolvedValue([
      {
        id: 1,
        text: 'Bilgisayarın yüksek yükte çalışan uygulamalarını kontrol edin.',
        createdAt: '2026-07-21T10:00:01Z',
      },
    ]);
    const user = userEvent.setup();
    render(<App />);

    await user.click(await screen.findByRole('button', { name: 'Moda Evi detaylarını aç' }));

    const dialog = await screen.findByRole('dialog', { name: 'Moda Evi' });
    expect(dialog).toBeInTheDocument();
    expect(screen.getAllByText('Çalışma Bilgisayarı')[0]).toBeInTheDocument();
    expect(screen.getByText('Yüksek yük')).toBeInTheDocument();
    expect(screen.getByText('Bilgisayarın yüksek yükte çalışan uygulamalarını kontrol edin.')).toBeInTheDocument();
    expect(screen.getByRole('progressbar', { name: 'Aylık bütçe kullanımı' })).toHaveAttribute(
      'aria-valuenow',
      '100',
    );
    expect(screen.getAllByText('%112,5').length).toBeGreaterThan(0);
  });

  it('shows the dashboard skeleton until the first request finishes', () => {
    let requestSignal: AbortSignal | undefined;
    vi.spyOn(api, 'getHomeStatuses').mockImplementation(
      (signal) => {
        requestSignal = signal;
        return new Promise(() => undefined);
      },
    );

    const { unmount } = render(<App />);

    expect(screen.getByRole('status', { name: 'Evler yükleniyor' })).toBeInTheDocument();
    unmount();
    expect(requestSignal?.aborted).toBe(true);
  });

  it('shows a readable API error and retries without exposing server details', async () => {
    const statuses = vi
      .spyOn(api, 'getHomeStatuses')
      .mockRejectedValueOnce(new ApiError('VoltFlow servisine şu anda ulaşılamıyor.', 500))
      .mockResolvedValue([normalHome]);
    const user = userEvent.setup();
    render(<App />);

    expect(await screen.findByText('Veriler alınamadı')).toBeInTheDocument();
    expect(screen.getByText('VoltFlow servisine şu anda ulaşılamıyor.')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Yeniden dene' }));

    expect(await screen.findByRole('heading', { name: 'Kadıköy Evi' })).toBeInTheDocument();
    expect(statuses).toHaveBeenCalledTimes(2);
  });

  it('keeps only functional controls in the main header', async () => {
    vi.spyOn(api, 'getHomeStatuses').mockResolvedValue([normalHome]);
    const user = userEvent.setup();

    render(<App />);

    await screen.findByRole('heading', { name: 'Kadıköy Evi' });
    expect(screen.queryByRole('button', { name: 'Demo Daire Ekle' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Tema Değiştir' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'TR' })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Yeni ev ekle' }));
    expect(await screen.findByRole('dialog', { name: 'Yeni ev kaydı' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Vazgeç' }));

    await user.click(screen.getByRole('button', { name: 'Çıkış' }));
    expect(await screen.findByRole('heading', { name: 'Hesabınıza Giriş Yapın' })).toBeInTheDocument();
    expect(getStoredToken()).toBeNull();
  });
});

describe('home card visual states', () => {
  it('visually marks homes that cross the 80 percent quota', () => {
    const { container } = render(<HomeCard home={warningHome} onSelect={vi.fn()} />);

    const card = container.querySelector('.home-card');
    expect(card).toHaveAttribute('data-quota-state', 'warning');
    expect(card).toHaveClass('home-card--warning');
    expect(screen.getByText('%82,5')).toBeInTheDocument();
  });

  it('visually marks penalty tariff and anomaly states', () => {
    const onSelect = vi.fn();
    const { container } = render(<HomeCard home={anomalousHome} onSelect={onSelect} />);

    const card = container.querySelector('.home-card');
    expect(card).toHaveAttribute('data-quota-state', 'critical');
    expect(card).toHaveAttribute('data-anomaly', 'true');
    expect(card).toHaveClass('home-card--penalty', 'home-card--anomaly');
    expect(screen.getByText('1 aktif anomali')).toBeInTheDocument();
    expect(screen.getByRole('progressbar', { name: 'Moda Evi bütçe kullanımı' })).toHaveAttribute(
      'aria-valuenow',
      '100',
    );
    expect(screen.getByText('%112,5')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Moda Evi detaylarını aç' }));
    expect(onSelect).toHaveBeenCalledWith(anomalousHome);
  });
});

describe('public VoltFlow routes', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    window.history.replaceState({}, '', '/');
  });

  it('keeps the landing page public and opens the real password registration experience', async () => {
    setStoredToken(null);
    window.history.replaceState({}, '', '/');
    const user = userEvent.setup();

    render(<App />);

    expect(
      screen.getByRole('heading', {
        name: /Enerjiyi izlemek değil, anlamak için/i,
      }),
    ).toBeInTheDocument();
    await user.click(
      screen.getByRole('button', { name: /Evimi izlemeye başla/i }),
    );

    expect(window.location.pathname).toBe('/register');
    expect(
      await screen.findByRole('heading', { name: 'VoltFlow’a Katılın' }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText('Şifre')).toBeInTheDocument();
    expect(screen.getByLabelText('Şifre tekrarı')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: /google|oauth|sosyal/i }),
    ).not.toBeInTheDocument();
  });

  it('allows an authenticated visitor to view the public landing page', () => {
    setStoredToken('header.payload.signature');
    window.history.replaceState({}, '', '/');

    render(<App />);

    expect(
      screen.getByRole('heading', {
        name: /Enerjiyi izlemek değil, anlamak için/i,
      }),
    ).toBeInTheDocument();
    expect(window.location.pathname).toBe('/');
  });

  it('sends an authenticated landing-page visitor straight to the dashboard', async () => {
    setStoredToken('header.payload.signature');
    window.history.replaceState({}, '', '/');
    vi.spyOn(api, 'getHomeStatuses').mockResolvedValue([]);
    const user = userEvent.setup();

    render(<App />);
    await user.click(screen.getByRole('button', { name: 'Giriş yap' }));

    expect(
      await screen.findByRole('heading', {
        name: /Evinizin enerjisi, tek bakışta anlaşılır/i,
      }),
    ).toBeInTheDocument();
    expect(window.location.pathname).toBe('/dashboard');
  });

  it('redirects unauthenticated dashboard navigation to login', async () => {
    setStoredToken(null);
    window.history.replaceState({}, '', '/dashboard');

    render(<App />);

    expect(
      await screen.findByRole('heading', { name: 'Hesabınıza Giriş Yapın' }),
    ).toBeInTheDocument();
    expect(window.location.pathname).toBe('/login');
  });

  it('redirects authenticated login navigation to the dashboard', async () => {
    setStoredToken('header.payload.signature');
    window.history.replaceState({}, '', '/login');
    vi.spyOn(api, 'getHomeStatuses').mockResolvedValue([]);

    render(<App />);

    expect(
      await screen.findByRole('heading', {
        name: /Evinizin enerjisi, tek bakışta anlaşılır/i,
      }),
    ).toBeInTheDocument();
    expect(window.location.pathname).toBe('/dashboard');
  });
});
