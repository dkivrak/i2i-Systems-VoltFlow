import {
  Activity,
  AlertTriangle,
  BellRing,
  CheckCircle2,
  CircleDollarSign,
  Clock3,
  Gauge,
  House,
  Lightbulb,
  MapPin,
  PlugZap,
  RefreshCw,
  ShieldCheck,
  Sparkles,
  Trash2,
  Zap,
} from 'lucide-react';
import {
  useCallback,
  useEffect,
  useId,
  useMemo,
  useRef,
  useState,
} from 'react';
import { api, getUserFacingError } from '../api/client';
import { getPollingInterval, usePollingResource } from '../hooks/usePollingResource';
import {
  getTelemetryFreshness,
  presentAppliance,
  type AppliancePresentation,
  type TelemetryFreshness,
} from '../presentation/appliancePresentation';
import type { HistoryPoint, HomeEvent, HomeStatus, Recommendation } from '../types';
import {
  formatDateTime,
  formatEnergy,
  formatMoney,
  formatPercent,
  formatPower,
  tariffLabels,
} from '../utils/format';
import { Dialog } from './Dialog';
import { EnergyCharts } from './EnergyCharts';
import { ErrorState, InlineSpinner } from './PageStates';
import { ApplianceCharacterGrid } from './home-detail/ApplianceCharacterGrid';
import { ApplianceTelemetryPanel } from './home-detail/ApplianceTelemetryPanel';
import { DetailTabs, type HomeDetailTab } from './home-detail/DetailTabs';

interface HomeDetailModalProps {
  summary: HomeStatus;
  onClose: () => void;
  onDeleted?: () => void;
}

type AnalyticsSource = 'history' | 'events' | 'recommendations';

interface AnalyticsState {
  history: HistoryPoint[];
  events: HomeEvent[];
  recommendations: Recommendation[];
  isLoading: boolean;
  failedSources: AnalyticsSource[];
  error?: string;
}

type DeleteTarget =
  | { kind: 'home'; name: string }
  | { kind: 'appliance'; applianceId: number; name: string };

const eventIcons = {
  QUOTA: Gauge,
  ANOMALY: AlertTriangle,
  TARIFF: CircleDollarSign,
  UNKNOWN: BellRing,
} as const;

function connectionLabel(
  freshness: TelemetryFreshness,
  hasRequestError: boolean,
): string {
  if (freshness === 'disconnected') return 'Canlı bağlantı kesildi';
  if (freshness === 'stale') return 'Telemetri gecikmiş';
  if (freshness === 'missing') return 'İlk telemetri bekleniyor';
  if (hasRequestError) return 'Bağlantı yeniden kuruluyor';
  return 'Canlı bağlantı güncel';
}

export function HomeDetailModal({
  summary,
  onClose,
  onDeleted,
}: HomeDetailModalProps) {
  const homeId = summary.homeId;
  const detailRequest = useCallback(
    (signal: AbortSignal) => api.getHomeStatus(homeId, signal),
    [homeId],
  );
  const live = usePollingResource(detailRequest, getPollingInterval(), true, summary);
  const retryLive = live.retry;
  const home = live.data ?? summary;

  const [analyticsVersion, setAnalyticsVersion] = useState(0);
  const [analytics, setAnalytics] = useState<AnalyticsState>({
    history: [],
    events: [],
    recommendations: [],
    isLoading: true,
    failedSources: [],
  });
  const analyticsController = useRef<AbortController | null>(null);

  useEffect(() => {
    analyticsController.current?.abort();
    const controller = new AbortController();
    analyticsController.current = controller;
    setAnalytics((current) => ({
      ...current,
      isLoading: true,
      failedSources: [],
      error: undefined,
    }));

    void Promise.allSettled([
      api.getHistory(homeId, controller.signal),
      api.getEvents(homeId, controller.signal),
      api.getRecommendations(homeId, controller.signal),
    ]).then((results) => {
      if (controller.signal.aborted) return;
      const [historyResult, eventsResult, recommendationsResult] = results;
      const failedSources: AnalyticsSource[] = [];
      if (historyResult.status === 'rejected') failedSources.push('history');
      if (eventsResult.status === 'rejected') failedSources.push('events');
      if (recommendationsResult.status === 'rejected') {
        failedSources.push('recommendations');
      }
      const rejected = results.find((result) => result.status === 'rejected');

      setAnalytics({
        history: historyResult.status === 'fulfilled' ? historyResult.value : [],
        events: eventsResult.status === 'fulfilled' ? eventsResult.value : [],
        recommendations:
          recommendationsResult.status === 'fulfilled'
            ? recommendationsResult.value
            : [],
        isLoading: false,
        failedSources,
        error:
          rejected?.status === 'rejected'
            ? getUserFacingError(rejected.reason)
            : undefined,
      });
    });

    return () => controller.abort();
  }, [analyticsVersion, homeId]);

  const [activeTab, setActiveTab] = useState<HomeDetailTab>('overview');
  const [selectedApplianceId, setSelectedApplianceId] = useState<number | null>(
    summary.appliances[0]?.applianceId ?? null,
  );
  const [deletingHome, setDeletingHome] = useState(false);
  const [deletingApplianceId, setDeletingApplianceId] = useState<number | null>(
    null,
  );
  const [deleteTarget, setDeleteTarget] = useState<DeleteTarget | null>(null);
  const [mutationError, setMutationError] = useState('');
  const cancelDeleteRef = useRef<HTMLButtonElement>(null);
  const deleteTriggerRef = useRef<HTMLElement | null>(null);
  const tabId = useId();
  const deletePromptId = useId();

  useEffect(() => {
    setSelectedApplianceId((current) => {
      if (
        current !== null &&
        home.appliances.some((appliance) => appliance.applianceId === current)
      ) {
        return current;
      }
      return home.appliances[0]?.applianceId ?? null;
    });
  }, [home.appliances]);

  const freshnessEpoch = Math.floor(Date.now() / 5_000) * 5_000;
  const appliancePresentations = useMemo<AppliancePresentation[]>(() => {
    const now = freshnessEpoch || Date.now();
    return home.appliances.map((appliance) =>
      presentAppliance(appliance, {
        now,
        connectionError: Boolean(live.error),
      }),
    );
  }, [freshnessEpoch, home.appliances, live.error]);

  const selectedAppliance = useMemo(
    () =>
      appliancePresentations.find(
        (item) => item.appliance.applianceId === selectedApplianceId,
      ),
    [appliancePresentations, selectedApplianceId],
  );

  const homeFreshness = getTelemetryFreshness(home.lastUpdatedAt, {
    now: freshnessEpoch || Date.now(),
    connectionError: Boolean(live.error),
  });
  const activeApplianceCount = home.appliances.filter(
    (appliance) =>
      appliance.operatingState === 'ON' ||
      appliance.operatingState === 'HIGH_LOAD',
  ).length;
  const quotaProgress = Math.min(Math.max(home.budgetUsagePercent, 0), 100);
  const isQuotaWarning = home.budgetUsagePercent >= 80;
  const isQuotaCritical =
    home.budgetUsagePercent >= 100 || home.tariffState === 'PENALTY';

  const retryAnalytics = useCallback(
    () => setAnalyticsVersion((version) => version + 1),
    [],
  );
  const activateTab = useCallback(
    (tab: HomeDetailTab) => {
      setActiveTab(tab);
      window.requestAnimationFrame(() => {
        document.getElementById(`${tabId}-tab-${tab}`)?.focus();
      });
    },
    [tabId],
  );

  useEffect(() => {
    if (!deleteTarget) return;
    const frame = window.requestAnimationFrame(() =>
      cancelDeleteRef.current?.focus(),
    );
    return () => window.cancelAnimationFrame(frame);
  }, [deleteTarget]);

  const requestDeleteHome = () => {
    if (deleteTarget || deletingHome || deletingApplianceId !== null) return;
    deleteTriggerRef.current =
      document.activeElement instanceof HTMLElement
        ? document.activeElement
        : null;
    setMutationError('');
    setDeleteTarget({ kind: 'home', name: home.homeName });
  };

  const requestDeleteAppliance = useCallback(
    (applianceId: number, applianceName: string) => {
      if (deleteTarget || deletingHome || deletingApplianceId !== null) return;
      deleteTriggerRef.current =
        document.activeElement instanceof HTMLElement
          ? document.activeElement
          : null;
      setMutationError('');
      setDeleteTarget({
        kind: 'appliance',
        applianceId,
        name: applianceName,
      });
    },
    [deleteTarget, deletingApplianceId, deletingHome],
  );

  const cancelDelete = () => {
    setDeleteTarget(null);
    setMutationError('');
    window.requestAnimationFrame(() => deleteTriggerRef.current?.focus());
  };

  const confirmDelete = async () => {
    const target = deleteTarget;
    if (!target) return;

    setMutationError('');
    if (target.kind === 'home') {
      try {
        setDeletingHome(true);
        await api.deleteHome(homeId);
        setDeleteTarget(null);
        setDeletingHome(false);
        onDeleted?.();
        onClose();
      } catch (error) {
        setMutationError(getUserFacingError(error));
        setDeletingHome(false);
      }
      return;
    }

    try {
      setDeletingApplianceId(target.applianceId);
      await api.deleteAppliance(homeId, target.applianceId);
      if (selectedApplianceId === target.applianceId) {
        const nextAppliance = home.appliances.find(
          (appliance) => appliance.applianceId !== target.applianceId,
        );
        setSelectedApplianceId(nextAppliance?.applianceId ?? null);
      }
      setDeleteTarget(null);
      retryLive();
    } catch (error) {
      setMutationError(getUserFacingError(error));
    } finally {
      setDeletingApplianceId(null);
    }
  };

  const selectAppliance = useCallback((applianceId: number) => {
    setSelectedApplianceId(applianceId);
  }, []);

  return (
    <Dialog
      title={home.homeName}
      eyebrow="Canlı ev görünümü"
      description={`Son veri: ${formatDateTime(home.lastUpdatedAt)}`}
      onClose={onClose}
      wide
      closeDisabled={deletingHome || deletingApplianceId !== null}
    >
      <div className="detail-content">
        <div className="detail-toolbar">
          <div
            className={`detail-connection-status detail-connection-status--${
              live.error ? 'disconnected' : homeFreshness
            }`}
            role="status"
          >
            <span className="live-indicator__dot" aria-hidden="true" />
            <span>{connectionLabel(homeFreshness, Boolean(live.error))}</span>
          </div>
          <button
            className="button button--small detail-delete-home"
            type="button"
            onClick={requestDeleteHome}
            disabled={
              deletingHome ||
              deletingApplianceId !== null ||
              deleteTarget !== null
            }
          >
            <Trash2 aria-hidden="true" size={14} />
            {deletingHome ? 'Siliniyor…' : 'Evi Sil'}
          </button>
        </div>

        {deleteTarget && (
          <section
            className="delete-confirmation"
            role="group"
            aria-labelledby={deletePromptId}
          >
            <span className="delete-confirmation__icon" aria-hidden="true">
              <AlertTriangle size={19} />
            </span>
            <div className="delete-confirmation__copy">
              <strong id={deletePromptId}>
                {deleteTarget.kind === 'home'
                  ? `"${deleteTarget.name}" evini silmek istiyor musunuz?`
                  : `"${deleteTarget.name}" cihazını silmek istiyor musunuz?`}
              </strong>
              <p>
                {deleteTarget.kind === 'home'
                  ? 'Ev ve ilişkili verileri kalıcı olarak kaldırılacak.'
                  : 'Cihaz canlı izleme ve geçmiş görünümlerinden kaldırılacak.'}
              </p>
            </div>
            <div className="delete-confirmation__actions">
              <button
                className="button button--small button--ghost"
                type="button"
                ref={cancelDeleteRef}
                onClick={cancelDelete}
                disabled={deletingHome || deletingApplianceId !== null}
              >
                Vazgeç
              </button>
              <button
                className="button button--small delete-confirmation__confirm"
                type="button"
                onClick={confirmDelete}
                disabled={deletingHome || deletingApplianceId !== null}
              >
                <Trash2 aria-hidden="true" size={14} />
                {deletingHome || deletingApplianceId !== null
                  ? 'Siliniyor…'
                  : 'Kalıcı olarak sil'}
              </button>
            </div>
          </section>
        )}

        {mutationError && (
          <div className="mutation-feedback" role="alert">
            <AlertTriangle aria-hidden="true" size={17} />
            <span>{mutationError}</span>
          </div>
        )}

        {Boolean(live.error) && (
          <ErrorState
            message={getUserFacingError(live.error)}
            onRetry={retryLive}
            compact
          />
        )}

        <DetailTabs
          activeTab={activeTab}
          idPrefix={tabId}
          anomalyCount={home.anomalyCount}
          onChange={setActiveTab}
        />

        <section
          className="detail-tab-panel detail-tab-panel--overview"
          id={`${tabId}-panel-overview`}
          role="tabpanel"
          aria-labelledby={`${tabId}-tab-overview`}
          hidden={activeTab !== 'overview'}
          tabIndex={0}
        >
          <section
            className={`detail-hero${
              isQuotaCritical
                ? ' detail-hero--critical'
                : isQuotaWarning
                  ? ' detail-hero--warning'
                  : ''
            }`}
            aria-label="Canlı tüketim özeti"
          >
            <div className="detail-hero__primary">
              <div className="detail-live-label">
                <span className="live-indicator__dot" aria-hidden="true" />
                Şu anda kullanılıyor
              </div>
              <strong>{formatPower(home.currentPowerWatts)}</strong>
              <span>{activeApplianceCount} cihaz aktif</span>
            </div>

            <div className="detail-hero__metrics">
              <div>
                <span>
                  <Zap aria-hidden="true" size={15} /> Dönem enerjisi
                </span>
                <strong>{formatEnergy(home.accumulatedEnergyKwh)}</strong>
              </div>
              <div>
                <span>
                  <CircleDollarSign aria-hidden="true" size={15} /> Güncel
                  maliyet
                </span>
                <strong>{formatMoney(home.currentCost)}</strong>
              </div>
              <div>
                <span>
                  <ShieldCheck aria-hidden="true" size={15} /> Tarife
                </span>
                <strong
                  className={
                    home.tariffState === 'PENALTY' ? 'text-critical' : ''
                  }
                >
                  {tariffLabels[home.tariffState]}
                </strong>
              </div>
            </div>

            <div className="detail-budget">
              <div className="detail-budget__heading">
                <span>Aylık bütçe</span>
                <strong>{formatPercent(home.budgetUsagePercent)}</strong>
              </div>
              <div
                className="quota-progress quota-progress--large"
                role="progressbar"
                aria-label="Aylık bütçe kullanımı"
                aria-valuenow={Math.round(quotaProgress)}
                aria-valuemin={0}
                aria-valuemax={100}
                aria-valuetext={`${formatPercent(home.budgetUsagePercent)} bütçe kullanıldı`}
              >
                <span style={{ width: `${quotaProgress}%` }} />
                <i style={{ left: '80%' }} aria-hidden="true" />
              </div>
              <div className="detail-budget__amounts">
                <span>{formatMoney(home.currentCost)} kullanıldı</span>
                <span>{formatMoney(home.monthlyBudget)} bütçe</span>
              </div>
            </div>
          </section>

          <div className="home-overview-grid">
            <article className="home-overview-card">
              <span aria-hidden="true">
                <House size={18} />
              </span>
              <div>
                <p>Ev & konum</p>
                <strong>{home.homeName}</strong>
                <small>
                  <MapPin aria-hidden="true" size={12} />
                  {home.city || 'Konum bilgisi yok'}
                </small>
              </div>
            </article>
            <article className="home-overview-card">
              <span aria-hidden="true">
                <PlugZap size={18} />
              </span>
              <div>
                <p>Cihaz durumu</p>
                <strong>{home.appliances.length} kayıtlı cihaz</strong>
                <small>{activeApplianceCount} cihaz şu anda aktif</small>
              </div>
            </article>
            <article
              className={`home-overview-card${
                home.anomalyCount ? ' home-overview-card--alert' : ''
              }`}
            >
              <span aria-hidden="true">
                {home.anomalyCount ? (
                  <AlertTriangle size={18} />
                ) : (
                  <CheckCircle2 size={18} />
                )}
              </span>
              <div>
                <p>Sağlık özeti</p>
                <strong>
                  {home.anomalyCount
                    ? `${home.anomalyCount} aktif anomali`
                    : 'Tüm cihazlar normal'}
                </strong>
                <button type="button" onClick={() => activateTab('appliances')}>
                  Cihazları incele
                </button>
              </div>
            </article>
            <article
              className={`home-overview-card${
                homeFreshness !== 'live' || live.error
                  ? ' home-overview-card--alert'
                  : ''
              }`}
            >
              <span aria-hidden="true">
                <Clock3 size={18} />
              </span>
              <div>
                <p>Son telemetri</p>
                <strong>{formatDateTime(home.lastUpdatedAt)}</strong>
                <small>{connectionLabel(homeFreshness, Boolean(live.error))}</small>
              </div>
            </article>
          </div>

          <div className="detail-progressive-actions">
            <button
              className="button button--secondary"
              type="button"
              onClick={() => activateTab('appliances')}
            >
              <PlugZap aria-hidden="true" size={16} /> Cihaz telemetrisini aç
            </button>
            <button
              className="button button--secondary"
              type="button"
              onClick={() => activateTab('analytics')}
            >
              <Activity aria-hidden="true" size={16} /> Geçmiş analitiği aç
            </button>
          </div>
        </section>

        <section
          className="detail-tab-panel detail-tab-panel--appliances"
          id={`${tabId}-panel-appliances`}
          role="tabpanel"
          aria-labelledby={`${tabId}-tab-appliances`}
          hidden={activeTab !== 'appliances'}
          tabIndex={0}
        >
          <div className="section-heading section-heading--compact">
            <div>
              <p className="eyebrow">Cihazlar</p>
              <h3>Canlı cihaz karakterleri</h3>
            </div>
            <span
              className={`health-summary${
                home.anomalyCount ? ' health-summary--alert' : ''
              }`}
            >
              {home.anomalyCount ? (
                <AlertTriangle aria-hidden="true" size={15} />
              ) : (
                <CheckCircle2 aria-hidden="true" size={15} />
              )}
              {home.anomalyCount
                ? `${home.anomalyCount} cihaz incelenmeli`
                : 'Tüm cihazlar normal'}
            </span>
          </div>

          <div className="appliance-detail-layout">
            <ApplianceCharacterGrid
              items={appliancePresentations}
              selectedApplianceId={selectedApplianceId}
              isRefreshing={live.isRefreshing}
              onSelect={selectAppliance}
            />
            <ApplianceTelemetryPanel
              item={selectedAppliance}
              isDeleting={
                deleteTarget !== null ||
                deletingHome ||
                deletingApplianceId !== null
              }
              onDelete={requestDeleteAppliance}
            />
          </div>
        </section>

        <section
          className="detail-tab-panel detail-tab-panel--analytics"
          id={`${tabId}-panel-analytics`}
          role="tabpanel"
          aria-labelledby={`${tabId}-tab-analytics`}
          hidden={activeTab !== 'analytics'}
          tabIndex={0}
        >
          {analytics.isLoading ? (
            <div className="analytics-loading" role="status">
              <InlineSpinner label="Analitik veriler hazırlanıyor" />
            </div>
          ) : (
            <>
              {analytics.failedSources.includes('history') && analytics.error && (
                <div className="analytics-notice" role="status">
                  <AlertTriangle aria-hidden="true" size={16} />
                  <span>Geçmiş tüketim verileri alınamadı. {analytics.error}</span>
                  <button type="button" onClick={retryAnalytics}>
                    <RefreshCw aria-hidden="true" size={14} /> Tekrar dene
                  </button>
                </div>
              )}
              {activeTab === 'analytics' && (
                <EnergyCharts
                  history={analytics.history}
                  appliances={home.appliances}
                />
              )}
            </>
          )}
        </section>

        <section
          className="detail-tab-panel detail-tab-panel--insights"
          id={`${tabId}-panel-insights`}
          role="tabpanel"
          aria-labelledby={`${tabId}-tab-insights`}
          hidden={activeTab !== 'insights'}
          tabIndex={0}
        >
          {(analytics.failedSources.includes('events') ||
            analytics.failedSources.includes('recommendations')) && (
            <div className="analytics-notice" role="status">
              <AlertTriangle aria-hidden="true" size={16} />
              <span>Bazı öneri veya olay kayıtları alınamadı.</span>
              <button type="button" onClick={retryAnalytics}>
                <RefreshCw aria-hidden="true" size={14} /> Tekrar dene
              </button>
            </div>
          )}

          <section className="insights-grid" aria-label="Öneriler ve olaylar">
            <article className="recommendations-panel">
              <header>
                <span aria-hidden="true">
                  <Sparkles size={18} />
                </span>
                <div>
                  <p className="eyebrow">VoltFlow önerisi</p>
                  <h3>Akıllı tasarruf notları</h3>
                </div>
              </header>
              {analytics.isLoading ? (
                <InlineSpinner label="Öneriler yükleniyor" />
              ) : analytics.failedSources.includes('recommendations') ? (
                <div className="panel-unavailable" role="status">
                  <AlertTriangle aria-hidden="true" size={16} />
                  <p>Enerji önerileri şu anda hazırlanamadı.</p>
                </div>
              ) : analytics.recommendations.length ? (
                <div className="recommendation-list">
                  {analytics.recommendations.slice(0, 3).map((recommendation) => (
                    <div className="recommendation" key={recommendation.id}>
                      <Lightbulb aria-hidden="true" size={17} />
                      <div>
                        <p>{recommendation.text}</p>
                        <span>
                          {formatDateTime(recommendation.createdAt)}
                          {recommendation.fallbackUsed
                            ? ' · Güvenli öneri'
                            : ' · AI önerisi'}
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="panel-empty-copy">
                  Yeni bir eşik veya anomali oluştuğunda kişisel öneriler burada
                  görünecek.
                </p>
              )}
            </article>

            <article className="events-panel">
              <header>
                <span aria-hidden="true">
                  <Activity size={18} />
                </span>
                <div>
                  <p className="eyebrow">Denetim kaydı</p>
                  <h3>Son olaylar</h3>
                </div>
              </header>
              {analytics.isLoading ? (
                <InlineSpinner label="Olaylar yükleniyor" />
              ) : analytics.failedSources.includes('events') ? (
                <div className="panel-unavailable" role="status">
                  <AlertTriangle aria-hidden="true" size={16} />
                  <p>Olay geçmişi şu anda alınamadı.</p>
                </div>
              ) : analytics.events.length ? (
                <ol className="event-list">
                  {analytics.events.slice(0, 6).map((event) => {
                    const Icon = eventIcons[event.type];
                    return (
                      <li key={`${event.type}-${event.id}`}>
                        <span
                          className={`event-list__icon event-list__icon--${event.type.toLowerCase()}`}
                          aria-hidden="true"
                        >
                          <Icon size={15} />
                        </span>
                        <div>
                          <div>
                            <strong>{event.title}</strong>
                            <time dateTime={event.occurredAt}>
                              <Clock3 aria-hidden="true" size={12} />{' '}
                              {formatDateTime(event.occurredAt)}
                            </time>
                          </div>
                          <p>{event.description}</p>
                          {event.resolvedAt && (
                            <span className="resolved-label">
                              Çözüldü · {formatDateTime(event.resolvedAt)}
                            </span>
                          )}
                        </div>
                      </li>
                    );
                  })}
                </ol>
              ) : (
                <p className="panel-empty-copy">
                  Bu ev için henüz kota, tarife veya anomali olayı kaydedilmedi.
                </p>
              )}
            </article>
          </section>
        </section>
      </div>
    </Dialog>
  );
}
