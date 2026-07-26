import {
  ArrowLeft,
  ArrowRight,
  Check,
  Eye,
  EyeOff,
  LockKeyhole,
  Mail,
  ShieldCheck,
  Sparkles,
  UserPlus,
  Zap,
} from 'lucide-react';
import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type FormEvent,
} from 'react';
import { ApiError, api, ensureDemoAccount, getUserFacingError } from '../api/client';

import {
  ApplianceCharacter,
  CharacterGroup,
  type CharacterState,
} from '../characters';

export type AuthMode = 'LOGIN' | 'SIGNUP';
type FocusTarget = 'NONE' | 'EMAIL' | 'PASSWORD' | 'CONFIRMATION';
type AuthField = 'email' | 'password' | 'confirmation';
type FieldErrors = Partial<Record<AuthField, string>>;

interface LoginPageProps {
  onLoginSuccess: (email: string) => void;
  initialMode?: AuthMode;
  onBack?: () => void;
  onModeChange?: (mode: AuthMode) => void;
}

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function LoginPage({
  onLoginSuccess,
  initialMode = 'LOGIN',
  onBack,
  onModeChange,
}: LoginPageProps) {
  const [activeMode, setActiveMode] = useState<AuthMode>(initialMode);
  const [focusTarget, setFocusTarget] = useState<FocusTarget>('NONE');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [passwordVisible, setPasswordVisible] = useState(false);
  const [confirmationVisible, setConfirmationVisible] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [successMessage, setSuccessMessage] = useState('');
  const [reaction, setReaction] = useState<CharacterState>('idle');
  const requestController = useRef<AbortController | null>(null);
  const reactionTimer = useRef<number | undefined>();
  const successTimer = useRef<number | undefined>();

  const normalizedEmail = email.trim().toLowerCase();
  const emailIsValid =
    emailPattern.test(normalizedEmail) && normalizedEmail.length <= 320;
  const passwordIsValid = password.length >= 8 && password.length <= 72;
  const passwordIsFocused =
    focusTarget === 'PASSWORD' || focusTarget === 'CONFIRMATION';
  const focusedPasswordIsVisible =
    focusTarget === 'CONFIRMATION' ? confirmationVisible : passwordVisible;

  useEffect(() => {
    setActiveMode(initialMode);
    setPassword('');
    setConfirmation('');
    setPasswordVisible(false);
    setConfirmationVisible(false);
    setError('');
    setFieldErrors({});
    setSuccessMessage('');
    setReaction('idle');
  }, [initialMode]);

  useEffect(
    () => () => {
      requestController.current?.abort();
      if (reactionTimer.current !== undefined) {
        window.clearTimeout(reactionTimer.current);
      }
      if (successTimer.current !== undefined) {
        window.clearTimeout(successTimer.current);
      }
    },
    [],
  );

  const showReaction = (next: CharacterState, resetAfter?: number) => {
    if (reactionTimer.current !== undefined) {
      window.clearTimeout(reactionTimer.current);
    }
    setReaction(next);
    if (resetAfter) {
      reactionTimer.current = window.setTimeout(() => {
        setReaction('idle');
        reactionTimer.current = undefined;
      }, resetAfter);
    }
  };

  const characterState = useMemo<CharacterState>(() => {
    if (reaction !== 'idle') return reaction;
    if (loading) return 'loading';
    if (passwordIsFocused && !focusedPasswordIsVisible) return 'privacy';
    if (
      activeMode === 'SIGNUP' &&
      confirmation &&
      password !== confirmation
    ) {
      return 'warning';
    }
    if (focusTarget === 'EMAIL' && emailIsValid) return 'approved';
    if (focusTarget !== 'NONE') return 'observing';
    return 'idle';
  }, [
    activeMode,
    confirmation,
    emailIsValid,
    focusTarget,
    focusedPasswordIsVisible,
    loading,
    password,
    passwordIsFocused,
    reaction,
  ]);

  const focusedCharacterGaze = useMemo(
    () =>
      focusTarget === 'EMAIL' ||
      (passwordIsFocused && focusedPasswordIsVisible)
        ? { x: 1, y: 0.08 }
        : undefined,
    [
      focusTarget,
      focusedPasswordIsVisible,
      passwordIsFocused,
    ],
  );

  const clearFieldError = (field: AuthField) => {
    setFieldErrors((current) => {
      if (!current[field]) return current;
      const next = { ...current };
      delete next[field];
      return next;
    });
    setError('');
    setSuccessMessage('');
  };

  const validate = (): FieldErrors => {
    const next: FieldErrors = {};
    if (!emailIsValid) {
      next.email = 'Geçerli bir e-posta adresi girin.';
    }
    if (!passwordIsValid) {
      next.password = 'Şifre 8 ile 72 karakter arasında olmalıdır.';
    }
    if (activeMode === 'SIGNUP') {
      if (!confirmation) {
        next.confirmation = 'Şifrenizi tekrar girin.';
      } else if (password !== confirmation) {
        next.confirmation = 'Şifreler eşleşmiyor.';
      }
    }
    return next;
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (loading) return;

    const nextErrors = validate();
    if (Object.keys(nextErrors).length) {
      setFieldErrors(nextErrors);
      setError('Lütfen işaretli alanları kontrol edin.');
      showReaction('error', 750);
      return;
    }

    requestController.current?.abort();
    const controller = new AbortController();
    requestController.current = controller;
    setLoading(true);
    setError('');
    setFieldErrors({});
    setSuccessMessage('');

    try {
      const response =
        activeMode === 'LOGIN'
          ? await api.login(normalizedEmail, password, controller.signal)
          : await api.register(normalizedEmail, password, controller.signal);
      if (controller.signal.aborted) return;
      setSuccessMessage(
        response.message ||
          (activeMode === 'LOGIN'
            ? 'Giriş başarılı.'
            : 'Hesabınız oluşturuldu.'),
      );
      showReaction('success');
      setLoading(false);
      successTimer.current = window.setTimeout(
        () => onLoginSuccess(response.user.email),
        320,
      );
    } catch (requestError) {
      if (
        requestError instanceof DOMException &&
        requestError.name === 'AbortError'
      ) {
        return;
      }
      if (requestError instanceof ApiError) {
        setFieldErrors({
          email: requestError.fieldErrors.email,
          password: requestError.fieldErrors.password,
        });
      }
      setError(getUserFacingError(requestError));
      showReaction('error', 780);
      setLoading(false);
    }
  };

  const handleTestLogin = async () => {
    if (loading) return;
    requestController.current?.abort();
    const controller = new AbortController();
    requestController.current = controller;
    setLoading(true);
    setError('');
    setFieldErrors({});
    setSuccessMessage('Demo hesabı hazırlanıyor, lütfen bekleyin...');

    try {
      const response = await ensureDemoAccount(controller.signal);
      if (controller.signal.aborted) return;
      setSuccessMessage('Demo hesabına giriş yapılıyor...');
      showReaction('success');
      setLoading(false);
      successTimer.current = window.setTimeout(
        () => onLoginSuccess(response.user.email),
        320,
      );
    } catch (requestError) {
      if (
        requestError instanceof DOMException &&
        requestError.name === 'AbortError'
      ) {
        return;
      }
      setError(getUserFacingError(requestError));
      setSuccessMessage('');
      showReaction('error', 780);
      setLoading(false);
    }
  };


  const changeMode = (nextMode: AuthMode) => {
    if (loading || nextMode === activeMode) return;
    requestController.current?.abort();
    setActiveMode(nextMode);
    setPassword('');
    setConfirmation('');
    setPasswordVisible(false);
    setConfirmationVisible(false);
    setError('');
    setFieldErrors({});
    setSuccessMessage('');
    setReaction('idle');
    setFocusTarget('NONE');
    onModeChange?.(nextMode);
  };

  const emailDescription = [
    'auth-email-help',
    fieldErrors.email ? 'auth-email-error' : '',
  ]
    .filter(Boolean)
    .join(' ');
  const passwordDescription = [
    activeMode === 'SIGNUP' ? 'auth-password-help' : '',
    fieldErrors.password ? 'auth-password-error' : '',
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <main className="auth-page" id="auth-main" tabIndex={-1}>
      <a className="skip-link" href="#auth-form">
        {activeMode === 'LOGIN' ? 'Giriş formuna geç' : 'Kayıt formuna geç'}
      </a>

      <section className="auth-visual" aria-labelledby="auth-visual-title">
        <div className="auth-visual__topline">
          <a
            className="brand brand--light"
            href="/"
            onClick={(event) => {
              if (!onBack) return;
              event.preventDefault();
              onBack();
            }}
          >
            <span className="brand__mark" aria-hidden="true">
              <Zap size={21} strokeWidth={2.6} />
            </span>
            <span className="brand__wordmark">
              Volt<span>Flow</span>
            </span>
          </a>
          <span className="auth-visual__badge">
            <Sparkles aria-hidden="true" size={14} /> Eviniz için enerji zekâsı
          </span>
        </div>

        <div className="auth-visual__copy">
          <p className="eyebrow eyebrow--light">Akıllı ev ekibi hazır</p>
          <h1 id="auth-visual-title">
            Enerjinizin <span>karakterini</span> tanıyın.
          </h1>
          <p>
            Cihazlarınız tüketimi anlatır, VoltFlow bütçenizi korur ve olağan
            dışı davranışları büyümeden haber verir.
          </p>
        </div>

        <CharacterGroup
          className="auth-character-stage"
          gazeEnabled
          trackViewport
          gazeLimit={8}
          gazeStrength={0.85}
          orbitAnimation={false}
          aria-hidden="true"
        >
          <div className="auth-character auth-character--fridge">
            <ApplianceCharacter
              type="REFRIGERATOR"
              state={characterState}
            />
          </div>
          <div className="auth-character auth-character--washer">
            <ApplianceCharacter
              type="WASHING_MACHINE"
              state={characterState === 'privacy' ? 'privacy' : (activeMode === 'SIGNUP' ? 'active' : 'idle')}
            />
          </div>
          <div className="auth-character auth-character--tv">
            <ApplianceCharacter
              type="TELEVISION"
              state={characterState === 'privacy' ? 'privacy' : 'observing'}
            />
          </div>
          <div className="auth-character auth-character--kettle">
            <ApplianceCharacter
              type="KETTLE"
              state={characterState === 'privacy' ? 'privacy' : 'happy'}
            />
          </div>
        </CharacterGroup>
      </section>

      <section className="auth-panel">
        <div className="auth-panel__inner">
          <div className="auth-nav-header">
            {onBack ? (
              <button className="auth-back" type="button" onClick={onBack}>
                <ArrowLeft aria-hidden="true" size={17} /> VoltFlow’u keşfet
              </button>
            ) : <div />}

            <div className="auth-tabs" role="group" aria-label="Hesap işlemi">
              <button
                type="button"
                aria-pressed={activeMode === 'LOGIN'}
                className={activeMode === 'LOGIN' ? 'is-active' : ''}
                onClick={() => changeMode('LOGIN')}
              >
                Giriş yap
              </button>
              <button
                type="button"
                aria-pressed={activeMode === 'SIGNUP'}
                className={activeMode === 'SIGNUP' ? 'is-active' : ''}
                onClick={() => changeMode('SIGNUP')}
              >
                Kaydol
              </button>
            </div>
          </div>

          <div className="auth-heading">
            <span className="auth-heading__icon" aria-hidden="true">
              {activeMode === 'LOGIN' ? (
                <LockKeyhole size={20} />
              ) : (
                <UserPlus size={20} />
              )}
            </span>
            <div>
              <p className="eyebrow">
                {activeMode === 'LOGIN'
                  ? 'Tekrar hoş geldiniz'
                  : 'Aramıza hoş geldiniz'}
              </p>
              <h2>
                {activeMode === 'LOGIN'
                  ? 'Hesabınıza Giriş Yapın'
                  : 'VoltFlow’a Katılın'}
              </h2>
              <p>
                {activeMode === 'LOGIN'
                  ? 'Enerji kullanımınızı ve ev cihazlarınızı görüntülemek için e-posta adresiniz ve şifrenizle giriş yapın.'
                  : 'Enerji kullanımınızı izlemek için e-posta adresiniz ve güvenli bir şifreyle hesabınızı oluşturun.'}
              </p>
            </div>
          </div>

          <div className="auth-announcements">
            {error && (
              <div
                className="form-alert form-alert--error"
                id="auth-error"
                role="alert"
              >
                <span aria-hidden="true">!</span>
                <p>{error}</p>
              </div>
            )}
            {successMessage && !error && (
              <div className="form-alert form-alert--success" role="status">
                <Check aria-hidden="true" size={16} />
                <p>{successMessage}</p>
              </div>
            )}
          </div>

          <form
            id="auth-form"
            className="auth-form"
            onSubmit={handleSubmit}
            noValidate
          >
            <div className="field auth-field">
              <label htmlFor="auth-email">E-posta adresi</label>
              <span className="auth-input">
                <Mail aria-hidden="true" size={18} />
                <input
                  id="auth-email"
                  type="email"
                  inputMode="email"
                  autoComplete="email"
                  value={email}
                  onFocus={() => setFocusTarget('EMAIL')}
                  onBlur={() => setFocusTarget('NONE')}
                  onChange={(event) => {
                    setEmail(event.target.value);
                    clearFieldError('email');
                  }}
                  aria-invalid={Boolean(fieldErrors.email)}
                  aria-describedby={emailDescription}
                  placeholder="siz@example.com"
                  maxLength={320}
                  required
                />
                {emailIsValid && (
                  <Check
                    className="auth-input__valid"
                    aria-label="Geçerli e-posta"
                    size={17}
                  />
                )}
              </span>
              <small id="auth-email-help">
                E-posta adresiniz küçük harfe dönüştürülerek güvenle eşleştirilir.
              </small>
              {fieldErrors.email && (
                <span className="field-error" id="auth-email-error">
                  {fieldErrors.email}
                </span>
              )}
            </div>

            <div className="field auth-field">
              <label htmlFor="auth-password">Şifre</label>
              <span className="auth-input">
                <LockKeyhole aria-hidden="true" size={18} />
                <input
                  id="auth-password"
                  type={passwordVisible ? 'text' : 'password'}
                  autoComplete={
                    activeMode === 'LOGIN'
                      ? 'current-password'
                      : 'new-password'
                  }
                  value={password}
                  onFocus={() => setFocusTarget('PASSWORD')}
                  onBlur={() => setFocusTarget('NONE')}
                  onChange={(event) => {
                    setPassword(event.target.value);
                    clearFieldError('password');
                    if (fieldErrors.confirmation) {
                      clearFieldError('confirmation');
                    }
                  }}
                  aria-invalid={Boolean(fieldErrors.password)}
                  aria-describedby={passwordDescription || undefined}
                  minLength={activeMode === 'SIGNUP' ? 8 : undefined}
                  maxLength={72}
                  required
                />
                <button
                  className="auth-password-toggle"
                  type="button"
                  onClick={() => setPasswordVisible((visible) => !visible)}
                  aria-label={passwordVisible ? 'Şifreyi gizle' : 'Şifreyi göster'}
                  aria-pressed={passwordVisible}
                >
                  {passwordVisible ? (
                    <EyeOff aria-hidden="true" size={18} />
                  ) : (
                    <Eye aria-hidden="true" size={18} />
                  )}
                </button>
              </span>
              {activeMode === 'SIGNUP' && (
                <small id="auth-password-help">
                  Şifreniz en az 8 karakter olmalıdır.
                </small>
              )}
              {fieldErrors.password && (
                <span className="field-error" id="auth-password-error">
                  {fieldErrors.password}
                </span>
              )}
            </div>

            {activeMode === 'SIGNUP' && (
              <div className="field auth-field">
                <label htmlFor="auth-password-confirmation">
                  Şifre tekrarı
                </label>
                <span className="auth-input">
                  <ShieldCheck aria-hidden="true" size={18} />
                  <input
                    id="auth-password-confirmation"
                    type={confirmationVisible ? 'text' : 'password'}
                    autoComplete="new-password"
                    value={confirmation}
                    onFocus={() => setFocusTarget('CONFIRMATION')}
                    onBlur={() => setFocusTarget('NONE')}
                    onChange={(event) => {
                      setConfirmation(event.target.value);
                      clearFieldError('confirmation');
                    }}
                    aria-invalid={Boolean(fieldErrors.confirmation)}
                    aria-describedby={
                      fieldErrors.confirmation
                        ? 'auth-confirmation-error'
                        : undefined
                    }
                    maxLength={72}
                    required
                  />
                  <button
                    className="auth-password-toggle"
                    type="button"
                    onClick={() =>
                      setConfirmationVisible((visible) => !visible)
                    }
                    aria-label={
                      confirmationVisible
                        ? 'Şifre tekrarını gizle'
                        : 'Şifre tekrarını göster'
                    }
                    aria-pressed={confirmationVisible}
                  >
                    {confirmationVisible ? (
                      <EyeOff aria-hidden="true" size={18} />
                    ) : (
                      <Eye aria-hidden="true" size={18} />
                    )}
                  </button>
                </span>
                {fieldErrors.confirmation && (
                  <span className="field-error" id="auth-confirmation-error">
                    {fieldErrors.confirmation}
                  </span>
                )}
              </div>
            )}

            <button
              className="button button--primary button--large auth-submit"
              type="submit"
              disabled={loading}
            >
              {loading ? (
                <>
                  <span className="spinner" aria-hidden="true" />
                  {activeMode === 'LOGIN'
                    ? 'Giriş yapılıyor'
                    : 'Hesap oluşturuluyor'}
                </>
              ) : (
                <>
                  {activeMode === 'LOGIN' ? 'Giriş yap' : 'Hesap oluştur'}
                  <ArrowRight aria-hidden="true" size={18} />
                </>
              )}
            </button>

            <div className="auth-demo-divider" aria-hidden="true">
              <span>ya da</span>
            </div>

            <button
              id="demo-login-btn"
              className="button button--demo-login button--large"
              type="button"
              disabled={loading}
              onClick={handleTestLogin}
              aria-label="Demo hesabı ile tek tıkla giriş yapın. 6 ev ve canlı telemetri verisi içerir."
            >
              {loading ? (
                <>
                  <span className="spinner" aria-hidden="true" />
                  Demo hazırlanıyor
                </>
              ) : (
                <>
                  <Zap aria-hidden="true" size={18} />
                  Test Girişi
                </>
              )}
            </button>
          </form>




          <div className="auth-mode-prompt">
            <span>
              {activeMode === 'LOGIN'
                ? 'Henüz hesabınız yok mu?'
                : 'Zaten hesabınız var mı?'}
            </span>
            <button
              type="button"
              onClick={() =>
                changeMode(activeMode === 'LOGIN' ? 'SIGNUP' : 'LOGIN')
              }
            >
              {activeMode === 'LOGIN' ? 'Hesap oluşturun' : 'Giriş yapın'}
            </button>
          </div>

          <div className="auth-security-note">
            <ShieldCheck aria-hidden="true" size={17} />
            <p>
              Şifreniz güvenli biçimde saklanır ve hiçbir zaman VoltFlow
              yanıtlarında gösterilmez.
            </p>
          </div>
        </div>
      </section>
    </main>
  );
}
