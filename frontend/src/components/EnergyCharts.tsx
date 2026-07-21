import { BarChart3, CircleDollarSign, Gauge, PieChart as PieChartIcon, Zap } from 'lucide-react';
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
import { applianceTypeLabels, formatChartTime, formatEnergy, formatMoney, formatPower } from '../utils/format';

interface EnergyChartsProps {
  history: HistoryPoint[];
  appliances: ApplianceStatus[];
}

const distributionColors = ['#43e6a4', '#55c6ec', '#a78bfa', '#f7bd63', '#ff7d72', '#6ce0d5', '#93c5fd'];
const axisStyle = { fill: '#83958f', fontSize: 11 };
const tooltipStyle = {
  background: '#10201c',
  border: '1px solid #294139',
  borderRadius: 10,
  color: '#edf8f3',
  boxShadow: '0 12px 30px rgb(0 0 0 / 30%)',
};

function EmptyChart({ message }: { message: string }) {
  return (
    <div className="chart-empty">
      <BarChart3 aria-hidden="true" size={24} />
      <span>{message}</span>
    </div>
  );
}

function ChartHeading({ icon: Icon, title, detail }: { icon: typeof Zap; title: string; detail: string }) {
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
}

export function EnergyCharts({ history, appliances }: EnergyChartsProps) {
  const timelineData = history
    .filter((point) => point.periodStart)
    .slice()
    .sort((a, b) => Date.parse(a.periodStart) - Date.parse(b.periodStart))
    .map((point) => ({ ...point, label: formatChartTime(point.periodStart) }));
  const distributionData = appliances
    .filter((appliance) => appliance.accumulatedEnergyKwh > 0)
    .map((appliance) => ({
      name: appliance.name,
      type: applianceTypeLabels[appliance.type],
      value: appliance.accumulatedEnergyKwh,
    }));
  const powerData = appliances.map((appliance) => ({
    name: appliance.name.length > 14 ? `${appliance.name.slice(0, 13)}…` : appliance.name,
    power: appliance.currentPowerWatts,
    limit: appliance.safePowerLimitWatts,
  }));

  return (
    <section className="charts-section" aria-labelledby="charts-title">
      <div className="section-heading section-heading--compact">
        <div>
          <p className="eyebrow">Analitik</p>
          <h3 id="charts-title">Enerji görünümü</h3>
        </div>
        <span className="section-heading__meta">Son 7 gün · saatlik</span>
      </div>

      <div className="charts-grid">
        <article className="chart-card" aria-label="Zamana göre enerji tüketimi grafiği">
          <ChartHeading icon={Zap} title="Enerji tüketimi" detail="Saatlik toplam kWh" />
          <div className="chart-card__canvas">
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
                  <XAxis dataKey="label" tick={axisStyle} axisLine={false} tickLine={false} minTickGap={32} />
                  <YAxis tick={axisStyle} axisLine={false} tickLine={false} />
                  <Tooltip
                    contentStyle={tooltipStyle}
                    formatter={(value) => [formatEnergy(Number(value)), 'Enerji']}
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
          <div className="chart-card__canvas">
            {timelineData.length ? (
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={timelineData} margin={{ top: 12, right: 8, bottom: 2, left: -16 }}>
                  <CartesianGrid stroke="#223a32" vertical={false} strokeDasharray="3 4" />
                  <XAxis dataKey="label" tick={axisStyle} axisLine={false} tickLine={false} minTickGap={32} />
                  <YAxis tick={axisStyle} axisLine={false} tickLine={false} />
                  <Tooltip
                    contentStyle={tooltipStyle}
                    formatter={(value) => [formatMoney(Number(value)), 'Maliyet']}
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

        <article className="chart-card" aria-label="Cihaz enerji tüketim dağılımı grafiği">
          <ChartHeading icon={PieChartIcon} title="Cihaz dağılımı" detail="Toplam enerjideki pay" />
          <div className="chart-card__canvas chart-card__canvas--pie">
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
                      <Cell key={entry.name} fill={distributionColors[index % distributionColors.length]} />
                    ))}
                  </Pie>
                  <Tooltip contentStyle={tooltipStyle} formatter={(value) => formatEnergy(Number(value))} />
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
          <div className="chart-card__canvas">
            {powerData.length ? (
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={powerData} margin={{ top: 12, right: 5, bottom: 2, left: -14 }}>
                  <CartesianGrid stroke="#223a32" vertical={false} strokeDasharray="3 4" />
                  <XAxis dataKey="name" tick={axisStyle} axisLine={false} tickLine={false} />
                  <YAxis tick={axisStyle} axisLine={false} tickLine={false} />
                  <Tooltip
                    contentStyle={tooltipStyle}
                    formatter={(value, name) => [formatPower(Number(value)), name === 'power' ? 'Anlık güç' : 'Güvenli sınır']}
                  />
                  <Legend
                    formatter={(value) => (value === 'power' ? 'Anlık güç' : 'Güvenli sınır')}
                    wrapperStyle={{ fontSize: 11 }}
                  />
                  <Bar dataKey="power" fill="#43e6a4" radius={[5, 5, 0, 0]} isAnimationActive={false} />
                  <Bar dataKey="limit" fill="#40564f" radius={[5, 5, 0, 0]} isAnimationActive={false} />
                </BarChart>
              </ResponsiveContainer>
            ) : (
              <EmptyChart message="Kayıtlı cihaz bulunmuyor." />
            )}
          </div>
        </article>
      </div>
    </section>
  );
}
