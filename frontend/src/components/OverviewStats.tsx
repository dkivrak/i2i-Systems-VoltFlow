import { Activity, CircleDollarSign, HousePlug, TriangleAlert } from 'lucide-react';
import type { HomeStatus } from '../types';
import { formatMoney, formatPower } from '../utils/format';

interface OverviewStatsProps {
  homes: HomeStatus[];
}

export function OverviewStats({ homes }: OverviewStatsProps) {
  const totalPower = homes.reduce((total, home) => total + home.currentPowerWatts, 0);
  const totalCost = homes.reduce((total, home) => total + home.currentCost, 0);
  const anomalies = homes.reduce((total, home) => total + home.anomalyCount, 0);
  const healthyHomes = homes.filter((home) => home.anomalyCount === 0 && home.budgetUsagePercent < 80).length;

  const stats = [
    {
      label: 'Anlık toplam güç',
      value: formatPower(totalPower),
      note: 'Tüm evlerde şimdi',
      icon: Activity,
      tone: 'cyan',
    },
    {
      label: 'Bu dönem maliyet',
      value: formatMoney(totalCost),
      note: `${homes.length} evin toplamı`,
      icon: CircleDollarSign,
      tone: 'green',
    },
    {
      label: 'Sağlıklı evler',
      value: `${healthyHomes} / ${homes.length}`,
      note: 'Bütçe ve cihaz durumu',
      icon: HousePlug,
      tone: 'blue',
    },
    {
      label: 'Aktif anomaliler',
      value: String(anomalies),
      note: anomalies ? 'İncelenmesi gerekiyor' : 'Her şey yolunda',
      icon: TriangleAlert,
      tone: anomalies ? 'orange' : 'muted',
    },
  ] as const;

  return (
    <section className="overview-stats" aria-label="Genel enerji özeti">
      {stats.map(({ label, value, note, icon: Icon, tone }) => (
        <article className="stat-card" key={label}>
          <span className={`stat-card__icon stat-card__icon--${tone}`} aria-hidden="true">
            <Icon size={20} />
          </span>
          <div>
            <p className="stat-card__label">{label}</p>
            <p className="stat-card__value">{value}</p>
            <p className="stat-card__note">{note}</p>
          </div>
        </article>
      ))}
    </section>
  );
}
