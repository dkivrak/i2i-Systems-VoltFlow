import React, { useState } from 'react';
import { Dialog } from './Dialog';
import { User, ShieldCheck, KeyRound, Zap, Activity, CheckCircle2, AlertCircle, Lock } from 'lucide-react';

interface ProfileModalProps {
  onClose: () => void;
  userEmail: string;
}

export function ProfileModal({ onClose, userEmail }: ProfileModalProps) {
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  let activeEmail = userEmail;
  if (!activeEmail || activeEmail === 'onur@gmail.com') {
    activeEmail = localStorage.getItem('voltflow_user_email') || '';
  }
  if (!activeEmail) {
    try {
      const token = localStorage.getItem('voltflow_jwt_token');
      if (token) {
        const payload = JSON.parse(atob(token.split('.')[1]));
        if (payload && payload.sub) activeEmail = payload.sub;
      }
    } catch {
      // silent
    }
  }
  if (!activeEmail) activeEmail = 'voltflow@gmail.com';

  const handleChangePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setMessage(null);

    if (newPassword !== confirmPassword) {
      setMessage({ type: 'error', text: 'Yeni şifreler birbiriyle eşleşmiyor.' });
      return;
    }

    if (newPassword.length < 8) {
      setMessage({ type: 'error', text: 'Yeni şifre en az 8 karakter olmalıdır.' });
      return;
    }

    setLoading(true);

    try {
      const token = localStorage.getItem('voltflow_jwt_token');
      const res = await fetch('/api/v1/auth/change-password', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify({
          currentPassword,
          newPassword,
        }),
      });

      if (res.ok) {
        setMessage({ type: 'success', text: 'Şifreniz başarıyla güncellendi!' });
        setCurrentPassword('');
        setNewPassword('');
        setConfirmPassword('');
      } else {
        const data = await res.json().catch(() => ({}));
        setMessage({
          type: 'error',
          text: data.message || 'Mevcut şifreniz hatalı veya işlem gerçekleştirilemedi.',
        });
      }
    } catch {
      setMessage({ type: 'error', text: 'Ağ bağlantısı hatası oluştu. Lütfen tekrar deneyin.' });
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog
      title="Profil ve Güvenlik Ayarları"
      description="Hesap bilgilerinizi görüntüleyin ve şifrenizi güvenle güncelleyin."
      onClose={onClose}
    >
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: '1.4rem',
          padding: '1.4rem 1.5rem 1.6rem',
          boxSizing: 'border-box',
          width: '100%',
          maxWidth: '100%',
          backgroundColor: 'var(--color-canvas, #fafafa)',
        }}
      >
        {/* User Banner Card */}
        <div
          style={{
            position: 'relative',
            padding: '1.25rem 1.4rem',
            border: '2.5px solid var(--color-ink)',
            borderRadius: 'var(--radius-lg)',
            background: 'linear-gradient(135deg, rgba(99, 102, 241, 0.09) 0%, var(--color-surface) 100%)',
            boxShadow: '4px 4px 0px var(--color-ink)',
            display: 'flex',
            flexWrap: 'wrap',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: '1rem',
            boxSizing: 'border-box',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', minWidth: 0, flex: '1 1 240px' }}>
            <div
              style={{
                width: '54px',
                height: '54px',
                borderRadius: '50%',
                backgroundColor: 'var(--color-brand, #6366f1)',
                color: '#ffffff',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                border: '2.5px solid var(--color-ink, #000)',
                boxShadow: '2px 2px 0px var(--color-ink, #000)',
                flexShrink: 0,
              }}
            >
              <User size={26} strokeWidth={2.4} />
            </div>
            <div style={{ minWidth: 0, flex: 1 }}>
              <div
                style={{
                  fontSize: '0.68rem',
                  fontWeight: 800,
                  color: 'var(--color-brand, #6366f1)',
                  letterSpacing: '0.08em',
                  textTransform: 'uppercase',
                  marginBottom: '2px',
                }}
              >
                Aktif Oturum
              </div>
              <div
                style={{
                  fontSize: '1.1rem',
                  fontWeight: 900,
                  color: 'var(--color-ink)',
                  lineHeight: '1.25',
                  wordBreak: 'break-all',
                  overflowWrap: 'anywhere',
                }}
              >
                {activeEmail}
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '6px', flexWrap: 'wrap' }}>
                <span
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: '4px',
                    fontSize: '0.72rem',
                    fontWeight: 750,
                    color: '#15803d',
                    backgroundColor: 'rgba(34, 197, 94, 0.14)',
                    padding: '3px 9px',
                    borderRadius: 'var(--radius-pill)',
                    border: '1.5px solid var(--color-ink)',
                  }}
                >
                  <ShieldCheck size={13} strokeWidth={2.4} /> Doğrulanmış Yönetici
                </span>
              </div>
            </div>
          </div>

          <div
            style={{
              display: 'flex',
              flexDirection: 'row',
              gap: '10px',
              alignItems: 'center',
              flexWrap: 'wrap',
              backgroundColor: 'var(--color-surface)',
              padding: '6px 12px',
              borderRadius: 'var(--radius-pill)',
              border: '1.5px solid var(--color-ink)',
              boxShadow: '2px 2px 0px var(--color-ink)',
            }}
          >
            <div style={{ fontSize: '0.72rem', fontWeight: 800, color: 'var(--color-ink)', display: 'flex', alignItems: 'center', gap: '4px' }}>
              <Zap size={13} color="var(--color-brand)" strokeWidth={2.5} /> VoltFlow
            </div>
            <div style={{ fontSize: '0.72rem', fontWeight: 800, color: '#16a34a', display: 'flex', alignItems: 'center', gap: '4px' }}>
              <Activity size={13} strokeWidth={2.5} /> Aktif
            </div>
          </div>
        </div>

        {/* Change Password Form Card */}
        <div
          style={{
            border: '2.5px solid var(--color-ink)',
            borderRadius: 'var(--radius-lg)',
            padding: '1.25rem',
            background: 'var(--color-surface)',
            boxShadow: '4px 4px 0px var(--color-ink)',
            boxSizing: 'border-box',
            width: '100%',
          }}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              marginBottom: '1rem',
              paddingBottom: '0.75rem',
              borderBottom: '2px solid var(--color-border)',
              flexWrap: 'wrap',
              gap: '6px',
            }}
          >
            <h4
              style={{
                margin: 0,
                fontSize: '1rem',
                fontWeight: 900,
                display: 'flex',
                alignItems: 'center',
                gap: '0.5rem',
                color: 'var(--color-ink)',
              }}
            >
              <KeyRound size={19} strokeWidth={2.4} color="var(--color-brand)" /> Şifre Güncelleme
            </h4>
            <span style={{ fontSize: '0.72rem', color: 'var(--color-ink-muted)', fontWeight: 650, display: 'flex', alignItems: 'center', gap: '3px' }}>
              <Lock size={12} /> Min. 8 Karakter
            </span>
          </div>

          {message && (
            <div
              style={{
                marginBottom: '1rem',
                padding: '0.75rem 1rem',
                borderRadius: 'var(--radius-md)',
                border: '2px solid var(--color-ink)',
                backgroundColor: message.type === 'success' ? 'rgba(34, 197, 94, 0.12)' : 'rgba(239, 68, 68, 0.12)',
                color: 'var(--color-ink)',
                fontSize: '0.85rem',
                fontWeight: 750,
                boxShadow: '2px 2px 0px var(--color-ink)',
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                boxSizing: 'border-box',
              }}
            >
              {message.type === 'success' ? (
                <CheckCircle2 size={18} color="#16a34a" strokeWidth={2.4} />
              ) : (
                <AlertCircle size={18} color="#dc2626" strokeWidth={2.4} />
              )}
              <span>{message.text}</span>
            </div>
          )}

          <form onSubmit={handleChangePassword} style={{ display: 'flex', flexDirection: 'column', gap: '1rem', width: '100%', boxSizing: 'border-box' }}>
            <div style={{ width: '100%', boxSizing: 'border-box' }}>
              <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 800, marginBottom: '0.4rem', color: 'var(--color-ink)' }}>
                Mevcut Şifre
              </label>
              <input
                type="password"
                required
                value={currentPassword}
                onChange={(e) => setCurrentPassword(e.target.value)}
                placeholder="••••••••"
                style={{
                  width: '100%',
                  boxSizing: 'border-box',
                  padding: '0.7rem 0.9rem',
                  borderRadius: 'var(--radius-md)',
                  border: '2px solid var(--color-ink)',
                  fontSize: '0.9rem',
                  backgroundColor: 'var(--color-canvas)',
                  fontWeight: 600,
                  outline: 'none',
                }}
              />
            </div>

            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
                gap: '0.9rem',
                width: '100%',
                boxSizing: 'border-box',
              }}
            >
              <div style={{ width: '100%', boxSizing: 'border-box' }}>
                <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 800, marginBottom: '0.4rem', color: 'var(--color-ink)' }}>
                  Yeni Şifre
                </label>
                <input
                  type="password"
                  required
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  placeholder="En az 8 karakter"
                  style={{
                    width: '100%',
                    boxSizing: 'border-box',
                    padding: '0.7rem 0.9rem',
                    borderRadius: 'var(--radius-md)',
                    border: '2px solid var(--color-ink)',
                    fontSize: '0.9rem',
                    backgroundColor: 'var(--color-canvas)',
                    fontWeight: 600,
                    outline: 'none',
                  }}
                />
              </div>

              <div style={{ width: '100%', boxSizing: 'border-box' }}>
                <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 800, marginBottom: '0.4rem', color: 'var(--color-ink)' }}>
                  Yeni Şifre (Tekrar)
                </label>
                <input
                  type="password"
                  required
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  placeholder="Şifreyi onaylayın"
                  style={{
                    width: '100%',
                    boxSizing: 'border-box',
                    padding: '0.7rem 0.9rem',
                    borderRadius: 'var(--radius-md)',
                    border: '2px solid var(--color-ink)',
                    fontSize: '0.9rem',
                    backgroundColor: 'var(--color-canvas)',
                    fontWeight: 600,
                    outline: 'none',
                  }}
                />
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="button button--primary"
              style={{
                marginTop: '0.4rem',
                justifyContent: 'center',
                width: '100%',
                padding: '0.8rem',
                fontSize: '0.95rem',
                boxSizing: 'border-box',
                cursor: loading ? 'not-allowed' : 'pointer',
              }}
            >
              {loading ? 'Şifre Güncelleniyor...' : 'Şifreyi Güncelle'}
            </button>
          </form>
        </div>
      </div>
    </Dialog>
  );
}
