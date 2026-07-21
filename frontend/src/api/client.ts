import type {
  ApplianceHealthStatus,
  ApplianceStatus,
  ApplianceType,
  FieldErrors,
  HistoryPoint,
  HomeEvent,
  HomeEventType,
  HomeRegistrationRequest,
  HomeStatus,
  OperatingState,
  Recommendation,
  TariffState,
} from '../types';
import { APPLIANCE_TYPES } from '../types';

const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim();
export const API_BASE_URL = (configuredBaseUrl || '/api/v1').replace(/\/$/, '');

const REQUEST_TIMEOUT_MS = 10_000;
const HOME_STATUS_PAGE_SIZE = 100;
const MAX_HOME_STATUS_PAGES = 1_000;
const operatingStates = new Set<OperatingState>(['OFF', 'STANDBY', 'ON', 'HIGH_LOAD']);
const healthStates = new Set<ApplianceHealthStatus>(['NORMAL', 'ANOMALOUS']);
const tariffStates = new Set<TariffState>(['NORMAL', 'PENALTY']);
const applianceTypes = new Set<string>(APPLIANCE_TYPES);

type UnknownRecord = Record<string, unknown>;

export class ApiError extends Error {
  readonly status: number;
  readonly fieldErrors: FieldErrors;

  constructor(message: string, status = 0, fieldErrors: FieldErrors = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

function isRecord(value: unknown): value is UnknownRecord {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function nestedRecord(value: unknown): UnknownRecord {
  return isRecord(value) ? value : {};
}

function firstDefined(source: UnknownRecord, keys: string[]): unknown {
  for (const key of keys) {
    if (source[key] !== undefined && source[key] !== null) return source[key];
  }
  return undefined;
}

function asString(value: unknown, fallback = ''): string {
  return typeof value === 'string' ? value : value == null ? fallback : String(value);
}

function asNumber(value: unknown, fallback = 0): number {
  const number = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(number) ? number : fallback;
}

function asEnum<T extends string>(value: unknown, allowed: Set<T>, fallback: T): T {
  const candidate = asString(value).toUpperCase() as T;
  return allowed.has(candidate) ? candidate : fallback;
}

function extractItems(payload: unknown): unknown[] {
  if (Array.isArray(payload)) return payload;
  if (!isRecord(payload)) return [];

  for (const key of ['content', 'items', 'results']) {
    if (Array.isArray(payload[key])) return payload[key] as unknown[];
  }

  if (isRecord(payload.data)) return extractItems(payload.data);
  if (Array.isArray(payload.data)) return payload.data;
  return [];
}

interface PageMetadata {
  totalPages?: number;
  last?: boolean;
}

function nonNegativeInteger(value: unknown): number | undefined {
  const parsed = typeof value === 'number' ? value : Number(value);
  return Number.isSafeInteger(parsed) && parsed >= 0 ? parsed : undefined;
}

function extractPageMetadata(payload: unknown): PageMetadata {
  const root = nestedRecord(payload);
  const page = isRecord(root.data) ? root.data : root;
  return {
    totalPages: nonNegativeInteger(page.totalPages),
    last: typeof page.last === 'boolean' ? page.last : undefined,
  };
}

interface LinkedSignal {
  signal: AbortSignal;
  dispose: () => void;
  timedOut: () => boolean;
}

function createLinkedSignal(external?: AbortSignal): LinkedSignal {
  const controller = new AbortController();
  let didTimeOut = false;
  const abortFromExternal = () => controller.abort(external?.reason);
  if (external?.aborted) controller.abort(external.reason);
  else external?.addEventListener('abort', abortFromExternal, { once: true });

  const timeoutId = window.setTimeout(() => {
    didTimeOut = true;
    controller.abort('timeout');
  }, REQUEST_TIMEOUT_MS);
  return {
    signal: controller.signal,
    timedOut: () => didTimeOut,
    dispose: () => {
      window.clearTimeout(timeoutId);
      external?.removeEventListener('abort', abortFromExternal);
    },
  };
}

async function readPayload(response: Response): Promise<unknown> {
  if (response.status === 204) return null;
  const contentType = response.headers.get('content-type') ?? '';
  if (!contentType.includes('application/json')) return null;
  try {
    return await response.json();
  } catch {
    return null;
  }
}

async function request<T>(
  path: string,
  options: RequestInit = {},
  externalSignal?: AbortSignal,
  sharedSignal?: LinkedSignal,
): Promise<T> {
  const linkedSignal = sharedSignal ?? createLinkedSignal(externalSignal);
  const { signal, timedOut } = linkedSignal;
  try {
    const response = await fetch(`${API_BASE_URL}${path}`, {
      ...options,
      signal,
      headers: {
        Accept: 'application/json',
        ...(options.body ? { 'Content-Type': 'application/json' } : {}),
        ...options.headers,
      },
    });
    const payload = await readPayload(response);

    if (!response.ok) {
      const errorBody = nestedRecord(payload);
      const fieldErrors = isRecord(errorBody.fieldErrors)
        ? Object.fromEntries(
            Object.entries(errorBody.fieldErrors).map(([key, value]) => [key, asString(value, 'Geçersiz değer')]),
          )
        : {};
      const serverMessage = asString(errorBody.message);
      const safeMessage =
        response.status === 400 && serverMessage
          ? serverMessage
          : response.status === 404
            ? 'İstenen kayıt bulunamadı.'
            : response.status >= 500
              ? 'VoltWise servisine şu anda ulaşılamıyor.'
              : serverMessage || 'İstek tamamlanamadı.';
      throw new ApiError(safeMessage, response.status, fieldErrors);
    }

    return payload as T;
  } catch (error) {
    if (error instanceof ApiError) throw error;
    if (signal.aborted) {
      if (timedOut()) throw new ApiError('İstek zaman aşımına uğradı. Lütfen yeniden deneyin.');
      throw new DOMException('İstek iptal edildi', 'AbortError');
    }
    throw new ApiError('Ağ bağlantısı kurulamadı. Lütfen bağlantınızı kontrol edin.');
  } finally {
    if (!sharedSignal) linkedSignal.dispose();
  }
}

function normalizeAppliance(rawValue: unknown, index: number): ApplianceStatus {
  const raw = nestedRecord(rawValue);
  const live = { ...raw, ...nestedRecord(raw.liveState), ...nestedRecord(raw.status) };
  const typeValue = asString(firstDefined(live, ['type', 'applianceType'])).toUpperCase();
  const type = (applianceTypes.has(typeValue) ? typeValue : 'LAMP') as ApplianceType;
  return {
    applianceId: asNumber(firstDefined(live, ['applianceId', 'id']), index + 1),
    name: asString(firstDefined(live, ['name', 'applianceName']), `${type} ${index + 1}`),
    type,
    currentPowerWatts: asNumber(firstDefined(live, ['currentPowerWatts', 'powerWatts'])),
    accumulatedEnergyKwh: asNumber(firstDefined(live, ['accumulatedEnergyKwh', 'energyKwh'])),
    accumulatedCost: asNumber(firstDefined(live, ['accumulatedCost', 'currentCost', 'cost'])),
    operatingState: asEnum(
      firstDefined(live, ['operatingState', 'state']),
      operatingStates,
      'OFF',
    ),
    safePowerLimitWatts: asNumber(firstDefined(live, ['safePowerLimitWatts', 'safePowerLimit'])),
    consecutiveBreachCount: asNumber(firstDefined(live, ['consecutiveBreachCount', 'breachCount'])),
    healthStatus: asEnum(
      firstDefined(live, ['healthStatus', 'status']),
      healthStates,
      'NORMAL',
    ),
    lastUpdatedAt: asString(firstDefined(live, ['lastUpdatedAt', 'updatedAt'])) || undefined,
  };
}

export function normalizeHomeStatus(rawValue: unknown, index = 0): HomeStatus {
  const raw = nestedRecord(rawValue);
  const home = nestedRecord(raw.home);
  const live = { ...home, ...raw, ...nestedRecord(raw.liveState), ...nestedRecord(raw.status) };
  const applianceSource = firstDefined(live, [
    'appliances',
    'applianceStatuses',
    'applianceLiveStates',
  ]);
  const appliances = Array.isArray(applianceSource)
    ? applianceSource.map(normalizeAppliance)
    : [];
  const anomalyCountValue = firstDefined(live, ['anomalyCount', 'activeAnomalyCount']);
  return {
    homeId: asNumber(firstDefined(live, ['homeId', 'id']), index + 1),
    homeName: asString(firstDefined(live, ['homeName', 'name']), `Ev ${index + 1}`),
    currentPowerWatts: asNumber(firstDefined(live, ['currentPowerWatts', 'totalPowerWatts'])),
    accumulatedEnergyKwh: asNumber(firstDefined(live, ['accumulatedEnergyKwh', 'energyKwh'])),
    currentCost: asNumber(firstDefined(live, ['currentCost', 'accumulatedCost', 'cost'])),
    monthlyBudget: asNumber(firstDefined(live, ['monthlyBudget', 'budget'])),
    budgetUsagePercent: asNumber(firstDefined(live, ['budgetUsagePercent', 'usagePercent'])),
    tariffState: asEnum(firstDefined(live, ['tariffState', 'tariff']), tariffStates, 'NORMAL'),
    anomalyCount:
      anomalyCountValue !== undefined
        ? asNumber(anomalyCountValue)
        : appliances.filter((appliance) => appliance.healthStatus === 'ANOMALOUS').length,
    lastUpdatedAt: asString(firstDefined(live, ['lastUpdatedAt', 'updatedAt'])) || undefined,
    appliances,
  };
}

function normalizeHistoryPoint(value: unknown, index: number): HistoryPoint {
  const raw = nestedRecord(value);
  return {
    id: (firstDefined(raw, ['id', 'snapshotId']) as number | string | undefined) ?? index,
    periodStart: asString(firstDefined(raw, ['periodStart', 'timestamp', 'occurredAt', 'createdAt'])),
    periodEnd: asString(raw.periodEnd) || undefined,
    energyKwh: asNumber(firstDefined(raw, ['energyKwh', 'accumulatedEnergyKwh'])),
    cost: asNumber(firstDefined(raw, ['cost', 'accumulatedCost', 'currentCost'])),
    averagePowerWatts: asNumber(raw.averagePowerWatts),
    maximumPowerWatts: asNumber(raw.maximumPowerWatts),
    applianceId: raw.applianceId === undefined ? undefined : asNumber(raw.applianceId),
  };
}

function eventDescription(raw: UnknownRecord, type: HomeEventType): string {
  if (typeof raw.description === 'string') return raw.description;
  if (typeof raw.message === 'string') return raw.message;
  if (typeof raw.details === 'string') return raw.details;
  if (type === 'QUOTA') {
    return `Bütçe kullanımı %${asNumber(firstDefined(raw, ['usagePercent', 'budgetUsagePercent'])).toFixed(0)} seviyesine ulaştı.`;
  }
  if (type === 'ANOMALY') {
    const reading = asNumber(firstDefined(raw, ['measuredPowerWatts', 'powerWatts']));
    const limit = asNumber(firstDefined(raw, ['safePowerLimitWatts', 'safeLimitWatts']));
    return `Cihaz tüketimi ${reading.toFixed(0)} W olarak ölçüldü; güvenli sınır ${limit.toFixed(0)} W.`;
  }
  if (type === 'TARIFF') return 'Bütçe sınırı nedeniyle tarife durumu değişti.';
  return 'Enerji durumunda bir değişiklik kaydedildi.';
}

function normalizeEvent(value: unknown, index: number, typeHint?: HomeEventType): HomeEvent {
  const raw = nestedRecord(value);
  const rawType = asString(firstDefined(raw, ['type', 'eventType', 'threshold'])).toUpperCase();
  const type: HomeEventType =
    typeHint ??
    (rawType.includes('ANOMAL')
      ? 'ANOMALY'
      : rawType.includes('QUOTA') || rawType.includes('PERCENT')
        ? 'QUOTA'
        : rawType.includes('TARIFF')
          ? 'TARIFF'
          : 'UNKNOWN');
  const titles: Record<HomeEventType, string> = {
    QUOTA: 'Bütçe eşiği',
    ANOMALY: 'Olağan dışı tüketim',
    TARIFF: 'Tarife değişikliği',
    UNKNOWN: 'Sistem olayı',
  };
  return {
    id: (firstDefined(raw, ['id', 'eventId']) as number | string | undefined) ?? `${type}-${index}`,
    type,
    title: asString(raw.title, titles[type]),
    description: eventDescription(raw, type),
    occurredAt: asString(firstDefined(raw, ['occurredAt', 'detectedAt', 'changedAt', 'createdAt'])),
    resolvedAt: asString(raw.resolvedAt) || undefined,
    status: asString(raw.status) || undefined,
  };
}

function normalizeEvents(payload: unknown): HomeEvent[] {
  if (isRecord(payload)) {
    const grouped: HomeEvent[] = [];
    const groups: Array<[string, HomeEventType]> = [
      ['quotaEvents', 'QUOTA'],
      ['anomalyEvents', 'ANOMALY'],
      ['tariffEvents', 'TARIFF'],
      ['tariffChangeEvents', 'TARIFF'],
    ];
    groups.forEach(([key, type]) => {
      if (Array.isArray(payload[key])) {
        (payload[key] as unknown[]).forEach((event, index) => grouped.push(normalizeEvent(event, index, type)));
      }
    });
    if (grouped.length) {
      return grouped.sort((a, b) => Date.parse(b.occurredAt) - Date.parse(a.occurredAt));
    }
  }
  return extractItems(payload).map((event, index) => normalizeEvent(event, index));
}

function normalizeRecommendation(value: unknown, index: number): Recommendation {
  const raw = nestedRecord(value);
  return {
    id: (firstDefined(raw, ['id', 'recommendationId']) as number | string | undefined) ?? index,
    text: asString(firstDefined(raw, ['recommendationText', 'text', 'content'])),
    triggerType: asString(raw.triggerType) || undefined,
    createdAt: asString(firstDefined(raw, ['createdAt', 'occurredAt'])),
    fallbackUsed: Boolean(raw.fallbackUsed),
  };
}

export const api = {
  async getHomeStatuses(signal?: AbortSignal): Promise<HomeStatus[]> {
    const homes: HomeStatus[] = [];
    const seenPages = new Set<string>();
    const paginationSignal = createLinkedSignal(signal);

    try {
      for (let page = 0; page < MAX_HOME_STATUS_PAGES; page += 1) {
        const payload = await request<unknown>(
          `/homes/status?page=${page}&size=${HOME_STATUS_PAGE_SIZE}`,
          {},
          undefined,
          paginationSignal,
        );
        const items = extractItems(payload);
        const metadata = extractPageMetadata(payload);

        if (metadata.totalPages !== undefined && metadata.totalPages > MAX_HOME_STATUS_PAGES) {
          throw new ApiError('Ev listesi güvenli sayfalama sınırını aştı.');
        }

        if (items.length) {
          const fingerprint = JSON.stringify(items);
          if (seenPages.has(fingerprint)) {
            throw new ApiError('Ev listesi sayfalanırken yinelenen bir yanıt alındı.');
          }
          seenPages.add(fingerprint);
          homes.push(...items.map((item, index) => normalizeHomeStatus(item, homes.length + index)));
        }

        const reachedDeclaredEnd =
          metadata.totalPages !== undefined && page + 1 >= metadata.totalPages;
        const reachedImplicitEnd =
          metadata.totalPages === undefined && items.length < HOME_STATUS_PAGE_SIZE;
        if (!items.length || metadata.last === true || reachedDeclaredEnd || reachedImplicitEnd) {
          return homes;
        }
      }

      throw new ApiError('Ev listesi güvenli sayfalama sınırını aştı.');
    } finally {
      paginationSignal.dispose();
    }
  },

  async getHomeStatus(homeId: number, signal?: AbortSignal): Promise<HomeStatus> {
    const payload = await request<unknown>(`/homes/${homeId}/status`, {}, signal);
    return normalizeHomeStatus(payload);
  },

  async getHistory(homeId: number, signal?: AbortSignal): Promise<HistoryPoint[]> {
    const to = new Date();
    const from = new Date(to.getTime() - 7 * 24 * 60 * 60 * 1000);
    const query = new URLSearchParams({
      from: from.toISOString(),
      to: to.toISOString(),
      bucket: 'HOUR',
      page: '0',
      size: '168',
    });
    const payload = await request<unknown>(`/homes/${homeId}/history?${query}`, {}, signal);
    return extractItems(payload).map(normalizeHistoryPoint);
  },

  async getEvents(homeId: number, signal?: AbortSignal): Promise<HomeEvent[]> {
    const payload = await request<unknown>(`/homes/${homeId}/events?page=0&size=20`, {}, signal);
    return normalizeEvents(payload);
  },

  async getRecommendations(homeId: number, signal?: AbortSignal): Promise<Recommendation[]> {
    const payload = await request<unknown>(`/homes/${homeId}/recommendations?page=0&size=10`, {}, signal);
    return extractItems(payload).map(normalizeRecommendation);
  },

  async registerHome(payload: HomeRegistrationRequest, signal?: AbortSignal): Promise<HomeStatus | null> {
    const response = await request<unknown>(
      '/homes',
      { method: 'POST', body: JSON.stringify(payload) },
      signal,
    );
    return response ? normalizeHomeStatus(response) : null;
  },
};

export function getUserFacingError(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  return 'Beklenmeyen bir sorun oluştu. Lütfen yeniden deneyin.';
}
