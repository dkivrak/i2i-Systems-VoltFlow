import {
  ChevronDown,
  Filter,
  Radio,
  Search,
  Waves,
} from 'lucide-react';
import {
  lazy,
  Suspense,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import {
  api,
  getStoredToken,
  getUserFacingError,
  setStoredToken,
} from './api/client';
import { Header } from './components/Header';
import { HomeCard } from './components/HomeCard';
import { LandingPage } from './components/LandingPage';
import { LoginPage, type AuthMode } from './components/LoginPage';
import { OverviewStats } from './components/OverviewStats';
import { Dialog } from './components/Dialog';
import {
  DashboardSkeleton,
  EmptyState,
  ErrorState,
  InlineSpinner,
  StaleDataNotice,
} from './components/PageStates';
import { RegistrationModal } from './components/RegistrationModal';
import { ToastProvider } from './components/ToastProvider';
import {
  getPollingInterval,
  usePollingResource,
} from './hooks/usePollingResource';
import { summarizeHomeAttention } from './presentation/homePresentation';
import type { HomeStatus } from './types';

type StatusFilter = 'ALL' | 'HEALTHY' | 'ATTENTION';
type AppRoute = 'LANDING' | 'LOGIN' | 'REGISTER' | 'DASHBOARD';

const HomeDetailModal = lazy(() =>
  import('./components/HomeDetailModal').then((module) => ({
    default: module.HomeDetailModal,
  })),
);

function routeFromLocation(authenticated: boolean): AppRoute {
  const pathname = window.location.pathname.replace(/\/+$/, '') || '/';
  if (pathname === '/login') return authenticated ? 'DASHBOARD' : 'LOGIN';
  if (pathname === '/register') return authenticated ? 'DASHBOARD' : 'REGISTER';
  if (pathname === '/dashboard') return authenticated ? 'DASHBOARD' : 'LOGIN';
  return 'LANDING';
}

const routePaths: Record<AppRoute, string> = {
  LANDING: '/',
  LOGIN: '/login',
  REGISTER: '/register',
  DASHBOARD: '/dashboard',
};

interface DashboardProps {
  onLogout: () => void;
}

function Dashboard({ onLogout }: DashboardProps) {
  const [selectedHomeId, setSelectedHomeId] = useState<number | null>(null);
  const [registrationOpen, setRegistrationOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL');
  const [statusAnnouncement, setStatusAnnouncement] = useState('');
  const previousImportantStatus = useRef<string | null>(null);
  const statusRequest = useCallback(
    (signal: AbortSignal) => api.getHomeStatuses(signal),
    [],
  );
  const homesQuery = usePollingResource(
    statusRequest,
    getPollingInterval(),
    selectedHomeId === null && !registrationOpen,
  );
  const homes = homesQuery.data ?? [];
  const presentationEpoch =
    Math.floor(
      (homesQuery.lastUpdatedAt?.getTime() ?? Date.now()) / 5_000,
    ) * 5_000;
  const handleSelectHome = useCallback((home: HomeStatus) => {
    setSelectedHomeId(home.homeId);
  }, []);

  const selectedHome = useMemo(
    () => homes.find((home) => home.homeId === selectedHomeId) ?? null,
    [homes, selectedHomeId],
  );

  useEffect(() => {
    if (selectedHomeId !== null && !selectedHome && !homesQuery.isLoading) {
      setSelectedHomeId(null);
    }
  }, [homesQuery.isLoading, selectedHome, selectedHomeId]);

  const visibleHomes = useMemo(() => {
    const normalizedSearch = searchQuery.trim().toLocaleLowerCase('tr-TR');
    return homes.filter((home) => {
      const matchesSearch =
        !normalizedSearch ||
        home.homeName.toLocaleLowerCase('tr-TR').includes(normalizedSearch) ||
        home.city?.toLocaleLowerCase('tr-TR').includes(normalizedSearch);
      const needsAttention = summarizeHomeAttention(
        home,
        presentationEpoch,
      ).needsAttention;
      const matchesFilter =
        statusFilter === 'ALL' ||
        (statusFilter === 'HEALTHY' && !needsAttention) ||
        (statusFilter === 'ATTENTION' && needsAttention);
      return Boolean(matchesSearch && matchesFilter);
    });
  }, [homes, presentationEpoch, searchQuery, statusFilter]);

  const importantStatus = useMemo(() => {
    const anomalyCount = homes.reduce(
      (total, home) => total + home.anomalyCount,
      0,
    );
    const attentionCount = homes.filter(
      (home) =>
        summarizeHomeAttention(home, presentationEpoch).needsAttention,
    ).length;
    return { anomalyCount, attentionCount };
  }, [homes, presentationEpoch]);
  const staleHomeCount = useMemo(
    () =>
      homes.filter(
        (home) =>
          summarizeHomeAttention(home, presentationEpoch)
            .hasConnectivityRisk,
      ).length,
    [homes, presentationEpoch],
  );

  useEffect(() => {
    if (!homes.length) {
      previousImportantStatus.current = null;
      return;
    }
    const nextKey = `${importantStatus.anomalyCount}:${importantStatus.attentionCount}`;
    if (previousImportantStatus.current === null) {
      previousImportantStatus.current = nextKey;
      return;
    }
    if (previousImportantStatus.current === nextKey) return;
    previousImportantStatus.current = nextKey;
    setStatusAnnouncement(
      importantStatus.anomalyCount
        ? `${importantStatus.anomalyCount} aktif anomali, ${importantStatus.attentionCount} ev incelenmeli.`
        : importantStatus.attentionCount
          ? `${importantStatus.attentionCount} ev bağlantı, cihaz veya bütçe durumu nedeniyle incelenmeli.`
          : 'Tüm evler yeniden normal duruma döndü.',
    );
  }, [homes.length, importantStatus]);

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">
        Ana içeriğe geç
      </a>
      <div className="ambient ambient--one" aria-hidden="true" />
      <div className="ambient ambient--two" aria-hidden="true" />

      <Header
        isRefreshing={homesQuery.isRefreshing}
        hasConnectionError={Boolean(homesQuery.error)}
        staleHomeCount={staleHomeCount}
        homeCount={homes.length}
        onRegister={() => setRegistrationOpen(true)}
        onLogout={onLogout}
      />

      <main id="main-content" className="main-content" tabIndex={-1}>
        <section className="dashboard-intro" aria-labelledby="dashboard-title">
          <div>
            <p className="eyebrow">
              <Waves aria-hidden="true" size={14} /> Canlı enerji ağı
            </p>
            <h1 id="dashboard-title">
              Evinizin enerjisi, <span>tek bakışta anlaşılır.</span>
            </h1>
            <p>
              Tüketimi, maliyeti ve cihaz sağlığını canlı izleyin; VoltWise
              bütçe risklerini büyümeden görünür kılar.
            </p>
          </div>
          <div className="dashboard-intro__signal" aria-label="Canlı telemetri aktif">
            <span className="signal-orbit" aria-hidden="true">
              <Radio size={19} />
            </span>
            <div>
              <strong>Canlı telemetri</strong>
              <span>Her 1–2 saniyede güvenle yenilenir</span>
            </div>
          </div>
        </section>

        {homesQuery.isLoading ? (
          <DashboardSkeleton />
        ) : homesQuery.error && homesQuery.data === undefined ? (
          <ErrorState
            message={getUserFacingError(homesQuery.error)}
            onRetry={homesQuery.retry}
          />
        ) : !homes.length ? (
          <EmptyState onRegister={() => setRegistrationOpen(true)} />
        ) : (
          <>
            {Boolean(homesQuery.error) && (
              <StaleDataNotice
                lastUpdatedLabel={homesQuery.lastUpdatedAt?.toLocaleTimeString('tr-TR', {
                  hour: '2-digit',
                  minute: '2-digit',
                  second: '2-digit',
                })}
                onRetry={homesQuery.retry}
              />
            )}

            <div className="sr-only" role="status" aria-live="polite">
              {statusAnnouncement}
            </div>

            <OverviewStats homes={homes} asOf={presentationEpoch} />

            <section className="homes-section" aria-labelledby="homes-title">
              <div className="section-heading">
                <div>
                  <p className="eyebrow">Akıllı evleriniz</p>
                  <h2 id="homes-title">Enerji mahallesi</h2>
                  <p>
                    {homes.length} evden gelen canlı tüketim, bütçe ve cihaz
                    sağlık durumu
                  </p>
                </div>
                <div className="home-controls">
                  <label className="search-control">
                    <Search aria-hidden="true" size={16} />
                    <span className="sr-only">Evlerde ara</span>
                    <input
                      type="search"
                      placeholder="Ev veya şehir ara"
                      value={searchQuery}
                      onChange={(event) => setSearchQuery(event.target.value)}
                    />
                  </label>
                  <label className="filter-control">
                    <Filter aria-hidden="true" size={15} />
                    <span className="sr-only">Duruma göre filtrele</span>
                    <select
                      value={statusFilter}
                      onChange={(event) =>
                        setStatusFilter(event.target.value as StatusFilter)
                      }
                    >
                      <option value="ALL">Tüm durumlar</option>
                      <option value="HEALTHY">Her şey yolunda</option>
                      <option value="ATTENTION">İncelenmeli</option>
                    </select>
                    <ChevronDown aria-hidden="true" size={14} />
                  </label>
                </div>
              </div>

              <p className="sr-only" role="status" aria-live="polite">
                {visibleHomes.length} ev gösteriliyor.
              </p>

              {visibleHomes.length ? (
                <div className="home-grid">
                  {visibleHomes.map((home) => (
                    <HomeCard
                      home={home}
                      onSelect={handleSelectHome}
                      key={home.homeId}
                    />
                  ))}
                </div>
              ) : (
                <div className="filter-empty">
                  <Search aria-hidden="true" size={24} />
                  <p>Bu arama veya filtreyle eşleşen bir ev bulunamadı.</p>
                  <button
                    type="button"
                    onClick={() => {
                      setSearchQuery('');
                      setStatusFilter('ALL');
                    }}
                  >
                    Filtreleri temizle
                  </button>
                </div>
              )}
            </section>
          </>
        )}
      </main>

      <footer className="app-footer">
        <span>VoltWise · evinizin enerji arkadaşı</span>
        <span>
          <i aria-hidden="true" /> Sistem izleniyor
        </span>
      </footer>

      {selectedHome && (
        <Suspense
          fallback={
            <Dialog
              title="Ev ayrıntıları hazırlanıyor"
              eyebrow="Canlı ev görünümü"
              description={`${selectedHome.homeName} için canlı veriler hazırlanıyor.`}
              onClose={() => setSelectedHomeId(null)}
              wide
            >
              <div className="lazy-dialog-content" role="status">
                <InlineSpinner label="Ev detayları hazırlanıyor" />
              </div>
            </Dialog>
          }
        >
          <HomeDetailModal
            summary={selectedHome}
            onClose={() => setSelectedHomeId(null)}
            onDeleted={() => {
              setSelectedHomeId(null);
              homesQuery.retry();
            }}
          />
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
  const [authenticated, setAuthenticated] = useState<boolean>(() =>
    Boolean(getStoredToken()),
  );
  const [route, setRoute] = useState<AppRoute>(() =>
    routeFromLocation(Boolean(getStoredToken())),
  );

  const navigate = useCallback(
    (nextRoute: AppRoute, replace = false) => {
      const path = routePaths[nextRoute];
      if (window.location.pathname !== path) {
        if (replace) window.history.replaceState({}, '', path);
        else window.history.pushState({}, '', path);
      }
      setRoute(nextRoute);
      window.scrollTo({
        top: 0,
        behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches
          ? 'auto'
          : 'smooth',
      });
    },
    [],
  );

  useEffect(() => {
    const visibleRoute = route;
    if (route === 'DASHBOARD' && authenticated &&
        window.location.pathname !== routePaths.DASHBOARD) {
      window.history.replaceState({}, '', routePaths.DASHBOARD);
    } else if (!authenticated && window.location.pathname === routePaths.DASHBOARD) {
      window.history.replaceState({}, '', routePaths.LOGIN);
    }
    const routeTitles: Record<AppRoute, string> = {
      LANDING: 'VoltWise · Akıllı Ev Enerji Takibi',
      LOGIN: 'Giriş Yap · VoltWise',
      REGISTER: 'Kaydol · VoltWise',
      DASHBOARD: 'Enerji Paneli · VoltWise',
    };
    const routeTargets: Record<AppRoute, string> = {
      LANDING: 'landing-main',
      LOGIN: 'auth-main',
      REGISTER: 'auth-main',
      DASHBOARD: 'main-content',
    };
    document.title = routeTitles[visibleRoute];
    const frame = window.requestAnimationFrame(() => {
      document.getElementById(routeTargets[visibleRoute])?.focus({
        preventScroll: true,
      });
    });
    return () => window.cancelAnimationFrame(frame);
  }, [authenticated, route]);

  useEffect(() => {
    const handleUnauthorized = () => {
      setAuthenticated(false);
      navigate('LOGIN', true);
    };
    const handlePopState = () => {
      setRoute(routeFromLocation(Boolean(getStoredToken())));
    };
    window.addEventListener('voltflow_unauthorized', handleUnauthorized);
    window.addEventListener('popstate', handlePopState);
    return () => {
      window.removeEventListener('voltflow_unauthorized', handleUnauthorized);
      window.removeEventListener('popstate', handlePopState);
    };
  }, [navigate]);

  const handleLogout = () => {
    setStoredToken(null);
    setAuthenticated(false);
    navigate('LOGIN');
  };

  const handleLoginSuccess = () => {
    setAuthenticated(true);
    navigate('DASHBOARD', true);
  };

  if (authenticated && route === 'DASHBOARD') {
    return (
      <ToastProvider>
        <Dashboard onLogout={handleLogout} />
      </ToastProvider>
    );
  }

  if (route === 'LANDING') {
    return (
      <LandingPage
        onLogin={() => navigate(authenticated ? 'DASHBOARD' : 'LOGIN')}
        onRegister={() => navigate(authenticated ? 'DASHBOARD' : 'REGISTER')}
      />
    );
  }

  const authMode: AuthMode = route === 'REGISTER' ? 'SIGNUP' : 'LOGIN';
  return (
    <LoginPage
      initialMode={authMode}
      onBack={() => navigate('LANDING')}
      onLoginSuccess={handleLoginSuccess}
      onModeChange={(mode) =>
        navigate(mode === 'SIGNUP' ? 'REGISTER' : 'LOGIN')
      }
    />
  );
}
