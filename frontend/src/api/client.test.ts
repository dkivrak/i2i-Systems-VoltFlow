import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from './client';

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('VoltWise API client', () => {
  afterEach(() => vi.restoreAllMocks());

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
});
