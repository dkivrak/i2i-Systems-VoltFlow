import { HousePlug, LogOut, Mail, Plus, Radio, Zap } from 'lucide-react';

interface HeaderProps {
  isRefreshing: boolean;
  hasConnectionError: boolean;
  staleHomeCount: number;
  homeCount: number;
  onRegister: () => void;
  onLogout: () => void;
  onOpenMailtrap?: () => void;
}

export function Header({
  isRefreshing,
  hasConnectionError,
  staleHomeCount,
  homeCount,
  onRegister,
  onLogout,
  onOpenMailtrap,
}: HeaderProps) {
  const liveLabel = hasConnectionError
    ? 'Bağlantı yeniden kuruluyor'
    : staleHomeCount > 0
      ? `${staleHomeCount} evde telemetri gecikiyor`
    : homeCount > 0
      ? `${homeCount} ev canlı`
      : 'Ev verisi bekleniyor';

  return (
    <header className="app-header">
      <div className="app-header__inner">
        <a className="brand" href="#main-content" aria-label="VoltFlow ana içeriğe git">
          <span className="brand__mark" aria-hidden="true">
            <Zap size={21} strokeWidth={2.6} />
          </span>
          <span className="brand__wordmark">
            Volt<span>Flow</span>
          </span>
        </a>

        <div className="app-header__context" aria-hidden="true">
          <HousePlug size={16} />
          <span>Canlı enerji merkezi</span>
        </div>

        <nav className="app-header__actions" aria-label="Gösterge paneli işlemleri">
          <div
            className={`live-indicator${
              hasConnectionError ? ' is-disconnected' : ''
            }${staleHomeCount > 0 ? ' is-stale' : ''}`}
          >
            <span className="live-indicator__signal" aria-hidden="true">
              <Radio size={14} />
              <span className="live-indicator__dot" />
            </span>
            <span>{liveLabel}</span>
          </div>
          {onOpenMailtrap && (
            <button className="button button--secondary header-mail-button" type="button" onClick={onOpenMailtrap}>
              <Mail aria-hidden="true" size={17} />
              <span>📧 Test E-postalarını Görüntüle</span>
            </button>
          )}
          <button className="button button--primary header-add-button" type="button" onClick={onRegister}>
            <Plus aria-hidden="true" size={18} />
            <span>Yeni ev ekle</span>
          </button>
          <button className="button button--secondary header-logout-button" type="button" onClick={onLogout}>
            <LogOut aria-hidden="true" size={17} />
            <span>Çıkış</span>
          </button>
        </nav>
      </div>
    </header>
  );
}
