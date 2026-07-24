import { memo, useEffect, useState } from 'react';
import { AlertTriangle, ArrowUpRight, Clock3, Gauge, House, Radio, Zap } from 'lucide-react';
import { ApplianceCharacter, type CharacterState } from '../characters';
import type { ApplianceStatus, HomeStatus } from '../types';
import {
  formatDateTime,
  formatEnergy,
  formatMoney,
  formatPercent,
  formatPower,
  formatRelativeTime,
  tariffLabels,
} from '../utils/format';

interface HomeCardProps {
  home: HomeStatus;
  onSelect: (home: HomeStatus) => void;
}

type TelemetryFreshness = 'live' | 'stale' | 'missing';

const STALE_AFTER_MS = 15_000;

function getQuotaTone(home: HomeStatus): 'normal' | 'warning' | 'critical' {
  if (home.budgetUsagePercent >= 100 || home.tariffState === 'PENALTY') return 'critical';
  if (home.budgetUsagePercent >= 80) return 'warning';
  return 'normal';
}

function getTelemetryFreshness(value?: string): TelemetryFreshness {
  if (!value) return 'missing';
  const timestamp = Date.parse(value);
  if (!Number.isFinite(timestamp)) return 'missing';
  return Date.now() - timestamp > STALE_AFTER_MS ? 'stale' : 'live';
}

function useTelemetryFreshness(value?: string): TelemetryFreshness {
  const [freshness, setFreshness] = useState<TelemetryFreshness>(() => getTelemetryFreshness(value));

  useEffect(() => {
    const next = getTelemetryFreshness(value);
    setFreshness(next);
    if (next !== 'live' || !value) return undefined;

    const staleAt = Date.parse(value) + STALE_AFTER_MS;
    const timer = window.setTimeout(() => setFreshness('stale'), Math.max(staleAt - Date.now(), 0));
    return () => window.clearTimeout(timer);
  }, [value]);

  return freshness;
}

function getCharacterState(appliance: ApplianceStatus): CharacterState {
  if (appliance.healthStatus === 'ANOMALOUS') return 'anomalous';
  if (
    appliance.operatingState === 'HIGH_LOAD' ||
    (appliance.safePowerLimitWatts > 0 &&
      appliance.currentPowerWatts > appliance.safePowerLimitWatts)
  ) {
    return 'warning';
  }
  if (appliance.operatingState === 'OFF') return 'sleeping';
  if (appliance.operatingState === 'ON') return 'active';
  return 'idle';
}

const freshnessLabels: Record<TelemetryFreshness, string> = {
  live: 'Canlı bağlantı',
  stale: 'Veri gecikmiş',
  missing: 'Telemetri bekleniyor',
};

export const HomeCard = memo(function HomeCard({ home, onSelect }: HomeCardProps) {
  const quotaTone = getQuotaTone(home);
  const freshness = useTelemetryFreshness(home.lastUpdatedAt);
  const appliancePreview = home.appliances.slice(0, 4);
  const hiddenApplianceCount = Math.max(home.appliances.length - appliancePreview.length, 0);
  const overLimitCount = home.appliances.filter(
    (appliance) =>
      appliance.safePowerLimitWatts > 0 &&
      appliance.currentPowerWatts > appliance.safePowerLimitWatts,
  ).length;
  const cardClasses = [
    'home-card',
    `home-card--${quotaTone}`,
    home.tariffState === 'PENALTY' ? 'home-card--penalty' : '',
    home.anomalyCount > 0 ? 'home-card--anomaly' : '',
    freshness !== 'live' ? `home-card--${freshness}` : '',
  ]
    .filter(Boolean)
    .join(' ');
  const progress = Math.min(Math.max(home.budgetUsagePercent, 0), 100);
  const [, setRelativeTimeTick] = useState(0);

  useEffect(() => {
    if (freshness !== 'stale') return undefined;
    const interval = window.setInterval(
      () => setRelativeTimeTick((tick) => tick + 1),
      30_000,
    );
    return () => window.clearInterval(interval);
  }, [freshness]);

  const handleKeyDown = (event: React.KeyboardEvent) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      onSelect(home);
    }
  };

  return (
    <article
      className={cardClasses}
      data-quota-state={quotaTone}
      data-anomaly={home.anomalyCount > 0}
      onClick={() => onSelect(home)}
      onKeyDown={handleKeyDown}
      tabIndex={0}
      role="button"
      aria-label={`${home.homeName} detaylarını göster`}
      style={{ cursor: 'pointer' }}
    >
      <div className="home-card__accent" aria-hidden="true" />
      <div className="home-card__header">
        <div className="home-card__identity">
          <span className="home-card__home-icon" aria-hidden="true">
            <House size={20} />
          </span>
          <div>
            <h3>{home.homeName}</h3>
            <p>
              <Clock3 aria-hidden="true" size={13} /> {formatRelativeTime(home.lastUpdatedAt)}
            </p>
          </div>
        </div>
        <div className="home-card__status-stack">
          <span className={`telemetry-pill telemetry-pill--${freshness}`} title={formatDateTime(home.lastUpdatedAt)}>
            <Radio aria-hidden="true" size={12} />
            {freshnessLabels[freshness]}
            <span className="sr-only">
              . Son telemetri: {formatDateTime(home.lastUpdatedAt)}
            </span>
          </span>
          <span className={`tariff-pill tariff-pill--${home.tariffState.toLowerCase()}`}>
            {tariffLabels[home.tariffState]}
          </span>
        </div>
      </div>

      <div className="home-card__power">
        <span>
          <Zap aria-hidden="true" size={16} /> Anlık güç
        </span>
        <strong>{formatPower(home.currentPowerWatts)}</strong>
      </div>

      <div
        className="home-card__characters"
        role="group"
        aria-label={`${home.homeName} cihaz önizlemesi`}
      >
        {appliancePreview.length ? (
          <ul>
            {appliancePreview.map((appliance) => (
              <li
                className={`home-card__character${appliance.healthStatus === 'ANOMALOUS' ? ' is-anomalous' : ''}`}
                key={appliance.applianceId}
                title={`${appliance.name}: ${formatPower(appliance.currentPowerWatts)}`}
              >
                <span aria-hidden="true">
                  <ApplianceCharacter
                    type={appliance.type}
                    state={getCharacterState(appliance)}
                    size="sm"
                  />
                </span>
                <span className="sr-only">
                  {appliance.name}, {formatPower(appliance.currentPowerWatts)}
                  {appliance.healthStatus === 'ANOMALOUS' ? ', anomali algılandı' : ''}
                </span>
                {appliance.healthStatus === 'ANOMALOUS' && (
                  <span className="home-card__character-alert" aria-hidden="true">!</span>
                )}
              </li>
            ))}
            {hiddenApplianceCount > 0 && (
              <li className="home-card__character-more" aria-label={`${hiddenApplianceCount} cihaz daha`}>
                +{hiddenApplianceCount}
              </li>
            )}
          </ul>
        ) : (
          <span className="home-card__no-devices">
            <Gauge aria-hidden="true" size={15} /> Cihaz telemetrisi bekleniyor
          </span>
        )}
      </div>

      <dl className="home-card__metrics">
        <div>
          <dt>Toplam enerji</dt>
          <dd>{formatEnergy(home.accumulatedEnergyKwh)}</dd>
        </div>
        <div>
          <dt>Güncel maliyet</dt>
          <dd>{formatMoney(home.currentCost)}</dd>
        </div>
      </dl>

      <div className="quota-block">
        <div className="quota-block__labels">
          <span>Aylık bütçe</span>
          <strong>{formatPercent(home.budgetUsagePercent)}</strong>
        </div>
        <div
          className="quota-progress"
          role="progressbar"
          aria-label={`${home.homeName} bütçe kullanımı`}
          aria-valuenow={Math.round(progress)}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuetext={`${formatPercent(home.budgetUsagePercent)} kullanıldı`}
        >
          <span style={{ width: `${progress}%` }} />
        </div>
        <div className="quota-block__amounts">
          <span>{formatMoney(home.currentCost)}</span>
          <span>{formatMoney(home.monthlyBudget)}</span>
        </div>
      </div>

      <div className="home-card__footer">
        {home.anomalyCount > 0 ? (
          <span className="anomaly-badge">
            <AlertTriangle aria-hidden="true" size={15} />
            {home.anomalyCount} aktif anomali
          </span>
        ) : overLimitCount > 0 ? (
          <span className="warning-badge">
            <AlertTriangle aria-hidden="true" size={15} />
            {overLimitCount} cihaz sınır üzerinde
          </span>
        ) : quotaTone !== 'normal' ? (
          <span className="warning-badge">
            <AlertTriangle aria-hidden="true" size={15} />
            {quotaTone === 'critical' ? 'Bütçe sınırı aşıldı' : 'Bütçe eşiğine yaklaşıldı'}
          </span>
        ) : (
          <span className="healthy-badge">
            <Gauge aria-hidden="true" size={15} /> Cihazlar normal
          </span>
        )}
        <button
          className="home-card__details"
          type="button"
          aria-label={`${home.homeName} detaylarını aç`}
          onClick={() => onSelect(home)}
        >
          Detaylar <ArrowUpRight aria-hidden="true" size={16} />
        </button>
      </div>
    </article>
  );
});
