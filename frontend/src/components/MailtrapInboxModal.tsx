import { Mail, RefreshCw, Calendar, User } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Dialog } from './Dialog';
import { InlineSpinner } from './PageStates';

interface MailtrapInboxModalProps {
  onClose: () => void;
}

interface MailtrapMessage {
  id: number;
  subject: string;
  to_email: string;
  to_name?: string;
  created_at: string;
  sent_at?: string;
}

interface MessageDetail {
  id: number;
  bodyText: string;
}

export function MailtrapInboxModal({ onClose }: MailtrapInboxModalProps) {
  const [messages, setMessages] = useState<MailtrapMessage[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedMessage, setSelectedMessage] = useState<MessageDetail | null>(null);
  const [loadingDetail, setLoadingDetail] = useState(false);

  const fetchMessages = async () => {
    setLoading(true);
    try {
      const token = localStorage.getItem('voltflow_jwt_token');
      const authHeaders: Record<string, string> = token
        ? { Authorization: `Bearer ${token}` }
        : {};

      const proxyRes = await fetch('/api/notifications/inbox', {
        headers: { Accept: 'application/json', ...authHeaders },
      });

      if (proxyRes.ok) {
        const data = (await proxyRes.json()) as MailtrapMessage[];
        let currentUserEmail = localStorage.getItem('voltflow_user_email');
        if (!currentUserEmail) {
          try {
            const token = localStorage.getItem('voltflow_jwt_token');
            if (token) {
              const payload = JSON.parse(atob(token.split('.')[1]));
              if (payload && payload.sub) currentUserEmail = payload.sub;
            }
          } catch {
            // silent
          }
        }
        const activeEmail = (currentUserEmail || 'voltflow@gmail.com').toLowerCase();
        const filtered = Array.isArray(data)
          ? data.filter(
              (m) =>
                !m.to_email ||
                m.to_email.toLowerCase() === activeEmail
            )
          : [];
        setMessages(filtered);
      }
      // Hata HTTP kodlarında sessizce devam et, setError yok.
    } catch {
      // Ağ hatası vb. — mevcut liste olduğu gibi kalır, kullanıcıya mesaj gösterilmez.
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void fetchMessages();
  }, []);

  const fetchMessageBody = async (msgId: number) => {
    setLoadingDetail(true);
    try {
      const token = localStorage.getItem('voltflow_jwt_token');
      const authHeaders: Record<string, string> = token
        ? { Authorization: `Bearer ${token}` }
        : {};

      const proxyRes = await fetch(`/api/notifications/inbox/${msgId}/body`, {
        headers: { ...authHeaders },
      });

      if (proxyRes.ok) {
        const text = await proxyRes.text();
        setSelectedMessage({ id: msgId, bodyText: text });
      } else {
        setSelectedMessage({ id: msgId, bodyText: 'E-posta içeriği okunamadı.' });
      }
    } catch {
      setSelectedMessage({
        id: msgId,
        bodyText: 'E-posta içeriği alınırken bir sorun oluştu.',
      });
    } finally {
      setLoadingDetail(false);
    }
  };

  return (
    <Dialog
      title="Gelen Kutusu"
      description="Hesabınıza gönderilen son bildirim ve uyarı e-postaları"
      onClose={onClose}
      wide
    >
      <div
        style={{
          display: 'flex',
          justifyContent: 'flex-end',
          alignItems: 'center',
          padding: '0.85rem 1.2rem 0.25rem',
        }}
      >
        <button
          className="button button--secondary button--small"
          type="button"
          onClick={() => void fetchMessages()}
          disabled={loading}
        >
          <RefreshCw size={14} className={loading ? 'spin' : ''} /> Yenile
        </button>
      </div>

      <div style={{ padding: '0.5rem 1.2rem 1.5rem' }}>
        {loading ? (
          <InlineSpinner label="E-postalar yükleniyor..." />
        ) : messages.length === 0 ? (
          <div
            style={{
              textAlign: 'center',
              padding: '2rem',
              color: 'var(--color-ink-muted)',
            }}
          >
            <Mail size={40} style={{ opacity: 0.4, marginBottom: '0.5rem' }} />
            <p>Gelen kutusunda henüz e-posta bulunmuyor.</p>
            <small>
              Bütçe aşımı (%80/%100) veya cihaz anomalisi gerçekleştiğinde
              e-postalar burada görünecektir.
            </small>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
            {messages.map((msg) => (
              <div
                key={msg.id}
                style={{
                  border: '1px solid var(--color-border)',
                  borderRadius: 'var(--radius-md)',
                  padding: '1rem',
                  background: 'var(--color-surface)',
                  boxShadow: '0 2px 4px rgb(0 0 0 / 4%)',
                  cursor: 'pointer',
                  transition: 'background 0.2s',
                }}
                onClick={() => void fetchMessageBody(msg.id)}
              >
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'flex-start',
                    gap: '1rem',
                  }}
                >
                  <strong style={{ fontSize: '0.95rem', color: 'var(--color-ink)' }}>
                    {msg.subject ? msg.subject.replace(/VoltWise/gi, 'VoltFlow') : ''}
                  </strong>
                  <span
                    style={{
                      fontSize: '0.75rem',
                      color: 'var(--color-ink-muted)',
                      whiteSpace: 'nowrap',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '0.25rem',
                    }}
                  >
                    <Calendar size={12} />
                    {new Date(msg.created_at || msg.sent_at || '').toLocaleString('tr-TR')}
                  </span>
                </div>

                <div
                  style={{
                    fontSize: '0.8rem',
                    color: 'var(--color-ink-soft)',
                    marginTop: '0.35rem',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '0.25rem',
                  }}
                >
                  <User size={12} /> Kime:{' '}
                  <strong>{msg.to_email}</strong>
                  {msg.to_name ? ` (${msg.to_name})` : ''}
                </div>

                {selectedMessage?.id === msg.id && (
                  <div
                    style={{
                      marginTop: '0.75rem',
                      paddingTop: '0.75rem',
                      borderTop: '1px dashed var(--color-border)',
                      background: '#fafafa',
                      padding: '0.75rem',
                      borderRadius: 'var(--radius-sm)',
                    }}
                  >
                    {loadingDetail ? (
                      <InlineSpinner label="E-posta içeriği yükleniyor..." />
                    ) : (
                      <pre
                        style={{
                          margin: 0,
                          whiteSpace: 'pre-wrap',
                          fontFamily: 'inherit',
                          fontSize: '0.82rem',
                          color: 'var(--color-ink)',
                        }}
                      >
                        {selectedMessage.bodyText}
                      </pre>
                    )}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </Dialog>
  );
}
