import React, { useState, useRef } from 'react';
import { api, getUserFacingError } from '../api/client';
import {
  Mail,
  ArrowRight,
  CheckCircle2,
  ShieldCheck,
  Zap,
  Activity,
  Sparkles,
  TrendingUp,
  Lock,
  RefreshCw,
  Cpu,
  Flame,
  Shield,
} from 'lucide-react';

interface LoginPageProps {
  onLoginSuccess: (email: string) => void;
}

export const LoginPage: React.FC<LoginPageProps> = ({ onLoginSuccess }) => {
  const [activeTab, setActiveTab] = useState<'LOGIN' | 'SIGNUP'>('LOGIN');
  const [step, setStep] = useState<'EMAIL' | 'OTP'>('EMAIL');
  const [email, setEmail] = useState('');
  const [code, setCode] = useState(['', '', '', '', '', '']);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [activeFeature, setActiveFeature] = useState<number>(0);

  const otpInputsRef = useRef<(HTMLInputElement | null)[]>([]);

  const handleOtpBoxChange = (index: number, val: string) => {
    const digit = val.replace(/\D/g, '').slice(-1);
    const newCode = [...code];
    newCode[index] = digit;
    setCode(newCode);

    if (digit && index < 5) {
      otpInputsRef.current[index + 1]?.focus();
    }
  };

  const handleOtpKeyDown = (index: number, e: React.KeyboardEvent) => {
    if (e.key === 'Backspace' && !code[index] && index > 0) {
      otpInputsRef.current[index - 1]?.focus();
    }
  };

  const handleOtpPaste = (e: React.ClipboardEvent) => {
    e.preventDefault();
    const pasted = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, 6);
    if (pasted.length > 0) {
      const newCode = ['', '', '', '', '', ''];
      for (let i = 0; i < pasted.length; i++) {
        newCode[i] = pasted[i];
      }
      setCode(newCode);
      const nextFocusIndex = Math.min(pasted.length, 5);
      otpInputsRef.current[nextFocusIndex]?.focus();
    }
  };

  const handleSendOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email.trim() || !email.includes('@')) {
      setError('Lütfen geçerli bir e-posta adresi giriniz.');
      return;
    }

    setLoading(true);
    setError(null);
    setSuccessMsg(null);

    try {
      const res = await api.sendOtp(email);
      setSuccessMsg(res.message || '6 haneli doğrulama kodunuz e-posta adresinize gönderildi.');
      setStep('OTP');
    } catch (err) {
      setError(getUserFacingError(err));
    } finally {
      setLoading(false);
    }
  };

  const handleVerifyOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    const fullCode = code.join('');
    if (fullCode.length !== 6) {
      setError('Lütfen 6 haneli doğrulama kodunu eksiksiz giriniz.');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const res = await api.verifyOtp(email, fullCode);
      onLoginSuccess(res.email);
    } catch (err) {
      setError(getUserFacingError(err));
    } finally {
      setLoading(false);
    }
  };

  const handlePresetEmail = (preset: string) => {
    setEmail(preset);
    setError(null);
  };

  const handleTemporaryLogin = () => {
    setError(null);
    setSuccessMsg(null);
    onLoginSuccess('temporary@voltflow.local');
  };

  return (
    <div style={{
      minHeight: '100vh',
      width: '100%',
      backgroundColor: '#05110e',
      backgroundImage: 'radial-gradient(circle at 50% 20%, rgba(67, 230, 164, 0.12), transparent 40rem), radial-gradient(circle at 80% 80%, rgba(85, 198, 236, 0.08), transparent 35rem)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      padding: '24px 16px',
      boxSizing: 'border-box',
      position: 'relative',
      overflow: 'hidden'
    }}>
      {/* Container Card */}
      <div style={{
        width: '100%',
        maxWidth: '1020px',
        backgroundColor: 'rgba(13, 27, 23, 0.92)',
        border: '1px solid #20372f',
        borderRadius: '24px',
        boxShadow: '0 30px 90px rgba(0, 0, 0, 0.45)',
        backdropFilter: 'blur(16px)',
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))',
        overflow: 'hidden',
        position: 'relative',
        zIndex: 10
      }}>
        
        {/* Left Side: SaaS Hero Feature Showcase */}
        <div style={{
          padding: '40px 36px',
          background: 'linear-gradient(160deg, rgba(7, 20, 17, 0.95), rgba(12, 28, 23, 0.98))',
          borderRight: '1px solid #20372f',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'space-between',
          gap: '24px'
        }}>
          {/* Brand Header */}
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '24px' }}>
              <div style={{
                width: '42px',
                height: '42px',
                borderRadius: '12px',
                background: 'linear-gradient(145deg, #7af0bd, #39d898)',
                display: 'grid',
                placeItems: 'center',
                boxShadow: '0 8px 24px rgba(67, 230, 164, 0.25)',
                color: '#062018'
              }}>
                <Zap size={24} fill="#062018" />
              </div>
              <div>
                <span style={{ fontSize: '1.4rem', fontWeight: 800, color: '#edf8f3', letterSpacing: '-0.03em' }}>
                  Volt<span style={{ color: '#43e6a4' }}>Flow</span>
                </span>
                <span style={{ display: 'block', fontSize: '0.65rem', fontWeight: 700, color: '#43e6a4', letterSpacing: '0.12em', textTransform: 'uppercase' }}>
                  Enterprise Energy Intelligence
                </span>
              </div>
            </div>

            <h2 style={{ fontSize: '1.75rem', fontWeight: 800, color: '#ffffff', lineHeight: 1.25, margin: 0, letterSpacing: '-0.03em' }}>
              Akıllı Eviniz İçin <br />
              <span style={{ background: 'linear-gradient(90deg, #43e6a4, #55c6ec)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
                Yüksek Hassasiyetli Enerji Zekâsı
              </span>
            </h2>
            <p style={{ color: '#a5b6b0', fontSize: '0.85rem', marginTop: '12px', lineHeight: 1.6 }}>
              Cihazlarınızı 1,5 saniyelik canlı telemetri akışıyla takip edin, bütçe aşımlarını kademeli ceza tarifesiyle anında tespit edin.
            </p>
          </div>

          {/* Interactive Feature Widget Showcase */}
          <div style={{
            backgroundColor: '#071411',
            border: '1px solid #20372f',
            borderRadius: '16px',
            padding: '18px',
            display: 'flex',
            flexDirection: 'column',
            gap: '12px'
          }}>
            {activeFeature === 0 && (
              <div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                  <span style={{ color: '#43e6a4', fontSize: '0.78rem', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '6px' }}>
                    <Activity size={14} /> Canlı Şebeke Yükü
                  </span>
                  <span style={{ backgroundColor: 'rgba(67, 230, 164, 0.12)', color: '#43e6a4', padding: '2px 8px', borderRadius: '100px', fontSize: '0.68rem', fontFamily: 'monospace' }}>
                    1.5s Döngü
                  </span>
                </div>
                <div style={{ fontSize: '1.6rem', fontWeight: 800, color: '#ffffff', fontFamily: 'monospace' }}>
                  4,180 <span style={{ fontSize: '0.85rem', color: '#70847d', fontWeight: 400 }}>Watt</span>
                </div>
                <div style={{ width: '100%', height: '6px', backgroundColor: '#182e27', borderRadius: '100px', marginTop: '10px', overflow: 'hidden' }}>
                  <div style={{ width: '68%', height: '100%', background: 'linear-gradient(90deg, #43e6a4, #55c6ec)', borderRadius: '100px' }} />
                </div>
              </div>
            )}

            {activeFeature === 1 && (
              <div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                  <span style={{ color: '#55c6ec', fontSize: '0.78rem', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '6px' }}>
                    <TrendingUp size={14} /> Aşamalı Ceza Tarifesi
                  </span>
                  <span style={{ backgroundColor: 'rgba(85, 198, 236, 0.12)', color: '#55c6ec', padding: '2px 8px', borderRadius: '100px', fontSize: '0.68rem', fontFamily: 'monospace' }}>
                    3 Kademe
                  </span>
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '8px', textAlign: 'center', fontSize: '0.7rem' }}>
                  <div style={{ backgroundColor: '#0c1d18', border: '1px solid #20372f', borderRadius: '10px', padding: '8px' }}>
                    <span style={{ color: '#70847d', display: 'block' }}>Zone 1</span>
                    <strong style={{ color: '#43e6a4' }}>Standart</strong>
                  </div>
                  <div style={{ backgroundColor: '#0c1d18', border: '1px solid #20372f', borderRadius: '10px', padding: '8px' }}>
                    <span style={{ color: '#70847d', display: 'block' }}>Zone 2</span>
                    <strong style={{ color: '#f7bd63' }}>+50% Ceza</strong>
                  </div>
                  <div style={{ backgroundColor: '#0c1d18', border: '1px solid #20372f', borderRadius: '10px', padding: '8px' }}>
                    <span style={{ color: '#70847d', display: 'block' }}>Zone 3</span>
                    <strong style={{ color: '#ff7d72' }}>+100% Ceza</strong>
                  </div>
                </div>
              </div>
            )}

            {activeFeature === 2 && (
              <div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                  <span style={{ color: '#a78bfa', fontSize: '0.78rem', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '6px' }}>
                    <Shield size={14} /> Şifresiz OTP Güvenliği
                  </span>
                  <span style={{ backgroundColor: 'rgba(167, 139, 250, 0.12)', color: '#a78bfa', padding: '2px 8px', borderRadius: '100px', fontSize: '0.68rem', fontFamily: 'monospace' }}>
                    SendGrid
                  </span>
                </div>
                <p style={{ color: '#a5b6b0', fontSize: '0.78rem', margin: 0, lineHeight: 1.5 }}>
                  Her giriş işlemi için SendGrid SMTP üzerinden 256-bit şifreli 6 haneli doğrulama kodu e-postanıza iletilir.
                </p>
              </div>
            )}
          </div>

          {/* Feature List Selectors */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            <button
              type="button"
              onClick={() => setActiveFeature(0)}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '12px',
                padding: '12px 14px',
                borderRadius: '14px',
                border: activeFeature === 0 ? '1px solid #43e6a4' : '1px solid #20372f',
                backgroundColor: activeFeature === 0 ? 'rgba(67, 230, 164, 0.08)' : 'rgba(16, 32, 27, 0.5)',
                color: '#edf8f3',
                textAlign: 'left',
                cursor: 'pointer',
                transition: 'all 180ms ease'
              }}
            >
              <Cpu size={18} color="#43e6a4" />
              <div>
                <strong style={{ fontSize: '0.8rem', display: 'block' }}>1,5s Canlı Telemetri Akışı</strong>
                <span style={{ fontSize: '0.7rem', color: '#70847d' }}>Anlık Watt tüketimi ve birikimli maliyet takibi.</span>
              </div>
            </button>

            <button
              type="button"
              onClick={() => setActiveFeature(1)}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '12px',
                padding: '12px 14px',
                borderRadius: '14px',
                border: activeFeature === 1 ? '1px solid #55c6ec' : '1px solid #20372f',
                backgroundColor: activeFeature === 1 ? 'rgba(85, 198, 236, 0.08)' : 'rgba(16, 32, 27, 0.5)',
                color: '#edf8f3',
                textAlign: 'left',
                cursor: 'pointer',
                transition: 'all 180ms ease'
              }}
            >
              <Flame size={18} color="#55c6ec" />
              <div>
                <strong style={{ fontSize: '0.8rem', display: 'block' }}>3 Kademeli Aşamalı Ceza Tarifesi</strong>
                <span style={{ fontSize: '0.7rem', color: '#70847d' }}>Bütçe aşımında otomatik kademeli tarife uyarısı.</span>
              </div>
            </button>

            <button
              type="button"
              onClick={() => setActiveFeature(2)}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '12px',
                padding: '12px 14px',
                borderRadius: '14px',
                border: activeFeature === 2 ? '1px solid #a78bfa' : '1px solid #20372f',
                backgroundColor: activeFeature === 2 ? 'rgba(167, 139, 250, 0.08)' : 'rgba(16, 32, 27, 0.5)',
                color: '#edf8f3',
                textAlign: 'left',
                cursor: 'pointer',
                transition: 'all 180ms ease'
              }}
            >
              <Sparkles size={18} color="#a78bfa" />
              <div>
                <strong style={{ fontSize: '0.8rem', display: 'block' }}>Şifresiz E-posta OTP & JWT</strong>
                <span style={{ fontSize: '0.7rem', color: '#70847d' }}>SendGrid SMTP ile 6 haneli e-posta kodu.</span>
              </div>
            </button>
          </div>

          {/* Footer Badge */}
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', fontSize: '0.72rem', color: '#70847d', borderTop: '1px solid #20372f', paddingTop: '16px' }}>
            <span style={{ display: 'flex', alignItems: 'center', gap: '6px', color: '#43e6a4', fontWeight: 600 }}>
              <span style={{ width: '8px', height: '8px', borderRadius: '50%', backgroundColor: '#43e6a4', display: 'inline-block' }} />
              SendGrid SMTP Aktif
            </span>
            <span>VoltFlow v1.0.0</span>
          </div>
        </div>

        {/* Right Side: Authentication Form */}
        <div style={{
          padding: '40px 36px',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'space-between',
          gap: '24px',
          backgroundColor: 'rgba(13, 27, 23, 0.95)'
        }}>
          <div>
            {/* Header Tab Switcher */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '28px', borderBottom: '1px solid #20372f', paddingBottom: '16px' }}>
              <div style={{ display: 'flex', gap: '6px', backgroundColor: '#071411', padding: '4px', borderRadius: '12px', border: '1px solid #20372f' }}>
                <button
                  type="button"
                  onClick={() => { setActiveTab('LOGIN'); setError(null); }}
                  style={{
                    padding: '6px 16px',
                    borderRadius: '8px',
                    fontSize: '0.78rem',
                    fontWeight: 700,
                    border: 0,
                    cursor: 'pointer',
                    backgroundColor: activeTab === 'LOGIN' ? 'linear-gradient(135deg, #5eeaad, #37d798)' : 'transparent',
                    background: activeTab === 'LOGIN' ? 'linear-gradient(135deg, #5eeaad, #37d798)' : 'transparent',
                    color: activeTab === 'LOGIN' ? '#062018' : '#a5b6b0',
                    transition: 'all 160ms ease'
                  }}
                >
                  Giriş Yap
                </button>
                <button
                  type="button"
                  onClick={() => { setActiveTab('SIGNUP'); setError(null); }}
                  style={{
                    padding: '6px 16px',
                    borderRadius: '8px',
                    fontSize: '0.78rem',
                    fontWeight: 700,
                    border: 0,
                    cursor: 'pointer',
                    background: activeTab === 'SIGNUP' ? 'linear-gradient(135deg, #5eeaad, #37d798)' : 'transparent',
                    color: activeTab === 'SIGNUP' ? '#062018' : '#a5b6b0',
                    transition: 'all 160ms ease'
                  }}
                >
                  Kaydol
                </button>
              </div>

              <span style={{ fontSize: '0.72rem', color: '#70847d', display: 'flex', alignItems: 'center', gap: '4px', backgroundColor: '#071411', padding: '4px 10px', borderRadius: '10px', border: '1px solid #20372f' }}>
                <Lock size={12} color="#43e6a4" /> 256-bit SSL
              </span>
            </div>

            {/* Title */}
            <div style={{ marginBottom: '24px' }}>
              <h3 style={{ fontSize: '1.35rem', fontWeight: 800, color: '#ffffff', margin: 0, letterSpacing: '-0.025em' }}>
                {activeTab === 'LOGIN' ? 'Hesabınıza Giriş Yapın' : 'Yeni VoltFlow Hesabı Oluşturun'}
              </h3>
              <p style={{ fontSize: '0.8rem', color: '#a5b6b0', marginTop: '6px', margin: 0 }}>
                {activeTab === 'LOGIN'
                  ? 'E-posta adresinize gönderilen tek kullanımlık 6 haneli kod ile bağlanın.'
                  : 'E-posta adresinizi girerek anında akıllı ev portföyünüzü oluşturun.'}
              </p>
            </div>

            {/* Error Message */}
            {error && (
              <div style={{
                padding: '12px 14px',
                backgroundColor: 'rgba(255, 125, 114, 0.12)',
                border: '1px solid rgba(255, 125, 114, 0.4)',
                borderRadius: '12px',
                color: '#ffc1bb',
                fontSize: '0.78rem',
                marginBottom: '20px',
                display: 'flex',
                alignItems: 'center',
                gap: '8px'
              }}>
                <span style={{ width: '8px', height: '8px', borderRadius: '50%', backgroundColor: '#ff7d72', display: 'inline-block', flexShrink: 0 }} />
                <span>{error}</span>
              </div>
            )}

            {/* Success Message */}
            {successMsg && (
              <div style={{
                padding: '12px 14px',
                backgroundColor: 'rgba(67, 230, 164, 0.12)',
                border: '1px solid rgba(67, 230, 164, 0.4)',
                borderRadius: '12px',
                color: '#a4f4d2',
                fontSize: '0.78rem',
                marginBottom: '20px',
                display: 'flex',
                alignItems: 'center',
                gap: '8px'
              }}>
                <CheckCircle2 size={16} color="#43e6a4" />
                <span>{successMsg}</span>
              </div>
            )}

            {/* Step 1: EMAIL */}
            {step === 'EMAIL' ? (
              <form onSubmit={handleSendOtp} style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
                <div>
                  <label style={{ display: 'block', fontSize: '0.78rem', fontWeight: 700, color: '#edf8f3', marginBottom: '8px' }}>
                    E-posta Adresi
                  </label>
                  <div style={{ position: 'relative' }}>
                    <Mail style={{ position: 'absolute', left: '14px', top: '50%', transform: 'translateY(-50%)', color: '#70847d' }} size={18} />
                    <input
                      type="email"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      placeholder="ornek@voltflow.com"
                      required
                      style={{
                        width: '100%',
                        height: '46px',
                        backgroundColor: '#061713',
                        border: '1px solid #20372f',
                        borderRadius: '12px',
                        paddingLeft: '44px',
                        paddingRight: '16px',
                        color: '#edf8f3',
                        fontSize: '0.88rem',
                        outline: 'none',
                        boxSizing: 'border-box',
                        transition: 'border-color 160ms ease'
                      }}
                    />
                  </div>
                </div>

                <button
                  type="submit"
                  disabled={loading}
                  style={{
                    height: '46px',
                    borderRadius: '12px',
                    border: 0,
                    background: 'linear-gradient(135deg, #5eeaad, #37d798)',
                    color: '#062018',
                    fontSize: '0.88rem',
                    fontWeight: 800,
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: '8px',
                    boxShadow: '0 8px 24px rgba(67, 230, 164, 0.2)',
                    transition: 'all 160ms ease'
                  }}
                >
                  {loading ? (
                    <span>Gönderiliyor...</span>
                  ) : (
                    <>
                      <span>Doğrulama Kodu Gönder</span>
                      <ArrowRight size={18} />
                    </>
                  )}
                </button>

                <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }} aria-hidden="true">
                  <span style={{ height: '1px', flex: 1, backgroundColor: '#20372f' }} />
                  <span style={{ color: '#70847d', fontSize: '0.7rem', fontWeight: 600 }}>veya</span>
                  <span style={{ height: '1px', flex: 1, backgroundColor: '#20372f' }} />
                </div>

                <button
                  type="button"
                  onClick={handleTemporaryLogin}
                  disabled={loading}
                  style={{
                    height: '44px',
                    borderRadius: '12px',
                    border: '1px solid rgba(85, 198, 236, 0.45)',
                    backgroundColor: 'rgba(85, 198, 236, 0.08)',
                    color: '#8edcf4',
                    fontSize: '0.84rem',
                    fontWeight: 800,
                    cursor: loading ? 'not-allowed' : 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: '8px',
                    opacity: loading ? 0.6 : 1,
                    transition: 'all 160ms ease'
                  }}
                >
                  <Zap size={17} />
                  <span>Geçici Giriş</span>
                </button>

                <p style={{ color: '#70847d', fontSize: '0.68rem', lineHeight: 1.45, margin: '-10px 0 0', textAlign: 'center' }}>
                  E-posta göndermeden bu tarayıcı oturumu boyunca devam eder.
                </p>

                {/* Quick Presets */}
                <div style={{ borderTop: '1px solid #20372f', paddingTop: '16px', display: 'flex', flexDirection: 'column', gap: '8px' }}>
                  <span style={{ fontSize: '0.72rem', color: '#70847d', fontWeight: 600 }}>
                    Hızlı Test Adresleri:
                  </span>
                  <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                    <button
                      type="button"
                      onClick={() => handlePresetEmail('demo@voltflow.com')}
                      style={{
                        padding: '6px 12px',
                        borderRadius: '8px',
                        backgroundColor: '#0f241e',
                        border: '1px solid #20372f',
                        color: '#43e6a4',
                        fontSize: '0.75rem',
                        cursor: 'pointer'
                      }}
                    >
                      demo@voltflow.com
                    </button>
                    <button
                      type="button"
                      onClick={() => handlePresetEmail('test.user@voltflow.com')}
                      style={{
                        padding: '6px 12px',
                        borderRadius: '8px',
                        backgroundColor: '#0f241e',
                        border: '1px solid #20372f',
                        color: '#43e6a4',
                        fontSize: '0.75rem',
                        cursor: 'pointer'
                      }}
                    >
                      test.user@voltflow.com
                    </button>
                  </div>
                </div>
              </form>
            ) : (
              /* Step 2: OTP */
              <form onSubmit={handleVerifyOtp} style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
                <div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px' }}>
                    <label style={{ fontSize: '0.78rem', fontWeight: 700, color: '#edf8f3' }}>
                      6 Haneli Kodu Giriniz
                    </label>
                    <button
                      type="button"
                      onClick={() => setStep('EMAIL')}
                      style={{ background: 'none', border: 0, color: '#43e6a4', fontSize: '0.72rem', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '4px' }}
                    >
                      <RefreshCw size={12} /> E-posta Değiştir
                    </button>
                  </div>

                  {/* 6 Glowing OTP Digit Boxes */}
                  <div style={{ display: 'flex', gap: '8px', justifyContent: 'center', margin: '14px 0' }} onPaste={handleOtpPaste}>
                    {code.map((digit, i) => (
                      <input
                        key={i}
                        ref={(el) => (otpInputsRef.current[i] = el)}
                        type="text"
                        inputMode="numeric"
                        maxLength={1}
                        value={digit}
                        onChange={(e) => handleOtpBoxChange(i, e.target.value)}
                        onKeyDown={(e) => handleOtpKeyDown(i, e)}
                        style={{
                          width: '42px',
                          height: '52px',
                          textAlign: 'center',
                          fontSize: '1.25rem',
                          fontWeight: 800,
                          fontFamily: 'monospace',
                          borderRadius: '12px',
                          border: digit ? '2px solid #43e6a4' : '1px solid #20372f',
                          backgroundColor: digit ? 'rgba(67, 230, 164, 0.12)' : '#061713',
                          color: digit ? '#43e6a4' : '#edf8f3',
                          outline: 'none',
                          boxShadow: digit ? '0 0 14px rgba(67, 230, 164, 0.2)' : 'none',
                          transition: 'all 160ms ease'
                        }}
                      />
                    ))}
                  </div>

                  <p style={{ fontSize: '0.72rem', color: '#a5b6b0', textAlign: 'center', margin: 0, lineHeight: 1.4 }}>
                    Kod <strong>{email}</strong> adresine iletilmiştir.<br />
                    <span style={{ fontSize: '0.68rem', color: '#70847d' }}>
                      (Yerel Mailpit test kutusu: <a href="http://localhost:8025" target="_blank" rel="noreferrer" style={{ color: '#43e6a4', textDecoration: 'underline' }}>http://localhost:8025</a>)
                    </span>
                  </p>
                </div>

                <button
                  type="submit"
                  disabled={loading || code.join('').length !== 6}
                  style={{
                    height: '46px',
                    borderRadius: '12px',
                    border: 0,
                    background: 'linear-gradient(135deg, #5eeaad, #37d798)',
                    color: '#062018',
                    fontSize: '0.88rem',
                    fontWeight: 800,
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: '8px',
                    opacity: (loading || code.join('').length !== 6) ? 0.6 : 1,
                    boxShadow: '0 8px 24px rgba(67, 230, 164, 0.2)',
                    transition: 'all 160ms ease'
                  }}
                >
                  {loading ? (
                    <span>Doğrulanıyor...</span>
                  ) : (
                    <>
                      <ShieldCheck size={18} />
                      <span>Doğrula ve Giriş Yap</span>
                    </>
                  )}
                </button>
              </form>
            )}
          </div>

          {/* Footer Security Note */}
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', fontSize: '0.72rem', color: '#70847d', borderTop: '1px solid #20372f', paddingTop: '16px' }}>
            <span style={{ display: 'flex', alignItems: 'center', gap: '6px', color: '#43e6a4' }}>
              <ShieldCheck size={14} /> VoltFlow OAuth / JWT Güvenlik Protokolü
            </span>
            <span>KVKK Uyumlu</span>
          </div>
        </div>

      </div>
    </div>
  );
};
