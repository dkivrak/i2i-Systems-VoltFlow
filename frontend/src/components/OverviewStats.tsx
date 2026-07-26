import { memo } from 'react';
import { Activity, Banknote, HousePlug, TriangleAlert } from 'lucide-react';
import { summarizeHomeAttention } from '../presentation/homePresentation';
import type { HomeStatus } from '../types';
import { formatMoney, formatPower } from '../utils/format';

interface OverviewStatsProps {
  homes: HomeStatus[];
  asOf?: number;
}

export const OverviewStats = memo(function OverviewStats({
  homes,
  asOf = Date.now(),
}: OverviewStatsProps) {
  const totalPower = homes.reduce((total, home) => total + home.currentPowerWatts, 0);
  const totalCost = homes.reduce((total, home) => total + home.currentCost, 0);
  const anomalies = homes.reduce((total, home) => total + home.anomalyCount, 0);
  const healthyHomes = homes.filter(
    (home) => !summarizeHomeAttention(home, asOf).needsAttention,
  ).length;
  const budgetRiskHomes = homes.filter(
    (home) => home.budgetUsagePercent >= 80 || home.tariffState === 'PENALTY',
  ).length;

  const stats = [
    {
      label: 'Anlık toplam güç',
      value: formatPower(totalPower),
      note: 'Tüm bağlı evlerde şimdi',
      status: homes.length ? 'Canlı ölçüm' : 'Veri bekleniyor',
      icon: Activity,
      tone: 'cyan',
    },
    {
      label: 'Bu dönem maliyet',
      value: formatMoney(totalCost),
      note: `${homes.length} evin toplamı`,
      status: budgetRiskHomes ? `${budgetRiskHomes} bütçe uyarısı` : 'Bütçe dengede',
      icon: Banknote,
      tone: budgetRiskHomes ? 'orange' : 'green',
    },
    {
      label: 'Sağlıklı evler',
      value: `${healthyHomes} / ${homes.length}`,
      note: 'Bütçe, tarife ve cihaz durumu',
      status: healthyHomes === homes.length && homes.length ? 'Tümü normal' : `${homes.length - healthyHomes} ev incelenmeli`,
      icon: HousePlug,
      tone: healthyHomes === homes.length ? 'blue' : 'orange',
    },
    {
      label: 'Aktif anomaliler',
      value: String(anomalies),
      note: anomalies ? 'İncelenmesi gerekiyor' : 'Her şey yolunda',
      status: anomalies ? 'Eylem gerekli' : 'Aktif uyarı yok',
      icon: TriangleAlert,
      tone: anomalies ? 'orange' : 'muted',
    },
  ] as const;

  return (
    <section className="overview-stats" aria-label="Genel enerji özeti">
      {stats.map(({ label, value, note, status, icon: Icon, tone }) => (
        <article className={`stat-card stat-card--${tone}`} data-status={tone} key={label}>
          <span className={`stat-card__icon stat-card__icon--${tone}`} aria-hidden="true">
            <Icon size={20} />
          </span>
          <div className="stat-card__copy">
            <p className="stat-card__label">{label}</p>
            <p className="stat-card__value">{value}</p>
            <p className="stat-card__note">{note}</p>
            <span className="stat-card__status">
              <i aria-hidden="true" /> {status}
            </span>
          </div>
        </article>
      ))}
    </section>
  );
});
