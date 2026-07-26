import { memo, useMemo } from 'react';
import {
  Banknote,
  BarChart3,
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
  '#6C47FF',
  '#00D284',
  '#FFB800',
  '#00C2FF',
  '#FF4757',
  '#FF8500',
  '#9B51E0',
];

const axisStyle = { fill: '#494459', fontSize: 11, fontWeight: 700 };

const tooltipStyle = {
  background: '#211D34',
  border: '2px solid #211D34',
  borderRadius: 12,
  color: '#FFFFFF',
  boxShadow: '4px 4px 0 #211D34',
  padding: '10px 14px',
  fontWeight: 700,
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
              <AreaChart data={timelineData} margin={{ top: 12, right: 8, bottom: 2, left: -10 }}>
                <defs>
                  <linearGradient id="energyGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#6C47FF" stopOpacity={0.35} />
                    <stop offset="95%" stopColor="#6C47FF" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid stroke="#E2DDED" vertical={false} strokeDasharray="3 4" />
                <XAxis
                  dataKey="label"
                  tick={axisStyle}
                  axisLine={false}
                  tickLine={false}
                  minTickGap={32}
                />
                <YAxis tick={axisStyle} axisLine={false} tickLine={false} tickFormatter={(v) => `${v} kWh`} />
                <Tooltip
                  contentStyle={tooltipStyle}
                  formatter={(value) => [formatEnergy(Number(value ?? 0)), 'Enerji']}
                  labelStyle={{ color: '#FFD600', marginBottom: 4, fontWeight: 800 }}
                />
                <Area
                  type="monotone"
                  dataKey="energyKwh"
                  stroke="#6C47FF"
                  strokeWidth={3}
                  fill="url(#energyGradient)"
                  activeDot={{ r: 5, fill: '#6C47FF', stroke: '#211D34', strokeWidth: 2 }}
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
        <ChartHeading icon={Banknote} title="Maliyet eğrisi" detail="Dönem içindeki değişim" />
        <p className="sr-only">{historySummary}</p>
        <div className="chart-card__canvas" aria-hidden="true">
          {timelineData.length ? (
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={timelineData} margin={{ top: 12, right: 8, bottom: 2, left: -10 }}>
                <CartesianGrid stroke="#E2DDED" vertical={false} strokeDasharray="3 4" />
                <XAxis
                  dataKey="label"
                  tick={axisStyle}
                  axisLine={false}
                  tickLine={false}
                  minTickGap={32}
                />
                <YAxis tick={axisStyle} axisLine={false} tickLine={false} tickFormatter={(v) => `₺${v}`} />
                <Tooltip
                  contentStyle={tooltipStyle}
                  formatter={(value) => [formatMoney(Number(value ?? 0)), 'Maliyet']}
                  labelStyle={{ color: '#FFD600', marginBottom: 4, fontWeight: 800 }}
                />
                <Line
                  type="monotone"
                  dataKey="cost"
                  stroke="#00C2FF"
                  strokeWidth={3}
                  dot={false}
                  activeDot={{ r: 5, fill: '#00C2FF', stroke: '#211D34', strokeWidth: 2 }}
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
              <BarChart data={powerData} margin={{ top: 12, right: 5, bottom: 2, left: -10 }}>
                <CartesianGrid stroke="#E2DDED" vertical={false} strokeDasharray="3 4" />
                <XAxis dataKey="name" tick={axisStyle} axisLine={false} tickLine={false} />
                <YAxis tick={axisStyle} axisLine={false} tickLine={false} tickFormatter={(v) => `${v}W`} />
                <Tooltip
                  contentStyle={tooltipStyle}
                  formatter={(value, name) => [
                    formatPower(Number(value ?? 0)),
                    name === 'power' ? 'Anlık güç' : 'Güvenli sınır',
                  ]}
                  labelStyle={{ color: '#FFD600', marginBottom: 4, fontWeight: 800 }}
                />
                <Legend
                  formatter={(value) =>
                    value === 'power' ? 'Anlık güç' : 'Güvenli sınır'
                  }
                  wrapperStyle={{ fontSize: 11, fontWeight: 700 }}
                />
                <Bar
                  dataKey="power"
                  fill="#00D284"
                  radius={[5, 5, 0, 0]}
                  isAnimationActive={false}
                />
                <Bar
                  dataKey="limit"
                  fill="#6C47FF"
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
