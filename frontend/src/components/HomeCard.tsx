import { AlertTriangle, ArrowUpRight, Clock3, Gauge, House, Zap } from 'lucide-react';
import type { HomeStatus } from '../types';
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

function getQuotaTone(home: HomeStatus): 'normal' | 'warning' | 'critical' {
  if (home.budgetUsagePercent >= 100 || home.tariffState === 'PENALTY') return 'critical';
  if (home.budgetUsagePercent >= 80) return 'warning';
  return 'normal';
}

export function HomeCard({ home, onSelect }: HomeCardProps) {
  const quotaTone = getQuotaTone(home);
  const cardClasses = [
    'home-card',
    `home-card--${quotaTone}`,
    home.tariffState === 'PENALTY' ? 'home-card--penalty' : '',
    home.anomalyCount > 0 ? 'home-card--anomaly' : '',
  ]
    .filter(Boolean)
    .join(' ');
  const progress = Math.min(Math.max(home.budgetUsagePercent, 0), 100);

  return (
    <article className={cardClasses} data-quota-state={quotaTone} data-anomaly={home.anomalyCount > 0}>
      <div className="home-card__accent" aria-hidden="true" />
      <div className="home-card__header">
        <div className="home-card__identity">
          <span className="home-card__home-icon" aria-hidden="true">
            <House size={20} />
          </span>
          <div>
            <h3>{home.homeName}</h3>
            <p title={formatDateTime(home.lastUpdatedAt)}>
              <Clock3 aria-hidden="true" size={13} /> {formatRelativeTime(home.lastUpdatedAt)}
            </p>
          </div>
        </div>
        <span className={`tariff-pill tariff-pill--${home.tariffState.toLowerCase()}`}>
          {tariffLabels[home.tariffState]}
        </span>
      </div>

      <div className="home-card__power">
        <span>
          <Zap aria-hidden="true" size={16} /> Anlık güç
        </span>
        <strong>{formatPower(home.currentPowerWatts)}</strong>
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
}
