import { memo, useMemo } from 'react';
import {
  BarChart3,
  CircleDollarSign,
  Gauge,
  PieChart as PieChartIcon,
  Zap,
} from 'lucide-react';
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import type { ApplianceStatus, HistoryPoint } from '../types';
import {
  applianceTypeLabels,
  formatChartTime,
  formatEnergy,
  formatMoney,
  formatPower,
} from '../utils/format';

interface EnergyChartsProps {
  history: HistoryPoint[];
  appliances: ApplianceStatus[];
}

interface TimelinePoint extends HistoryPoint {
  label: string;
}

interface HistorySummary {
  samples: number;
  totalEnergy: number;
  totalCost: number;
  peakPower: number;
}

interface DistributionPoint {
  id: number;
  name: string;
  type: string;
  value: number;
}

interface PowerPoint {
  id: number;
  name: string;
  power: number;
  limit: number;
}

const distributionColors = [
  '#43e6a4',
  '#55c6ec',
  '#a78bfa',
  '#f7bd63',
  '#ff7d72',
  '#6ce0d5',
  '#93c5fd',
];
const axisStyle = { fill: '#83958f', fontSize: 11 };
const tooltipStyle = {
  background: '#10201c',
  border: '1px solid #294139',
  borderRadius: 10,
  color: '#edf8f3',
  boxShadow: '0 12px 30px rgb(0 0 0 / 30%)',
};

const EmptyChart = memo(function EmptyChart({ message }: { message: string }) {
  return (
    <div className="chart-empty">
      <BarChart3 aria-hidden="true" size={24} />
      <span>{message}</span>
    </div>
  );
});

const ChartHeading = memo(function ChartHeading({
  icon: Icon,
  title,
  detail,
}: {
  icon: typeof Zap;
  title: string;
  detail: string;
}) {
  return (
    <header className="chart-card__heading">
      <span aria-hidden="true">
        <Icon size={17} />
      </span>
      <div>
        <h4>{title}</h4>
        <p>{detail}</p>
      </div>
    </header>
  );
});

const HistoricalChartPanels = memo(function HistoricalChartPanels({
  timelineData,
  summary,
}: {
  timelineData: TimelinePoint[];
  summary: HistorySummary;
}) {
  const historySummary = timelineData.length
    ? `${summary.samples} saatlik kayıt. Toplam ${formatEnergy(
        summary.totalEnergy,
      )}, toplam ${formatMoney(summary.totalCost)}${
        summary.peakPower > 0
          ? `, en yüksek güç ${formatPower(summary.peakPower)}`
          : ''
      }.`
    : 'Son yedi gün için geçmiş tüketim kaydı bulunmuyor.';

  return (
    <>
      <article className="chart-card" aria-label="Zamana göre enerji tüketimi grafiği">
        <ChartHeading icon={Zap} title="Enerji tüketimi" detail="Saatlik toplam kWh" />
        <p className="sr-only">{historySummary}</p>
        <div className="chart-card__canvas" aria-hidden="true">
          {timelineData.length ? (
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={timelineData} margin={{ top: 12, right: 8, bottom: 2, left: -18 }}>
                <defs>
                  <linearGradient id="energyGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#43e6a4" stopOpacity={0.38} />
                    <stop offset="95%" stopColor="#43e6a4" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid stroke="#223a32" vertical={false} strokeDasharray="3 4" />
                <XAxis
                  dataKey="label"
                  tick={axisStyle}
                  axisLine={false}
                  tickLine={false}
                  minTickGap={32}
                />
                <YAxis tick={axisStyle} axisLine={false} tickLine={false} />
                <Tooltip
                  contentStyle={tooltipStyle}
                  formatter={(value) => [formatEnergy(Number(value ?? 0)), 'Enerji']}
                  labelStyle={{ color: '#9aada6', marginBottom: 5 }}
                />
                <Area
                  type="monotone"
                  dataKey="energyKwh"
                  stroke="#43e6a4"
                  strokeWidth={2.2}
                  fill="url(#energyGradient)"
                  activeDot={{ r: 4, fill: '#43e6a4', stroke: '#071411', strokeWidth: 2 }}
                  isAnimationActive={false}
                />
              </AreaChart>
            </ResponsiveContainer>
          ) : (
            <EmptyChart message="Geçmiş enerji verisi oluştuğunda burada görünecek." />
          )}
        </div>
      </article>

      <article className="chart-card" aria-label="Zamana göre enerji maliyeti grafiği">
        <ChartHeading icon={CircleDollarSign} title="Maliyet eğrisi" detail="Dönem içindeki değişim" />
        <p className="sr-only">{historySummary}</p>
        <div className="chart-card__canvas" aria-hidden="true">
          {timelineData.length ? (
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={timelineData} margin={{ top: 12, right: 8, bottom: 2, left: -16 }}>
                <CartesianGrid stroke="#223a32" vertical={false} strokeDasharray="3 4" />
                <XAxis
                  dataKey="label"
                  tick={axisStyle}
                  axisLine={false}
                  tickLine={false}
                  minTickGap={32}
                />
                <YAxis tick={axisStyle} axisLine={false} tickLine={false} />
                <Tooltip
                  contentStyle={tooltipStyle}
                  formatter={(value) => [formatMoney(Number(value ?? 0)), 'Maliyet']}
                  labelStyle={{ color: '#9aada6', marginBottom: 5 }}
                />
                <Line
                  type="monotone"
                  dataKey="cost"
                  stroke="#55c6ec"
                  strokeWidth={2.2}
                  dot={false}
                  activeDot={{ r: 4, fill: '#55c6ec', stroke: '#071411', strokeWidth: 2 }}
                  isAnimationActive={false}
                />
              </LineChart>
            </ResponsiveContainer>
          ) : (
            <EmptyChart message="Maliyet geçmişi henüz hazır değil." />
          )}
        </div>
      </article>
    </>
  );
});

const ApplianceChartPanels = memo(function ApplianceChartPanels({
  distributionData,
  powerData,
}: {
  distributionData: DistributionPoint[];
  powerData: PowerPoint[];
}) {
  const distributionTotal = distributionData.reduce(
    (total, appliance) => total + appliance.value,
    0,
  );
  const overLimitCount = powerData.filter(
    (appliance) => appliance.limit > 0 && appliance.power > appliance.limit,
  ).length;

  return (
    <>
      <article className="chart-card" aria-label="Cihaz enerji tüketim dağılımı grafiği">
        <ChartHeading icon={PieChartIcon} title="Cihaz dağılımı" detail="Toplam enerjideki pay" />
        <p className="sr-only">
          {distributionData.length
            ? `${distributionData.length} cihaz toplam ${formatEnergy(
                distributionTotal,
              )} birikmiş enerji tüketti.`
            : 'Cihaz enerji dağılımı için henüz veri bulunmuyor.'}
        </p>
        <div className="chart-card__canvas chart-card__canvas--pie" aria-hidden="true">
          {distributionData.length ? (
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={distributionData}
                  dataKey="value"
                  nameKey="name"
                  cx="50%"
                  cy="46%"
                  innerRadius={45}
                  outerRadius={72}
                  paddingAngle={3}
                  stroke="none"
                  isAnimationActive={false}
                >
                  {distributionData.map((entry, index) => (
                    <Cell
                      key={entry.id}
                      fill={distributionColors[index % distributionColors.length]}
                    />
                  ))}
                </Pie>
                <Tooltip
                  contentStyle={tooltipStyle}
                  formatter={(value) => formatEnergy(Number(value ?? 0))}
                />
                <Legend
                  iconType="circle"
                  iconSize={7}
                  wrapperStyle={{ fontSize: 11, color: '#a5b6b0' }}
                />
              </PieChart>
            </ResponsiveContainer>
          ) : (
            <EmptyChart message="Cihaz tüketimi başladığında dağılım oluşacak." />
          )}
        </div>
      </article>

      <article className="chart-card" aria-label="Cihaz anlık güç karşılaştırması grafiği">
        <ChartHeading icon={Gauge} title="Anlık güç karşılaştırması" detail="Tüketim ve güvenli sınır" />
        <p className="sr-only">
          {powerData.length
            ? `${powerData.length} cihazdan ${overLimitCount} tanesi güvenli güç sınırının üzerinde.`
            : 'Kayıtlı cihaz bulunmuyor.'}
        </p>
        <div className="chart-card__canvas" aria-hidden="true">
          {powerData.length ? (
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={powerData} margin={{ top: 12, right: 5, bottom: 2, left: -14 }}>
                <CartesianGrid stroke="#223a32" vertical={false} strokeDasharray="3 4" />
                <XAxis dataKey="name" tick={axisStyle} axisLine={false} tickLine={false} />
                <YAxis tick={axisStyle} axisLine={false} tickLine={false} />
                <Tooltip
                  contentStyle={tooltipStyle}
                  formatter={(value, name) => [
                    formatPower(Number(value ?? 0)),
                    name === 'power' ? 'Anlık güç' : 'Güvenli sınır',
                  ]}
                />
                <Legend
                  formatter={(value) =>
                    value === 'power' ? 'Anlık güç' : 'Güvenli sınır'
                  }
                  wrapperStyle={{ fontSize: 11 }}
                />
                <Bar
                  dataKey="power"
                  fill="#43e6a4"
                  radius={[5, 5, 0, 0]}
                  isAnimationActive={false}
                />
                <Bar
                  dataKey="limit"
                  fill="#40564f"
                  radius={[5, 5, 0, 0]}
                  isAnimationActive={false}
                />
              </BarChart>
            </ResponsiveContainer>
          ) : (
            <EmptyChart message="Kayıtlı cihaz bulunmuyor." />
          )}
        </div>
      </article>
    </>
  );
});

export function EnergyCharts({ history, appliances }: EnergyChartsProps) {
  const { timelineData, historySummary } = useMemo(() => {
    const timeline = history
      .filter((point) => point.periodStart)
      .slice()
      .sort((a, b) => Date.parse(a.periodStart) - Date.parse(b.periodStart))
      .map((point) => ({
        ...point,
        label: formatChartTime(point.periodStart),
      }));

    const summary = timeline.reduce<HistorySummary>(
      (totals, point) => ({
        samples: totals.samples + 1,
        totalEnergy: totals.totalEnergy + point.energyKwh,
        totalCost: totals.totalCost + point.cost,
        peakPower: Math.max(
          totals.peakPower,
          point.maximumPowerWatts ?? point.averagePowerWatts ?? 0,
        ),
      }),
      { samples: 0, totalEnergy: 0, totalCost: 0, peakPower: 0 },
    );

    return { timelineData: timeline, historySummary: summary };
  }, [history]);

  const distributionData = useMemo<DistributionPoint[]>(
    () =>
      appliances
        .filter((appliance) => appliance.accumulatedEnergyKwh > 0)
        .map((appliance) => ({
          id: appliance.applianceId,
          name: appliance.name,
          type: applianceTypeLabels[appliance.type],
          value: appliance.accumulatedEnergyKwh,
        })),
    [appliances],
  );

  const powerData = useMemo<PowerPoint[]>(
    () =>
      appliances.map((appliance) => ({
        id: appliance.applianceId,
        name:
          appliance.name.length > 14
            ? `${appliance.name.slice(0, 13)}…`
            : appliance.name,
        power: appliance.currentPowerWatts,
        limit: appliance.safePowerLimitWatts,
      })),
    [appliances],
  );

  return (
    <section className="charts-section" aria-labelledby="charts-title">
      <div className="section-heading section-heading--compact">
        <div>
          <p className="eyebrow">Analitik</p>
          <h3 id="charts-title">Enerji görünümü</h3>
        </div>
        <span className="section-heading__meta">
          7 günlük geçmiş · canlı cihaz görünümü
        </span>
      </div>

      <div className="charts-grid">
        <HistoricalChartPanels
          timelineData={timelineData}
          summary={historySummary}
        />
        <ApplianceChartPanels
          distributionData={distributionData}
          powerData={powerData}
        />
      </div>
    </section>
  );
}
