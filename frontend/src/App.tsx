import { AlertTriangle, ChevronDown, Filter, Radio, Search, Waves, LogOut, Sun, Moon, Sparkles } from 'lucide-react';
import { lazy, Suspense, useCallback, useEffect, useMemo, useState } from 'react';
import { api, getStoredToken, setStoredToken, getUserFacingError } from './api/client';
import { EmptyState, ErrorState, DashboardSkeleton, InlineSpinner } from './components/PageStates';
import { Header } from './components/Header';
import { HomeCard } from './components/HomeCard';
import { OverviewStats } from './components/OverviewStats';
import { RegistrationModal } from './components/RegistrationModal';
import { LoginPage } from './components/LoginPage';
import { ToastProvider, useToast } from './components/ToastProvider';
import { getPollingInterval, usePollingResource } from './hooks/usePollingResource';
import type { HomeStatus } from './types';

type StatusFilter = 'ALL' | 'HEALTHY' | 'ATTENTION';

const HomeDetailModal = lazy(() =>
  import('./components/HomeDetailModal').then((module) => ({ default: module.HomeDetailModal })),
);

function Dashboard({ onLogout }: { onLogout: () => void }) {
  const statusRequest = useCallback((signal: AbortSignal) => api.getHomeStatuses(signal), []);
  const homesQuery = usePollingResource(statusRequest, getPollingInterval());
  const homes = homesQuery.data ?? [];
  const [selectedHome, setSelectedHome] = useState<HomeStatus | null>(null);
  const [registrationOpen, setRegistrationOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL');
  const [isDarkMode, setIsDarkMode] = useState(true);
  const [lang, setLang] = useState<'TR' | 'EN'>('TR');
  const { showToast } = useToast();

  const visibleHomes = useMemo(() => {
    const normalizedSearch = searchQuery.trim().toLocaleLowerCase('tr-TR');
    return homes.filter((home) => {
      const matchesSearch = !normalizedSearch || home.homeName.toLocaleLowerCase('tr-TR').includes(normalizedSearch);
      const needsAttention =
        home.anomalyCount > 0 || home.budgetUsagePercent >= 80 || home.tariffState === 'PENALTY';
      const matchesFilter =
        statusFilter === 'ALL' ||
        (statusFilter === 'HEALTHY' && !needsAttention) ||
        (statusFilter === 'ATTENTION' && needsAttention);
      return matchesSearch && matchesFilter;
    });
  }, [homes, searchQuery, statusFilter]);

  const handleDemoPreset = async () => {
    try {
      await api.registerHome({
        name: 'Demo Akıllı Daire',
        city: 'İstanbul',
        contactEmail: 'demo@voltflow.com',
        monthlyBudget: 2000,
        normalTariffPerKwh: 2.5,
        penaltyMultiplier: 1.5,
        appliances: [
          { name: 'Mutfak Buzdolabı', type: 'REFRIGERATOR', safePowerLimitWatts: 300 },
          { name: 'Çay Kettle', type: 'KETTLE', safePowerLimitWatts: 2300 },
          { name: 'Salon Kliması', type: 'AIR_CONDITIONER', safePowerLimitWatts: 2200 },
          { name: 'Oyun Bilgisayarı', type: 'COMPUTER', safePowerLimitWatts: 600 },
        ],
      });
      showToast({
        tone: 'success',
        title: 'Demo Preset Yüklendi',
        message: 'Demo Akıllı Daire 4 cihazı ile birlikte başarıyla oluşturuldu.',
      });
      homesQuery.retry();
    } catch (err) {
      showToast({
        tone: 'error',
        title: 'Preset Yüklenemedi',
        message: getUserFacingError(err),
      });
    }
  };

  return (
    <div className={`app-shell ${isDarkMode ? 'dark-mode' : 'light-mode'}`}>
      <div className="ambient ambient--one" aria-hidden="true" />
      <div className="ambient ambient--two" aria-hidden="true" />

      {/* Top Navigation & Status Header */}
      <header className="px-6 py-3 bg-slate-900/60 border-b border-slate-800/80 backdrop-blur-md flex items-center justify-between z-20">
        <div className="flex items-center gap-3">
          <Header
            isRefreshing={homesQuery.isRefreshing}
            homeCount={homes.length}
            onRegister={() => setRegistrationOpen(true)}
          />
        </div>

        <div className="flex items-center gap-2">
          {/* Quick Demo Preset Button */}
          <button
            type="button"
            onClick={handleDemoPreset}
            className="px-3 py-1.5 bg-cyan-500/10 hover:bg-cyan-500/20 border border-cyan-500/30 rounded-xl text-cyan-400 text-xs font-medium flex items-center gap-1.5 transition-all shadow-sm"
          >
            <Sparkles className="w-3.5 h-3.5" />
            <span>Demo Daire Ekle</span>
          </button>

          {/* TR / EN Toggle */}
          <button
            type="button"
            onClick={() => setLang(lang === 'TR' ? 'EN' : 'TR')}
            className="px-2.5 py-1.5 bg-slate-800/80 hover:bg-slate-700/80 border border-slate-700/60 rounded-xl text-slate-300 text-xs font-semibold transition-all"
          >
            {lang}
          </button>

          {/* Theme Toggle */}
          <button
            type="button"
            onClick={() => setIsDarkMode(!isDarkMode)}
            className="p-2 bg-slate-800/80 hover:bg-slate-700/80 border border-slate-700/60 rounded-xl text-slate-300 transition-all"
            title="Tema Değiştir"
          >
            {isDarkMode ? <Sun className="w-4 h-4 text-amber-400" /> : <Moon className="w-4 h-4 text-slate-300" />}
          </button>

          {/* Logout Button */}
          <button
            type="button"
            onClick={onLogout}
            className="px-3 py-1.5 bg-red-950/40 hover:bg-red-900/50 border border-red-800/50 rounded-xl text-red-300 text-xs font-medium flex items-center gap-1.5 transition-all"
          >
            <LogOut className="w-3.5 h-3.5" />
            <span>Çıkış</span>
          </button>
        </div>
      </header>

      <main id="main-content" className="main-content">
        <section className="dashboard-intro" aria-labelledby="dashboard-title">
          <div>
            <p className="eyebrow"><Waves aria-hidden="true" size={14} /> Canlı enerji ağı</p>
            <h1 id="dashboard-title">Enerjiniz, <span>kontrolünüz altında.</span></h1>
            <p>Evlerinizi tek bakışta izleyin; bütçe ve cihaz risklerini oluştuğu anda fark edin.</p>
          </div>
          <div className="dashboard-intro__signal" aria-label="Canlı telemetri aktif">
            <span className="signal-orbit" aria-hidden="true"><Radio size={19} /></span>
            <div><strong>Canlı telemetri</strong><span>1,5 saniyede yenilenir</span></div>
          </div>
        </section>

        {homesQuery.isLoading ? (
          <DashboardSkeleton />
        ) : homesQuery.error && homesQuery.data === undefined ? (
          <ErrorState message={getUserFacingError(homesQuery.error)} onRetry={homesQuery.retry} />
        ) : !homes.length ? (
          <EmptyState onRegister={() => setRegistrationOpen(true)} />
        ) : (
          <>
            {Boolean(homesQuery.error) && (
              <div className="stale-data-notice" role="status">
                <AlertTriangle aria-hidden="true" size={16} />
                <span>Canlı bağlantı kesildi; son başarılı veriler gösteriliyor.</span>
                <button type="button" onClick={homesQuery.retry}>Bağlanmayı dene</button>
              </div>
            )}

            <OverviewStats homes={homes} />

            <section className="homes-section" aria-labelledby="homes-title">
              <div className="section-heading">
                <div>
                  <p className="eyebrow">Portföy</p>
                  <h2 id="homes-title">Evleriniz</h2>
                  <p>{homes.length} evden gelen canlı tüketim ve bütçe durumu</p>
                </div>
                <div className="home-controls">
                  <label className="search-control">
                    <Search aria-hidden="true" size={16} />
                    <span className="sr-only">Evlerde ara</span>
                    <input
                      type="search"
                      placeholder="Evlerde ara"
                      value={searchQuery}
                      onChange={(event) => setSearchQuery(event.target.value)}
                    />
                  </label>
                  <label className="filter-control">
                    <Filter aria-hidden="true" size={15} />
                    <span className="sr-only">Duruma göre filtrele</span>
                    <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value as StatusFilter)}>
                      <option value="ALL">Tüm durumlar</option>
                      <option value="HEALTHY">Sağlıklı</option>
                      <option value="ATTENTION">İncelenmeli</option>
                    </select>
                    <ChevronDown aria-hidden="true" size={14} />
                  </label>
                </div>
              </div>

              {visibleHomes.length ? (
                <div className="home-grid">
                  {visibleHomes.map((home) => (
                    <HomeCard home={home} onSelect={setSelectedHome} key={home.homeId} />
                  ))}
                </div>
              ) : (
                <div className="filter-empty">
                  <Search aria-hidden="true" size={23} />
                  <p>Bu arama veya filtreyle eşleşen ev bulunamadı.</p>
                  <button type="button" onClick={() => { setSearchQuery(''); setStatusFilter('ALL'); }}>Filtreleri temizle</button>
                </div>
              )}
            </section>
          </>
        )}
      </main>

      <footer className="app-footer">
        <span>VoltWise enerji zekâsı</span>
        <span><i aria-hidden="true" /> Sistem izleniyor</span>
      </footer>

      {selectedHome && (
        <Suspense
          fallback={
            <div className="dialog-backdrop">
              <div className="lazy-dialog-loading" role="status">
                <InlineSpinner label="Ev detayları hazırlanıyor" />
              </div>
            </div>
          }
        >
          <HomeDetailModal summary={selectedHome} onClose={() => setSelectedHome(null)} onDeleted={homesQuery.retry} />
        </Suspense>
      )}
      {registrationOpen && (
        <RegistrationModal
          onClose={() => setRegistrationOpen(false)}
          onCreated={homesQuery.retry}
        />
      )}
    </div>
  );
}

export default function App() {
  const [authenticated, setAuthenticated] = useState<boolean>(() => Boolean(getStoredToken()));

  useEffect(() => {
    const handleUnauthorized = () => setAuthenticated(false);
    window.addEventListener('voltflow_unauthorized', handleUnauthorized);
    return () => window.removeEventListener('voltflow_unauthorized', handleUnauthorized);
  }, []);

  const handleLogout = () => {
    setStoredToken(null);
    setAuthenticated(false);
  };

  if (!authenticated) {
    return <LoginPage onLoginSuccess={() => setAuthenticated(true)} />;
  }

  return (
    <ToastProvider>
      <Dashboard onLogout={handleLogout} />
    </ToastProvider>
  );
}
