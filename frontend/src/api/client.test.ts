import { afterEach, describe, expect, it, vi } from 'vitest';
import { api, getStoredToken, setStoredToken } from './client';

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('VoltFlow API client', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    setStoredToken(null);
  });

  it('persists a valid JWT returned by password login', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        token: 'header.payload.signature',
        user: { id: 4, email: 'owner@example.com' },
        message: 'Giriş başarılı.',
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    const response = await api.login('owner@example.com', 'securePassword');

    expect(response.user).toEqual({ id: 4, email: 'owner@example.com' });
    expect(getStoredToken()).toBe('header.payload.signature');
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/auth/login'),
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({
          email: 'owner@example.com',
          password: 'securePassword',
        }),
      }),
    );
  });

  it('persists the registration JWT and rejects malformed auth responses', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        jsonResponse({
          token: 'header.payload.signature',
          user: { id: 8, email: 'new@example.com' },
          message: 'Hesabınız oluşturuldu.',
        }, 201),
      )
      .mockResolvedValueOnce(
        jsonResponse({
          token: 'not-a-jwt',
          user: { id: 8, email: 'new@example.com' },
        }),
      );
    vi.stubGlobal('fetch', fetchMock);

    await api.register('new@example.com', 'securePassword');
    expect(getStoredToken()).toBe('header.payload.signature');

    setStoredToken(null);
    await expect(
      api.login('new@example.com', 'securePassword'),
    ).rejects.toMatchObject({
      message: 'Güvenli oturum oluşturulamadı. Lütfen yeniden deneyin.',
    });
    expect(getStoredToken()).toBeNull();
  });

  it('uses a generic login error without triggering the expired-session event', async () => {
    const unauthorizedListener = vi.fn();
    window.addEventListener('voltflow_unauthorized', unauthorizedListener);
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse(
          { message: 'Account not found' },
          401,
        ),
      ),
    );

    await expect(
      api.login('missing@example.com', 'wrongPassword'),
    ).rejects.toMatchObject({
      message: 'E-posta adresi veya şifre hatalı.',
      status: 401,
    });
    expect(unauthorizedListener).not.toHaveBeenCalled();
    window.removeEventListener('voltflow_unauthorized', unauthorizedListener);
  });

  it('normalizes the concrete paged home status contract', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        content: [
          {
            homeId: 41,
            homeName: 'Sahil Evi',
            currentPowerWatts: 935.4,
            accumulatedEnergyKwh: 18.22,
            currentCost: 72.88,
            monthlyBudget: 900,
            budgetUsagePercent: 8.1,
            tariffState: 'NORMAL',
            anomalyCount: 0,
            lastUpdatedAt: '2026-07-21T12:00:00Z',
            appliances: [
              {
                applianceId: 7,
                name: 'Mutfak Su Isıtıcısı',
                type: 'KETTLE',
                currentPowerWatts: 900,
                accumulatedEnergyKwh: 1.4,
                accumulatedCost: 5.6,
                operatingState: 'ON',
                safePowerLimitWatts: 2400,
                consecutiveBreachCount: 0,
                healthStatus: 'NORMAL',
              },
            ],
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    const homes = await api.getHomeStatuses();

    expect(homes).toHaveLength(1);
    expect(homes[0]).toMatchObject({
      homeId: 41,
      homeName: 'Sahil Evi',
      currentPowerWatts: 935.4,
      tariffState: 'NORMAL',
    });
    expect(homes[0].appliances[0]).toMatchObject({
      applianceId: 7,
      type: 'KETTLE',
      operatingState: 'ON',
      healthStatus: 'NORMAL',
    });
    expect(String(fetchMock.mock.calls[0][0])).toContain('/homes/status?page=0&size=100');
  });

  it('retrieves every declared home status page with one caller cancellation signal', async () => {
    const controller = new AbortController();
    const firstPage = Array.from({ length: 100 }, (_, index) => ({
      homeId: index + 1,
      homeName: `Ev ${index + 1}`,
      monthlyBudget: 1000,
      tariffState: 'NORMAL',
      appliances: [],
    }));
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        jsonResponse({ content: firstPage, page: 0, size: 100, totalElements: 101, totalPages: 2 }),
      )
      .mockResolvedValueOnce(
        jsonResponse({
          content: [{ homeId: 101, homeName: 'Son Ev', monthlyBudget: 1000, tariffState: 'NORMAL', appliances: [] }],
          page: 1,
          size: 100,
          totalElements: 101,
          totalPages: 2,
        }),
      );
    vi.stubGlobal('fetch', fetchMock);

    const homes = await api.getHomeStatuses(controller.signal);

    expect(homes).toHaveLength(101);
    expect(homes.at(-1)).toMatchObject({ homeId: 101, homeName: 'Son Ev' });
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(String(fetchMock.mock.calls[1][0])).toContain('/homes/status?page=1&size=100');
    const firstFetchSignal = (fetchMock.mock.calls[0][1] as RequestInit).signal as AbortSignal;
    const secondFetchSignal = (fetchMock.mock.calls[1][1] as RequestInit).signal as AbortSignal;
    expect(firstFetchSignal).toBe(secondFetchSignal);
  });

  it('includes the content of a page marked as last and then stops', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        content: [{ homeId: 9, homeName: 'Son Sayfa Evi', monthlyBudget: 1000, appliances: [] }],
        page: 0,
        size: 100,
        totalPages: 20,
        last: true,
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    const homes = await api.getHomeStatuses();

    expect(homes).toHaveLength(1);
    expect(homes[0]).toMatchObject({ homeId: 9, homeName: 'Son Sayfa Evi' });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('stops malformed pagination instead of repeatedly accepting the same full page', async () => {
    const repeatedPage = Array.from({ length: 100 }, (_, index) => ({
      homeId: index + 1,
      homeName: `Ev ${index + 1}`,
      monthlyBudget: 1000,
      tariffState: 'NORMAL',
      appliances: [],
    }));
    const fetchMock = vi.fn().mockImplementation(() =>
      Promise.resolve(jsonResponse({ content: repeatedPage, page: 0, size: 100 })),
    );
    vi.stubGlobal('fetch', fetchMock);

    await expect(api.getHomeStatuses()).rejects.toThrow('yinelenen bir yanıt');
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('maps concrete event and recommendation page fields', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        jsonResponse({
          content: [
            {
              eventId: 'evt-12',
              eventType: 'ANOMALY_DETECTED',
              occurredAt: '2026-07-21T12:03:00Z',
              status: 'ACTIVE',
              measuredPowerWatts: 2550,
              applianceId: 7,
              details: 'Su ısıtıcısında üç ardışık sınır aşımı algılandı.',
            },
          ],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
        }),
      )
      .mockResolvedValueOnce(
        jsonResponse({
          content: [
            {
              id: 99,
              recommendationText: 'Cihazı kapatıp güvenli bağlantıyı kontrol edin.',
              triggerType: 'APPLIANCE_ANOMALY',
              createdAt: '2026-07-21T12:04:00Z',
              fallbackUsed: false,
            },
          ],
          page: 0,
          size: 10,
          totalElements: 1,
          totalPages: 1,
        }),
      );
    vi.stubGlobal('fetch', fetchMock);

    const events = await api.getEvents(41);
    const recommendations = await api.getRecommendations(41);

    expect(events[0]).toMatchObject({
      id: 'evt-12',
      type: 'ANOMALY',
      status: 'ACTIVE',
      description: 'Su ısıtıcısında üç ardışık sınır aşımı algılandı.',
    });
    expect(recommendations[0]).toMatchObject({
      id: 99,
      text: 'Cihazı kapatıp güvenli bağlantıyı kontrol edin.',
      triggerType: 'APPLIANCE_ANOMALY',
    });
  });

  it('requests every possible hourly bucket in the seven-day history window', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({ content: [], page: 0, size: 200, totalPages: 0 }),
    );
    vi.stubGlobal('fetch', fetchMock);

    await api.getHistory(41);

    expect(String(fetchMock.mock.calls[0][0])).toContain(
      '/homes/41/history?',
    );
    expect(String(fetchMock.mock.calls[0][0])).toContain('size=200');
  });

  it('normalizes indexed backend field-error paths for registration fields', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse(
          {
            message: 'Geçersiz istek',
            fieldErrors: {
              'appliances[0].name': 'Cihaz adı çok uzun.',
            },
          },
          400,
        ),
      ),
    );

    const request = api.registerHome({
      name: 'Ev',
      city: 'İstanbul',
      contactEmail: 'owner@example.com',
      monthlyBudget: 1000,
      normalTariffPerKwh: 2.5,
      penaltyMultiplier: 1.5,
      appliances: [
        {
          name: 'Cihaz',
          type: 'LAMP',
          safePowerLimitWatts: 60,
        },
      ],
    });

    await expect(request).rejects.toMatchObject({
      fieldErrors: {
        'appliances.0.name': 'Cihaz adı çok uzun.',
      },
    });
  });

  it('does not expose technical backend exception text to users', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse(
          {
            message:
              'java.lang.IllegalStateException: caused by org.springframework failure',
          },
          400,
        ),
      ),
    );

    await expect(
      api.register('owner@example.com', 'securePassword'),
    ).rejects.toMatchObject({
      message: 'İstek tamamlanamadı.',
    });
  });
});
