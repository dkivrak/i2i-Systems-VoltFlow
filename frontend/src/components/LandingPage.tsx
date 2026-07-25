import {
  Activity,
  ArrowRight,
  BarChart3,
  BellRing,
  CircleDollarSign,
  Gauge,
  HousePlug,
  Mail,
  ShieldAlert,
  Sparkles,
  Waves,
  Zap,
} from 'lucide-react';
import { ApplianceCharacter, CharacterGroup } from '../characters';
import { useEffect, useRef, useState } from 'react';

interface BenefitCardProps {
  icon: React.ComponentType<any>;
  title: string;
  copy: string;
  tone: string;
  index: number;
}

interface LandingPageProps {
  onLogin: () => void;
  onRegister: () => void;
}

const BenefitCard: React.FC<BenefitCardProps> = ({ icon: Icon, title, copy, tone, index }) => {
  const [isVisible, setIsVisible] = useState(false);
  const elementRef = useRef<HTMLElement>(null);

  useEffect(() => {
    if (typeof window === 'undefined' || !('IntersectionObserver' in window)) {
      setIsVisible(true);
      return;
    }

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setIsVisible(true);
          observer.unobserve(entry.target);
        }
      },
      { threshold: 0.1, rootMargin: '0px 0px -50px 0px' }
    );

    const el = elementRef.current;
    if (el) {
      observer.observe(el);
    }

    return () => {
      if (el) {
        observer.unobserve(el);
      }
      observer.disconnect();
    };
  }, []);

  return (
    <article
      ref={elementRef}
      className={`landing-feature-card landing-feature-card--${tone} scroll-reveal ${isVisible ? 'is-visible' : ''}`}
      style={{
        transitionDelay: isVisible ? `${index * 100}ms` : undefined,
      }}
    >
      <span className="landing-feature-card__icon" aria-hidden="true">
        <Icon size={21} />
      </span>
      <h3>{title}</h3>
      <p>{copy}</p>
    </article>
  );
};

interface LandingPageProps {
  onLogin: () => void;
  onRegister: () => void;
}

const benefits = [
  {
    icon: Activity,
    title: 'Canlı tüketimi görün',
    copy: 'Evinizin ve çalışan cihazların anlık gücünü, son telemetri zamanı ile birlikte izleyin.',
    tone: 'purple',
  },
  {
    icon: ShieldAlert,
    title: 'Anormallikleri erken yakalayın',
    copy: 'Ardışık güç sınırı ihlallerini net ölçümler, güvenli limitler ve anlaşılır uyarılarla inceleyin.',
    tone: 'coral',
  },
  {
    icon: CircleDollarSign,
    title: 'Bütçeyi aşmadan harekete geçin',
    copy: 'Aylık harcama, kota eşiği ve ceza tarifesi riskini tek bakışta anlayın.',
    tone: 'yellow',
  },
  {
    icon: BarChart3,
    title: 'Geçmişi bağlama dönüştürün',
    copy: 'Saatlik tüketim, maliyet eğrisi ve cihaz dağılımıyla bugünü önceki dönemlerle karşılaştırın.',
    tone: 'blue',
  },
  {
    icon: BellRing,
    title: 'Önemli anları kaçırmayın',
    copy: 'Bütçe, tarife ve cihaz olaylarını okunabilir bir denetim akışında takip edin.',
    tone: 'orange',
  },
  {
    icon: Sparkles,
    title: 'Tasarruf önerileri alın',
    copy: 'Mevcut enerji durumunuza göre hazırlanan uygulanabilir önerilerle gereksiz tüketimi azaltın.',
    tone: 'green',
  },
] as const;

const steps = [
  {
    number: '01',
    title: 'Evinizi ve cihazlarınızı ekleyin',
    copy: 'Aylık bütçenizi ve her cihaz için güvenli Watt sınırını tanımlayın.',
    videoUrl: `${import.meta.env.BASE_URL}videos/setup-home.mp4`,
  },
  {
    number: '02',
    title: 'Canlı enerji akışını izleyin',
    copy: 'VoltFlow yeni telemetriyi otomatik olarak işler ve ekranı kesintisiz günceller.',
    videoUrl: `${import.meta.env.BASE_URL}videos/monitor-energy.mp4`,
  },
  {
    number: '03',
    title: 'Uyarıyı anlayıp harekete geçin',
    copy: 'Karakter durumu, kesin ölçümler ve önerilen adım birlikte sunulur.',
    videoUrl: `${import.meta.env.BASE_URL}videos/improve-usage.mp4`,
  },
] as const;

const ScrollDrivenSection: React.FC = () => {
  const containerRef = useRef<HTMLDivElement>(null);
  const [progress, setProgress] = useState(0);
  const [windowWidth, setWindowWidth] = useState(window.innerWidth);

  useEffect(() => {
    const handleScroll = () => {
      if (!containerRef.current) return;
      const rect = containerRef.current.getBoundingClientRect();
      const viewHeight = window.innerHeight;
      const scrollHeight = rect.height;
      const offsetTop = -rect.top;
      const totalScrollable = scrollHeight - viewHeight;

      if (totalScrollable <= 0) return;
      const currentProgress = Math.min(Math.max(offsetTop / totalScrollable, 0), 1);
      setProgress(currentProgress);
    };

    const handleResize = () => {
      setWindowWidth(window.innerWidth);
      handleScroll();
    };

    window.addEventListener('scroll', handleScroll, { passive: true });
    window.addEventListener('resize', handleResize);
    handleScroll();

    return () => {
      window.removeEventListener('scroll', handleScroll);
      window.removeEventListener('resize', handleResize);
    };
  }, []);

  const cardWidth = Math.min(840, windowWidth * 0.85);
  const gap = 32;
  const initialOffset = (windowWidth - cardWidth) / 2;
  const translateX = initialOffset - (progress * 2) * (cardWidth + gap);

  const activeIndex = progress < 0.33 ? 0 : progress < 0.66 ? 1 : 2;

  const scrollToCard = (index: number) => {
    if (!containerRef.current) return;
    const rect = containerRef.current.getBoundingClientRect();
    const absoluteTop = window.scrollY + rect.top;
    const viewHeight = window.innerHeight;
    const scrollHeight = rect.height;
    const totalScrollable = scrollHeight - viewHeight;
    const targetProgress = index / 2;
    const targetScroll = absoluteTop + targetProgress * totalScrollable;
    window.scrollTo({
      top: targetScroll,
      behavior: 'smooth',
    });
  };

  const isDesktop = windowWidth > 991;

  return (
    <div ref={containerRef} className="scroll-horizontal-container" id="landing-how-scroll">
      <div className="sticky-wrapper">
        <div className="scroll-horizontal-header">
          <p className="eyebrow">Üç adımda kontrol</p>
          <h2 id="how-title">Kurun, izleyin, iyileştirin.</h2>
        </div>

        <nav className="scroll-driven-navbar" aria-label="Adım navigasyonu">
          {steps.map((step, index) => (
            <button
              key={step.number}
              type="button"
              className={`scroll-driven-nav-btn ${activeIndex === index ? 'is-active' : ''}`}
              onClick={() => scrollToCard(index)}
            >
              {step.number} {index === 0 ? 'Kurulum' : index === 1 ? 'İzleme' : 'İyileştirme'}
            </button>
          ))}
        </nav>

        <div className="horizontal-carousel-wrapper">
          <div
            className="horizontal-track"
            style={isDesktop ? { transform: `translateX(${translateX}px)` } : undefined}
          >
            {steps.map((step, index) => (
              <div
                className={`horizontal-card ${activeIndex === index ? 'is-active' : ''}`}
                key={step.number}
              >
                <div className="card-video-wrapper">
                  <video
                    src={step.videoUrl}
                    autoPlay
                    muted
                    loop
                    playsInline
                    preload="metadata"
                    controls={false}
                    disablePictureInPicture
                    className="card-video"
                  />
                </div>
                <div className="card-body">
                  <span aria-hidden="true" className="card-step-num">{step.number}</span>
                  <h3>{step.title}</h3>
                  <p>{step.copy}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
};

export function LandingPage({ onLogin, onRegister }: LandingPageProps) {
  return (
    <div className="landing-page">
      <a className="skip-link" href="#landing-main">
        Ana içeriğe geç
      </a>

      <header className="landing-header">
        <div className="landing-header__inner">
          <span className="brand" aria-label="VoltFlow">
            <span className="brand__mark" aria-hidden="true">
              <Zap size={21} strokeWidth={2.6} />
            </span>
            <span className="brand__wordmark">
              Volt<span>Flow</span>
            </span>
          </span>

          <nav className="landing-header__actions" aria-label="Hesap işlemleri">
            <button className="button button--ghost" type="button" onClick={onLogin}>
              Giriş yap
            </button>
            <button className="button button--primary" type="button" onClick={onRegister}>
              Ücretsiz başla <ArrowRight aria-hidden="true" size={17} />
            </button>
          </nav>
        </div>
      </header>

      <main id="landing-main" tabIndex={-1}>
        <section className="landing-hero" aria-labelledby="landing-title">
          <div className="landing-hero__copy">
            <p className="eyebrow">
              <Waves aria-hidden="true" size={14} /> Evinizin enerji ekibi
            </p>
            <h1 id="landing-title">
              Enerjiyi izlemek değil, <span>anlamak</span> için.
            </h1>
            <p>
              VoltFlow canlı cihaz tüketimini, bütçe risklerini ve anormal davranışları
              tek bir sakin, anlaşılır akışta buluşturur.
            </p>
            <div className="landing-hero__actions">
              <button className="button button--primary button--large" type="button" onClick={onRegister}>
                Evimi izlemeye başla <ArrowRight aria-hidden="true" size={18} />
              </button>
              <button className="button button--secondary button--large" type="button" onClick={onLogin}>
                Hesabıma giriş yap
              </button>
            </div>
            <ul className="landing-hero__proof" aria-label="VoltFlow canlı sistem özellikleri">
              <li><span aria-hidden="true" /> 1–2 saniyelik canlı güncelleme</li>
              <li><span aria-hidden="true" /> Hassas Watt ve maliyet verisi</li>
              <li><span aria-hidden="true" /> Erişilebilir risk uyarıları</li>
            </ul>
          </div>

          <div className="landing-hero__visual" aria-hidden="true">
            <CharacterGroup
              className="character-scene character-scene--hero"
              gazeEnabled
              gazeLimit={3}
              gazeStrength={0.45}
            >
              <span className="character-scene__energy-orbit" />
              <span className="character-scene__energy-badge">
                <Zap size={15} /> Canlı enerji akışı
              </span>
              <div className="character-scene__item character-scene__item--fridge">
                <ApplianceCharacter type="REFRIGERATOR" state="happy" />
              </div>
              <div className="character-scene__item character-scene__item--washer">
                <ApplianceCharacter type="WASHING_MACHINE" state="active" />
              </div>
              <div className="character-scene__item character-scene__item--tv">
                <ApplianceCharacter type="TELEVISION" state="observing" />
              </div>
              <div className="character-scene__item character-scene__item--kettle">
                <ApplianceCharacter type="KETTLE" state="idle" />
              </div>
              <div className="character-scene__item character-scene__item--lamp">
                <ApplianceCharacter type="LAMP" state="active" />
              </div>
            </CharacterGroup>
          </div>
        </section>

        <section className="landing-signal-strip" aria-label="VoltFlow enerji özeti örneği">
          <p className="landing-signal-strip__label">Örnek enerji özeti</p>
          <article>
            <Activity aria-hidden="true" size={19} />
            <span>Şu an</span>
            <strong>2,4 kW</strong>
          </article>
          <article>
            <CircleDollarSign aria-hidden="true" size={19} />
            <span>Bu dönem</span>
            <strong>284,50 ₺</strong>
          </article>
          <article>
            <Gauge aria-hidden="true" size={19} />
            <span>Bütçe kullanımı</span>
            <strong>%28</strong>
          </article>
          <article>
            <HousePlug aria-hidden="true" size={19} />
            <span>Bağlı cihaz</span>
            <strong>9</strong>
          </article>
        </section>

        <section className="landing-section landing-benefits" aria-labelledby="benefits-title">
          <div className="landing-section__heading">
            <p className="eyebrow">Bir bakışta VoltFlow</p>
            <h2 id="benefits-title">Teknik veriyi günlük kararlara dönüştürün.</h2>
            <p>İhtiyacınız olan ayrıntı görünür; geri kalanı siz istediğinizde açılır.</p>
          </div>
          <div className="landing-benefits__grid">
            {benefits.map(({ icon: Icon, title, copy, tone }, index) => (
              <BenefitCard
                key={title}
                icon={Icon}
                title={title}
                copy={copy}
                tone={tone}
                index={index}
              />
            ))}
          </div>
        </section>

        <section className="landing-section landing-story" aria-labelledby="story-title">
          <div className="landing-story__visual" aria-hidden="true">
            <div className="character-scene character-scene--warning">
              <ApplianceCharacter type="AIR_CONDITIONER" state="warning" />
              <span className="character-alert-bubble">
                <ShieldAlert size={16} /> Güvenli sınır aşıldı
              </span>
              <span className="character-meter"><i /></span>
            </div>
          </div>
          <div className="landing-story__copy">
            <p className="eyebrow">Örnek anomali · sadece kırmızı bir nokta değil</p>
            <h2 id="story-title">Bir uyarının nedenini de görün.</h2>
            <p>
              VoltFlow, olağan dışı tüketimi karakter ifadesiyle görünür kılarken
              ölçülen gücü, güvenli sınırı ve ardışık ihlal sayısını eksiksiz korur.
            </p>
            <dl className="landing-story__metrics">
              <div><dt>Ölçülen güç</dt><dd>2.550 W</dd></div>
              <div><dt>Güvenli sınır</dt><dd>2.200 W</dd></div>
              <div><dt>Ardışık ihlal</dt><dd>3 / 3</dd></div>
            </dl>
          </div>
        </section>

        <ScrollDrivenSection />

        <section className="landing-final-cta" aria-labelledby="final-cta-title">
          <div className="landing-final-cta__characters" aria-hidden="true">
            <ApplianceCharacter type="COMPUTER" state="happy" />
            <ApplianceCharacter type="OVEN" state="idle" />
          </div>
          <div>
            <p className="eyebrow">Enerjiniz sizinle konuşsun</p>
            <h2 id="final-cta-title">Akıllı evinizi daha bilinçli kullanmaya başlayın.</h2>
            <p>İlk evinizi ekleyin; canlı tüketim görünümünüz birkaç adımda hazır olsun.</p>
          </div>
          <button className="button button--primary button--large" type="button" onClick={onRegister}>
            VoltFlow&apos;u keşfet <ArrowRight aria-hidden="true" size={18} />
          </button>
        </section>
      </main>

      <footer className="landing-footer">
        <span>VoltFlow enerji zekâsı</span>
        <span>Gerçek veriler · anlaşılır kararlar</span>
      </footer>
    </div>
  );
}
