import { memo } from 'react';
import {
  AlertTriangle,
  CheckCircle2,
  CircleDollarSign,
  Clock3,
  Gauge,
  RadioTower,
  ShieldCheck,
  Trash2,
  Zap,
} from 'lucide-react';
import type { AppliancePresentation } from '../../presentation/appliancePresentation';
import {
  applianceTypeLabels,
  formatDateTime,
  formatEnergy,
  formatMoney,
  formatPower,
  operatingStateLabels,
} from '../../utils/format';

interface ApplianceTelemetryPanelProps {
  item: AppliancePresentation | undefined;
  isDeleting: boolean;
  onDelete: (applianceId: number, applianceName: string) => void;
}

export const ApplianceTelemetryPanel = memo(function ApplianceTelemetryPanel({
  item,
  isDeleting,
  onDelete,
}: ApplianceTelemetryPanelProps) {
  if (!item) {
    return (
      <div className="appliance-telemetry-panel appliance-telemetry-panel--empty">
        <RadioTower aria-hidden="true" size={24} />
        <div>
          <h4>Bir cihaz seçin</h4>
          <p>Canlı telemetri ve güvenli çalışma ayrıntıları burada gösterilecek.</p>
        </div>
      </div>
    );
  }

  const { appliance } = item;

  return (
    <article
      className={`appliance-telemetry-panel appliance-telemetry-panel--${item.tone}`}
      aria-labelledby={`appliance-telemetry-${appliance.applianceId}`}
      id={`appliance-telemetry-panel-${appliance.applianceId}`}
    >
      <header className="appliance-telemetry-panel__header">
        <div>
          <p className="eyebrow">Seçili cihaz</p>
          <h4 id={`appliance-telemetry-${appliance.applianceId}`}>
            {appliance.name} telemetrisi
          </h4>
          <p>{applianceTypeLabels[appliance.type]}</p>
        </div>
        <div className="appliance-telemetry-panel__actions">
          <span className={`health-chip health-chip--${item.anomalous ? 'anomalous' : 'normal'}`}>
            {item.anomalous ? (
              <AlertTriangle aria-hidden="true" size={13} />
            ) : (
              <CheckCircle2 aria-hidden="true" size={13} />
            )}
            {item.anomalous ? 'Anomali' : 'Normal'}
          </span>
          <button
            className="button button--small appliance-delete-button"
            type="button"
            onClick={() => onDelete(appliance.applianceId, appliance.name)}
            disabled={isDeleting}
            aria-label={`${appliance.name} cihazını sil`}
          >
            <Trash2 aria-hidden="true" size={14} />
            {isDeleting ? 'Siliniyor…' : 'Cihazı sil'}
          </button>
        </div>
      </header>

      <dl className="appliance-telemetry-grid">
        <div className="telemetry-metric telemetry-metric--primary">
          <dt>
            <Zap aria-hidden="true" size={14} /> Anlık güç
          </dt>
          <dd>{formatPower(appliance.currentPowerWatts)}</dd>
        </div>
        <div className="telemetry-metric">
          <dt>
            <ShieldCheck aria-hidden="true" size={14} /> Güvenli sınır
          </dt>
          <dd>{formatPower(appliance.safePowerLimitWatts)}</dd>
        </div>
        <div className="telemetry-metric">
          <dt>
            <Gauge aria-hidden="true" size={14} /> Çalışma durumu
          </dt>
          <dd>{operatingStateLabels[appliance.operatingState]}</dd>
        </div>
        <div className="telemetry-metric">
          <dt>
            <Zap aria-hidden="true" size={14} /> Biriken enerji
          </dt>
          <dd>{formatEnergy(appliance.accumulatedEnergyKwh)}</dd>
        </div>
        <div className="telemetry-metric">
          <dt>
            <CircleDollarSign aria-hidden="true" size={14} /> Tahmini maliyet
          </dt>
          <dd>{formatMoney(appliance.accumulatedCost)}</dd>
        </div>
        <div className="telemetry-metric">
          <dt>
            <AlertTriangle aria-hidden="true" size={14} /> İhlal döngüsü
          </dt>
          <dd>
            <span
              className="breach-cycles"
              aria-label={`${appliance.consecutiveBreachCount} / 3 ihlal döngüsü`}
            >
              {[1, 2, 3].map((cycle) => (
                <i
                  className={cycle <= appliance.consecutiveBreachCount ? 'is-filled' : ''}
                  key={cycle}
                />
              ))}
              <small>{Math.min(appliance.consecutiveBreachCount, 3)}/3</small>
            </span>
          </dd>
        </div>
        <div className="telemetry-metric telemetry-metric--wide">
          <dt>
            <Clock3 aria-hidden="true" size={14} /> Son telemetri
          </dt>
          <dd>
            {formatDateTime(appliance.lastUpdatedAt)}
            <span className={`freshness-label freshness-label--${item.freshness}`}>
              {item.freshnessLabel}
            </span>
          </dd>
        </div>
      </dl>

      {item.warningTitle ? (
        <section
          className={`anomaly-explainer anomaly-explainer--${item.tone}`}
          aria-labelledby={`appliance-warning-${appliance.applianceId}`}
        >
          <span className="anomaly-explainer__icon" aria-hidden="true">
            <AlertTriangle size={19} />
          </span>
          <div className="anomaly-explainer__copy">
            <h5 id={`appliance-warning-${appliance.applianceId}`}>
              {item.warningTitle}
            </h5>
            {item.warningDescription && <p>{item.warningDescription}</p>}
            {(item.thresholdExceeded || item.consecutiveViolation) && (
              <dl className="anomaly-explainer__values">
                <div>
                  <dt>Ölçülen</dt>
                  <dd>{formatPower(appliance.currentPowerWatts)}</dd>
                </div>
                <div>
                  <dt>Güvenli sınır</dt>
                  <dd>{formatPower(appliance.safePowerLimitWatts)}</dd>
                </div>
                <div>
                  <dt>Ardışık ihlal</dt>
                  <dd>{appliance.consecutiveBreachCount} döngü</dd>
                </div>
              </dl>
            )}
            {item.recommendedAction && (
              <div className="anomaly-explainer__action">
                <strong>Önerilen adım</strong>
                <p>{item.recommendedAction}</p>
              </div>
            )}
          </div>
        </section>
      ) : (
        <div className="telemetry-ok-panel" role="status">
          <CheckCircle2 aria-hidden="true" size={17} />
          <span>
            Cihaz güncel ölçümde güvenli güç sınırı içinde ve olağan düzende
            çalışıyor.
          </span>
        </div>
      )}
    </article>
  );
});
