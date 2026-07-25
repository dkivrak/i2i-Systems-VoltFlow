import type { PeakHourAdvisoryResponse } from '../../types';

interface PeakHourBannerProps {
  advisory?: PeakHourAdvisoryResponse | null;
  loading?: boolean;
}

export function PeakHourBanner({ advisory, loading }: PeakHourBannerProps) {
  if (loading) {
    return (
      <div className="peak-hour-banner peak-hour-banner--loading" aria-busy="true">
        <div className="peak-hour-banner__skeleton" />
      </div>
    );
  }

  if (!advisory) return null;

  const { isPeakHour, peakWindowText, normalTariffPerKwh, peakTariffPerKwh, estimatedTotalSavingsTl, advisories } = advisory;

  return (
    <section aria-labelledby="peak-hour-heading" className={`peak-hour-banner ${isPeakHour ? 'peak-hour-banner--active' : 'peak-hour-banner--offpeak'}`}>
      <div className="peak-hour-banner__header">
        <div className="peak-hour-banner__title-group">
          <span className="peak-hour-banner__icon" aria-hidden="true">⚡</span>
          <div>
            <h3 id="peak-hour-heading" className="peak-hour-banner__title">
              Pik Saat Tarife Optimize Etme
            </h3>
            <p className="peak-hour-banner__subtitle">
              Yoğun tüketim saatleri ({peakWindowText}) için tasarruf fırsatları
            </p>
          </div>
        </div>

        <div className="peak-hour-banner__badges">
          <span className={`peak-badge ${isPeakHour ? 'peak-badge--peak' : 'peak-badge--normal'}`}>
            <span className="peak-badge__dot" />
            {isPeakHour ? `Pik Tarife Aktif (${peakTariffPerKwh.toFixed(2)} ₺/kWh)` : `Normal Tarife (${normalTariffPerKwh.toFixed(2)} ₺/kWh)`}
          </span>
          {estimatedTotalSavingsTl > 0 && (
            <span className="peak-badge peak-badge--savings">
              Tahmini Tasarruf: +₺{estimatedTotalSavingsTl.toFixed(0)}
            </span>
          )}
        </div>
      </div>

      {advisories && advisories.length > 0 ? (
        <div className="peak-hour-banner__list">
          {advisories.map((item) => (
            <div key={item.applianceId} className="peak-advice-item">
              <div className="peak-advice-item__icon" aria-hidden="true">
                💡
              </div>
              <div className="peak-advice-item__content">
                <p className="peak-advice-item__message">{item.recommendationMessage}</p>
                {item.currentPowerWatts > 0 && (
                  <span className="peak-advice-item__power">
                    Anlık Anma Gücü: {item.currentPowerWatts.toFixed(0)} W
                  </span>
                )}
              </div>
              <div className="peak-advice-item__saving-badge">
                +₺{item.estimatedSavingsTl.toFixed(0)} Tasarruf
              </div>
            </div>
          ))}
        </div>
      ) : (
        <p className="peak-hour-banner__empty">
          Şu anda pik saatlerde ertelenebilecek yüksek tüketimli cihaz bulunmuyor.
        </p>
      )}
    </section>
  );
}
