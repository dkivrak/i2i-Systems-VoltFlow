import {
  Activity,
  AlertTriangle,
  BellRing,
  CheckCircle2,
  CircleDollarSign,
  Clock3,
  Gauge,
  Lightbulb,
  PlugZap,
  RefreshCw,
  ShieldCheck,
  Sparkles,
  Trash2,
  Zap,
} from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { api, getUserFacingError } from '../api/client';
import { getPollingInterval, usePollingResource } from '../hooks/usePollingResource';
import type { HistoryPoint, HomeEvent, HomeStatus, Recommendation } from '../types';
import {
  applianceTypeLabels,
  formatDateTime,
  formatEnergy,
  formatMoney,
  formatPercent,
  formatPower,
  operatingStateLabels,
  tariffLabels,
} from '../utils/format';
import { Dialog } from './Dialog';
import { EnergyCharts } from './EnergyCharts';
import { ErrorState, InlineSpinner } from './PageStates';

interface HomeDetailModalProps {
  summary: HomeStatus;
  onClose: () => void;
  onDeleted?: () => void;
}

interface AnalyticsState {
  history: HistoryPoint[];
  events: HomeEvent[];
  recommendations: Recommendation[];
  isLoading: boolean;
  error?: string;
}

const eventIcons = {
  QUOTA: Gauge,
  ANOMALY: AlertTriangle,
  TARIFF: CircleDollarSign,
  UNKNOWN: BellRing,
} as const;

export function HomeDetailModal({ summary, onClose, onDeleted }: HomeDetailModalProps) {
  const homeId = summary.homeId;
  const detailRequest = useCallback((signal: AbortSignal) => api.getHomeStatus(homeId, signal), [homeId]);
  const live = usePollingResource(detailRequest, getPollingInterval(), true, summary);
  const home = live.data ?? summary;
  const [analyticsVersion, setAnalyticsVersion] = useState(0);
  const [analytics, setAnalytics] = useState<AnalyticsState>({
    history: [],
    events: [],
    recommendations: [],
    isLoading: true,
  });
  const analyticsController = useRef<AbortController | null>(null);

  useEffect(() => {
    analyticsController.current?.abort();
    const controller = new AbortController();
    analyticsController.current = controller;
    setAnalytics((current) => ({ ...current, isLoading: true, error: undefined }));

    void Promise.allSettled([
      api.getHistory(homeId, controller.signal),
      api.getEvents(homeId, controller.signal),
      api.getRecommendations(homeId, controller.signal),
    ]).then((results) => {
      if (controller.signal.aborted) return;
      const [historyResult, eventsResult, recommendationsResult] = results;
      const rejected = results.find((result) => result.status === 'rejected');
      setAnalytics({
        history: historyResult.status === 'fulfilled' ? historyResult.value : [],
        events: eventsResult.status === 'fulfilled' ? eventsResult.value : [],
        recommendations: recommendationsResult.status === 'fulfilled' ? recommendationsResult.value : [],
        isLoading: false,
        error: rejected?.status === 'rejected' ? getUserFacingError(rejected.reason) : undefined,
      });
    });

    return () => controller.abort();
  }, [analyticsVersion, homeId]);

  const [deletingHome, setDeletingHome] = useState(false);
  const [deletingApplianceId, setDeletingApplianceId] = useState<number | null>(null);

  const handleDeleteHome = async () => {
    if (!window.confirm(`"${home.homeName}" evini ve tüm verilerini silmek istediğinize emin misiniz?`)) return;
    try {
      setDeletingHome(true);
      await api.deleteHome(homeId);
      onDeleted?.();
      onClose();
    } catch (err) {
      alert(getUserFacingError(err));
    } finally {
      setDeletingHome(false);
    }
  };

  const handleDeleteAppliance = async (applianceId: number, applianceName: string) => {
    if (!window.confirm(`"${applianceName}" cihazını silmek istediğinize emin misiniz?`)) return;
    try {
      setDeletingApplianceId(applianceId);
      await api.deleteAppliance(homeId, applianceId);
      live.retry();
    } catch (err) {
      alert(getUserFacingError(err));
    } finally {
      setDeletingApplianceId(null);
    }
  };

  const quotaProgress = Math.min(Math.max(home.budgetUsagePercent, 0), 100);
  const isQuotaWarning = home.budgetUsagePercent >= 80;
  const isQuotaCritical = home.budgetUsagePercent >= 100 || home.tariffState === 'PENALTY';

  return (
    <Dialog
      title={home.homeName}
      eyebrow="Canlı ev görünümü"
      description={`Son veri: ${formatDateTime(home.lastUpdatedAt)}`}
      onClose={onClose}
      wide
    >
      <div className="detail-content">
        <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: '10px' }}>
          <button
            type="button"
            onClick={handleDeleteHome}
            disabled={deletingHome}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              padding: '6px 12px',
              backgroundColor: 'rgba(239, 68, 68, 0.15)',
              border: '1px solid rgba(239, 68, 68, 0.4)',
              borderRadius: '8px',
              color: '#fca5a5',
              fontSize: '0.78rem',
              fontWeight: 600,
              cursor: 'pointer',
            }}
          >
            <Trash2 size={14} />
            <span>{deletingHome ? 'Siliniyor...' : 'Evi Sil'}</span>
          </button>
        </div>

        {Boolean(live.error) && (
          <ErrorState message={getUserFacingError(live.error)} onRetry={live.retry} compact />
        )}

        <section
          className={`detail-hero${isQuotaCritical ? ' detail-hero--critical' : isQuotaWarning ? ' detail-hero--warning' : ''}`}
          aria-label="Canlı tüketim özeti"
        >
          <div className="detail-hero__primary">
            <div className="detail-live-label">
              <span className="live-indicator__dot" aria-hidden="true" />
              Şu anda kullanılıyor
              {live.isRefreshing && <span className="sr-only">, güncelleniyor</span>}
            </div>
            <strong>{formatPower(home.currentPowerWatts)}</strong>
            <span>{home.appliances.filter((appliance) => appliance.operatingState !== 'OFF').length} cihaz aktif</span>
          </div>

          <div className="detail-hero__metrics">
            <div>
              <span><Zap aria-hidden="true" size={15} /> Dönem enerjisi</span>
              <strong>{formatEnergy(home.accumulatedEnergyKwh)}</strong>
            </div>
            <div>
              <span><CircleDollarSign aria-hidden="true" size={15} /> Güncel maliyet</span>
              <strong>{formatMoney(home.currentCost)}</strong>
            </div>
            <div>
              <span><ShieldCheck aria-hidden="true" size={15} /> Tarife</span>
              <strong className={home.tariffState === 'PENALTY' ? 'text-critical' : ''}>
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

        <section className="appliances-section" aria-labelledby="appliances-title">
          <div className="section-heading section-heading--compact">
            <div>
              <p className="eyebrow">Cihazlar</p>
              <h3 id="appliances-title">Canlı cihaz durumu</h3>
            </div>
            <span className={`health-summary${home.anomalyCount ? ' health-summary--alert' : ''}`}>
              {home.anomalyCount ? <AlertTriangle aria-hidden="true" size={15} /> : <CheckCircle2 aria-hidden="true" size={15} />}
              {home.anomalyCount ? `${home.anomalyCount} cihaz incelenmeli` : 'Tüm cihazlar normal'}
            </span>
          </div>

          {home.appliances.length ? (
            <div className="appliance-table-wrap">
              <table className="appliance-table">
                <thead>
                  <tr>
                    <th scope="col">Cihaz</th>
                    <th scope="col">Durum</th>
                    <th scope="col">Anlık güç</th>
                    <th scope="col">Güvenli sınır</th>
                    <th scope="col">İhlal döngüsü</th>
                    <th scope="col">Enerji</th>
                    <th scope="col">Maliyet</th>
                    <th scope="col">Sağlık</th>
                    <th scope="col" style={{ textAlign: 'right' }}>İşlem</th>
                  </tr>
                </thead>
                <tbody>
                  {home.appliances.map((appliance) => {
                    const anomalous = appliance.healthStatus === 'ANOMALOUS';
                    const isDeleting = deletingApplianceId === appliance.applianceId;
                    return (
                      <tr className={anomalous ? 'appliance-row--anomalous' : ''} key={appliance.applianceId}>
                        <td data-label="Cihaz">
                          <span className="appliance-identity">
                            <span className="appliance-identity__icon" aria-hidden="true"><PlugZap size={17} /></span>
                            <span>
                              <strong>{appliance.name}</strong>
                              <small>{applianceTypeLabels[appliance.type]}</small>
                            </span>
                          </span>
                        </td>
                        <td data-label="Durum">
                          <span className={`state-chip state-chip--${appliance.operatingState.toLowerCase()}`}>
                            {operatingStateLabels[appliance.operatingState]}
                          </span>
                        </td>
                        <td data-label="Anlık güç"><strong>{formatPower(appliance.currentPowerWatts)}</strong></td>
                        <td data-label="Güvenli sınır">{formatPower(appliance.safePowerLimitWatts)}</td>
                        <td data-label="İhlal döngüsü">
                          <span className="breach-cycles" aria-label={`${appliance.consecutiveBreachCount} / 3 ihlal döngüsü`}>
                            {[1, 2, 3].map((cycle) => (
                              <i className={cycle <= appliance.consecutiveBreachCount ? 'is-filled' : ''} key={cycle} />
                            ))}
                            <small>{Math.min(appliance.consecutiveBreachCount, 3)}/3</small>
                          </span>
                        </td>
                        <td data-label="Enerji">{formatEnergy(appliance.accumulatedEnergyKwh)}</td>
                        <td data-label="Maliyet">{formatMoney(appliance.accumulatedCost)}</td>
                        <td data-label="Sağlık">
                          <span className={`health-chip health-chip--${anomalous ? 'anomalous' : 'normal'}`}>
                            {anomalous ? <AlertTriangle aria-hidden="true" size={13} /> : <CheckCircle2 aria-hidden="true" size={13} />}
                            {anomalous ? 'Anomali' : 'Normal'}
                          </span>
                        </td>
                        <td data-label="İşlem" style={{ textAlign: 'right' }}>
                          <button
                            type="button"
                            onClick={() => handleDeleteAppliance(appliance.applianceId, appliance.name)}
                            disabled={isDeleting}
                            style={{
                              background: 'none',
                              border: 0,
                              color: '#ef4444',
                              cursor: 'pointer',
                              padding: '4px 6px',
                              borderRadius: '6px',
                              opacity: isDeleting ? 0.5 : 1,
                            }}
                            title="Cihazı sil"
                          >
                            <Trash2 size={15} />
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          ) : live.isRefreshing ? (
            <div className="section-loading"><InlineSpinner label="Cihazlar yükleniyor" /></div>
          ) : (
            <div className="inline-empty"><PlugZap aria-hidden="true" size={22} /> Henüz canlı cihaz verisi bulunmuyor.</div>
          )}
        </section>

        {analytics.isLoading ? (
          <div className="analytics-loading" role="status">
            <InlineSpinner label="Analitik veriler hazırlanıyor" />
          </div>
        ) : (
          <>
            {analytics.error && (
              <div className="analytics-notice" role="status">
                <AlertTriangle aria-hidden="true" size={16} />
                <span>Bazı geçmiş veriler alınamadı.</span>
                <button type="button" onClick={() => setAnalyticsVersion((version) => version + 1)}>
                  <RefreshCw aria-hidden="true" size={14} /> Tekrar dene
                </button>
              </div>
            )}
            <EnergyCharts history={analytics.history} appliances={home.appliances} />
          </>
        )}

        <section className="insights-grid" aria-label="Öneriler ve olaylar">
          <article className="recommendations-panel">
            <header>
              <span aria-hidden="true"><Sparkles size={18} /></span>
              <div>
                <p className="eyebrow">VoltWise önerisi</p>
                <h3>Akıllı tasarruf notları</h3>
              </div>
            </header>
            {analytics.isLoading ? (
              <InlineSpinner label="Öneriler yükleniyor" />
            ) : analytics.recommendations.length ? (
              <div className="recommendation-list">
                {analytics.recommendations.slice(0, 3).map((recommendation) => (
                  <div className="recommendation" key={recommendation.id}>
                    <Lightbulb aria-hidden="true" size={17} />
                    <div>
                      <p>{recommendation.text}</p>
                      <span>
                        {formatDateTime(recommendation.createdAt)}
                        {recommendation.fallbackUsed ? ' · Güvenli öneri' : ' · AI önerisi'}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <p className="panel-empty-copy">Yeni bir eşik veya anomali oluştuğunda kişisel öneriler burada görünecek.</p>
            )}
          </article>

          <article className="events-panel">
            <header>
              <span aria-hidden="true"><Activity size={18} /></span>
              <div>
                <p className="eyebrow">Denetim kaydı</p>
                <h3>Son olaylar</h3>
              </div>
            </header>
            {analytics.isLoading ? (
              <InlineSpinner label="Olaylar yükleniyor" />
            ) : analytics.events.length ? (
              <ol className="event-list">
                {analytics.events.slice(0, 6).map((event) => {
                  const Icon = eventIcons[event.type];
                  return (
                    <li key={`${event.type}-${event.id}`}>
                      <span className={`event-list__icon event-list__icon--${event.type.toLowerCase()}`} aria-hidden="true">
                        <Icon size={15} />
                      </span>
                      <div>
                        <div><strong>{event.title}</strong><time dateTime={event.occurredAt}><Clock3 aria-hidden="true" size={12} /> {formatDateTime(event.occurredAt)}</time></div>
                        <p>{event.description}</p>
                        {event.resolvedAt && <span className="resolved-label">Çözüldü · {formatDateTime(event.resolvedAt)}</span>}
                      </div>
                    </li>
                  );
                })}
              </ol>
            ) : (
              <p className="panel-empty-copy">Bu ev için henüz kota, tarife veya anomali olayı kaydedilmedi.</p>
            )}
          </article>
        </section>
      </div>
    </Dialog>
  );
}
