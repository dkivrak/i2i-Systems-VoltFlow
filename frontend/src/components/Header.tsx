import { HousePlug, LogOut, Mail, Plus, Radio, User, Zap } from 'lucide-react';

interface HeaderProps {
  isRefreshing: boolean;
  hasConnectionError: boolean;
  staleHomeCount: number;
  homeCount: number;
  onRegister: () => void;
  onLogout: () => void;
  onOpenMailtrap?: () => void;
  onOpenProfile?: () => void;
  mailCount?: number;
}

export function Header({
  isRefreshing,
  hasConnectionError,
  staleHomeCount,
  homeCount,
  onRegister,
  onLogout,
  onOpenMailtrap,
  onOpenProfile,
  mailCount = 0,
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
            <button
              className="button button--secondary header-mail-button"
              type="button"
              onClick={onOpenMailtrap}
              title="E-postalarımı Görüntüle"
              style={{ position: 'relative' }}
            >
              <Mail aria-hidden="true" size={17} />
              <span>E-postalarım</span>
              {mailCount > 0 && (
                <span
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    backgroundColor: 'var(--color-critical, #ef4444)',
                    color: '#ffffff',
                    fontSize: '0.72rem',
                    fontWeight: 900,
                    minWidth: '20px',
                    height: '20px',
                    padding: '0 6px',
                    borderRadius: 'var(--radius-pill, 9999px)',
                    border: '1.5px solid var(--color-ink, #000)',
                    boxShadow: '1.5px 1.5px 0px var(--color-ink, #000)',
                    marginLeft: '4px',
                  }}
                >
                  {mailCount}
                </span>
              )}
            </button>
          )}
          {onOpenProfile && (
            <button className="button button--secondary header-profile-button" type="button" onClick={onOpenProfile} title="Kullanıcı Profilim">
              <User aria-hidden="true" size={17} />
              <span>Profilim</span>
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
