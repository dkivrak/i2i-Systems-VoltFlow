import React, { useState, useRef, useEffect } from 'react';
import { Bot, X, Send, Sparkles } from 'lucide-react';

interface Message {
  id: string;
  sender: 'user' | 'ai';
  text: string;
  time: string;
}

export function ChatWidget() {
  const [isOpen, setIsOpen] = useState(false);
  const [input, setInput] = useState('');
  const [messages, setMessages] = useState<Message[]>([
    {
      id: '1',
      sender: 'ai',
      text: 'Merhaba! Ben VoltFlow AI Asistanı ⚡ Evinizin canlı enerjisi, maliyeti ve cihaz durumları hakkında sorularınızı yanıtlayabilirim.',
      time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    },
  ]);
  const [loading, setLoading] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    if (isOpen) {
      scrollToBottom();
    }
  }, [messages, isOpen]);

  const handleSend = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!input.trim() || loading) return;

    const userText = input.trim();
    const nowStr = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

    const userMsg: Message = {
      id: Date.now().toString(),
      sender: 'user',
      text: userText,
      time: nowStr,
    };

    setMessages((prev) => [...prev, userMsg]);
    setInput('');
    setLoading(true);

    try {
      const token = localStorage.getItem('voltflow_jwt_token');
      const authHeaders: Record<string, string> = {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      };

      const res = await fetch('/api/v1/chat', {
        method: 'POST',
        headers: authHeaders,
        body: JSON.stringify({ message: userText }),
      });

      if (res.ok) {
        const data = await res.json();
        const aiReply = data.reply || 'Enerji asistanınız şu anda isteklerinizi analiz ediyor.';
        setMessages((prev) => [
          ...prev,
          {
            id: (Date.now() + 1).toString(),
            sender: 'ai',
            text: aiReply,
            time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
          },
        ]);
      } else {
        setMessages((prev) => [
          ...prev,
          {
            id: (Date.now() + 1).toString(),
            sender: 'ai',
            text: 'Enerji kullanımınız tanımlanan sınıra ulaşmış veya bir cihazda olağan dışı tüketim algılanmıştır.',
            time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
          },
        ]);
      }
    } catch {
      setMessages((prev) => [
        ...prev,
        {
          id: (Date.now() + 1).toString(),
          sender: 'ai',
          text: 'Üzgünüm, şu anda AI servisine erişilemiyor. Lütfen ağ bağlantınızı kontrol edin.',
          time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
        },
      ]);
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      {/* Floating Toggle Button */}
      <button
        type="button"
        className="chat-widget-toggle"
        onClick={() => setIsOpen(!isOpen)}
        aria-label="VoltFlow AI Asistanını Aç/Kapat"
        style={{
          position: 'fixed',
          bottom: '1.8rem',
          right: '1.8rem',
          width: '56px',
          height: '56px',
          borderRadius: '50%',
          backgroundColor: 'var(--color-brand, #6366f1)',
          color: '#ffffff',
          border: '2.5px solid var(--color-ink, #000)',
          boxShadow: '4px 4px 0px var(--color-ink, #000)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          cursor: 'pointer',
          zIndex: 9999,
          transition: 'transform 0.2s ease, box-shadow 0.2s ease',
        }}
      >
        {isOpen ? <X size={26} strokeWidth={2.5} /> : <Bot size={28} strokeWidth={2.3} />}
      </button>

      {/* Floating Chat Modal */}
      {isOpen && (
        <div
          className="chat-widget-window"
          style={{
            position: 'fixed',
            bottom: '5.8rem',
            right: '1.8rem',
            width: 'min(380px, calc(100vw - 2rem))',
            maxHeight: 'min(520px, calc(100vh - 7rem))',
            height: '470px',
            backgroundColor: 'var(--color-surface, #ffffff)',
            border: '2.5px solid var(--color-ink, #000)',
            borderRadius: 'var(--radius-lg, 16px)',
            boxShadow: '8px 8px 0px var(--color-ink, #000)',
            display: 'flex',
            flexDirection: 'column',
            zIndex: 9999,
            overflow: 'hidden',
            fontFamily: 'inherit',
            boxSizing: 'border-box',
          }}
        >
          {/* Header */}
          <div
            style={{
              padding: '0.85rem 1.1rem',
              backgroundColor: 'var(--color-brand-soft, #f0f0ff)',
              borderBottom: '2px solid var(--color-ink, #000)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              boxSizing: 'border-box',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.55rem' }}>
              <div
                style={{
                  width: '34px',
                  height: '34px',
                  borderRadius: '50%',
                  backgroundColor: 'var(--color-brand, #6366f1)',
                  color: '#fff',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  border: '1.5px solid var(--color-ink, #000)',
                }}
              >
                <Sparkles size={18} />
              </div>
              <div>
                <h4 style={{ margin: 0, fontSize: '0.95rem', fontWeight: 900, color: 'var(--color-ink, #000)' }}>
                  VoltFlow AI
                </h4>
                <span style={{ fontSize: '0.72rem', color: 'var(--color-ink-muted, #666)', fontWeight: 650 }}>
                  Canlı Enerji Danışmanı
                </span>
              </div>
            </div>
            <button
              type="button"
              onClick={() => setIsOpen(false)}
              style={{
                background: 'none',
                border: 'none',
                cursor: 'pointer',
                color: 'var(--color-ink, #000)',
                padding: '4px',
                display: 'flex',
                alignItems: 'center',
              }}
            >
              <X size={20} />
            </button>
          </div>

          {/* Messages Body */}
          <div
            style={{
              flex: 1,
              padding: '1rem',
              overflowY: 'auto',
              display: 'flex',
              flexDirection: 'column',
              gap: '0.75rem',
              backgroundColor: 'var(--color-canvas, #fafafa)',
              boxSizing: 'border-box',
            }}
          >
            {messages.map((msg) => (
              <div
                key={msg.id}
                style={{
                  alignSelf: msg.sender === 'user' ? 'flex-end' : 'flex-start',
                  maxWidth: '85%',
                  padding: '0.75rem 0.9rem',
                  borderRadius: 'var(--radius-md, 10px)',
                  backgroundColor:
                    msg.sender === 'user' ? 'var(--color-brand, #6366f1)' : 'var(--color-surface, #fff)',
                  color: msg.sender === 'user' ? '#ffffff' : 'var(--color-ink, #000)',
                  border: '1.5px solid var(--color-ink, #000)',
                  boxShadow: '2px 2px 0px var(--color-ink, #000)',
                  fontSize: '0.85rem',
                  lineHeight: '1.45',
                  fontWeight: 550,
                  boxSizing: 'border-box',
                  wordBreak: 'break-word',
                  overflowWrap: 'anywhere',
                }}
              >
                <div>{msg.text}</div>
                <div
                  style={{
                    fontSize: '0.65rem',
                    textAlign: 'right',
                    marginTop: '0.3rem',
                    opacity: 0.75,
                  }}
                >
                  {msg.time}
                </div>
              </div>
            ))}
            {loading && (
              <div
                style={{
                  alignSelf: 'flex-start',
                  padding: '0.6rem 0.9rem',
                  borderRadius: 'var(--radius-md, 10px)',
                  backgroundColor: 'var(--color-surface, #fff)',
                  border: '1.5px solid var(--color-ink, #000)',
                  boxShadow: '2px 2px 0px var(--color-ink, #000)',
                  fontSize: '0.8rem',
                  color: 'var(--color-ink-muted, #666)',
                  fontWeight: 600,
                }}
              >
                VoltFlow AI düşünüyor... ⚡
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>

          {/* Input Footer */}
          <form
            onSubmit={handleSend}
            style={{
              padding: '0.75rem 0.9rem',
              backgroundColor: 'var(--color-surface, #fff)',
              borderTop: '2px solid var(--color-ink, #000)',
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem',
              boxSizing: 'border-box',
            }}
          >
            <input
              type="text"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder="Enerji sorunu sor..."
              disabled={loading}
              style={{
                flex: 1,
                padding: '0.65rem 0.85rem',
                borderRadius: 'var(--radius-pill, 9999px)',
                border: '1.5px solid var(--color-ink, #000)',
                fontSize: '0.85rem',
                backgroundColor: 'var(--color-canvas, #fafafa)',
                fontWeight: 600,
                outline: 'none',
                boxSizing: 'border-box',
              }}
            />
            <button
              type="submit"
              disabled={loading || !input.trim()}
              style={{
                width: '38px',
                height: '38px',
                borderRadius: '50%',
                backgroundColor: input.trim() ? 'var(--color-brand, #6366f1)' : '#e5e7eb',
                color: input.trim() ? '#ffffff' : '#9ca3af',
                border: '1.5px solid var(--color-ink, #000)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                cursor: input.trim() && !loading ? 'pointer' : 'default',
                boxShadow: input.trim() ? '1.5px 1.5px 0px var(--color-ink, #000)' : 'none',
                flexShrink: 0,
              }}
            >
              <Send size={16} />
            </button>
          </form>
        </div>
      )}
    </>
  );
}
