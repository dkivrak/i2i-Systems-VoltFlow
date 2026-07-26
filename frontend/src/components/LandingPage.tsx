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

  const cardWidth = Math.min(680, windowWidth * 0.76);
  const gap = 40;
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

              {/* Dönen Hatlar & Merkez Hub Konteyneri (Karakterlerin yörünge hareketiyle tam senkronize dönmesi için) */}
              <div className="character-scene__rotator">
                {/* Ortadaki Şimşek / Elektrik Hub Simgesi */}
                <div className="character-scene__center-hub">
                  <div className="character-scene__center-icon">
                    <Zap size={28} />
                  </div>
                </div>

              {/* Karakterlere Tam Sabitlenmiş Dinamik Çizgiler */}
              <svg className="character-scene__connections" viewBox="0 0 100 100" preserveAspectRatio="none">
                {/* Dış Çevre Bağlantı Çemberi */}
                <circle cx="50" cy="50" r="36" className="scene-connect-ring" />
                
                {/* Merkezdeki Şimşekten Karakterlere Giden Dinamik Hatlar */}
                <line className="scene-connect-line scene-connect-line-0" />
                <line className="scene-connect-line scene-connect-line-1" />
                <line className="scene-connect-line scene-connect-line-2" />
                <line className="scene-connect-line scene-connect-line-3" />
                <line className="scene-connect-line scene-connect-line-4" />

                {/* Karakterler Arasındaki Ağ Çizgileri */}
                <line className="scene-connect-mesh scene-connect-mesh-0" />
                <line className="scene-connect-mesh scene-connect-mesh-1" />
                <line className="scene-connect-mesh scene-connect-mesh-2" />
                <line className="scene-connect-mesh scene-connect-mesh-3" />
                <line className="scene-connect-mesh scene-connect-mesh-4" />
              </svg>
              </div>

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



        {/* 1. Rakamlarla Güven (Social Proof / Stats Strip) */}
        <section className="landing-stats-strip" aria-label="VoltFlow platform istatistikleri">
          <div className="landing-stats-strip__item">
            <span className="landing-stats-strip__number">1.200+</span>
            <span className="landing-stats-strip__label">Aktif Konut / Ev</span>
          </div>
          <div className="landing-stats-strip__item">
            <span className="landing-stats-strip__number">9.400+</span>
            <span className="landing-stats-strip__label">İzlenen Cihaz</span>
          </div>
          <div className="landing-stats-strip__item">
            <span className="landing-stats-strip__number">%23</span>
            <span className="landing-stats-strip__label">Ort. Aylık Tasarruf</span>
          </div>
          <div className="landing-stats-strip__item">
            <span className="landing-stats-strip__number">&lt; 1 sn</span>
            <span className="landing-stats-strip__label">Anomali Algılama</span>
          </div>
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

        {/* 2. Desteklenen Cihazlar Şeridi */}
        <section className="landing-devices" aria-labelledby="devices-title">
          <div className="landing-devices__heading">
            <p className="eyebrow">Tam Ekosistem Desteği</p>
            <h2 id="devices-title">Evinizdeki her cihazın kendi dili var.</h2>
            <p>VoltFlow 9 farklı cihaz türünü anlık akıllı karakterlerle izler ve anormallikleri anında bildirir.</p>
          </div>
          <div className="landing-devices__grid">
            {[
              { type: 'REFRIGERATOR', name: 'Buzdolabı', desc: 'Gece ve gündüz sürekli yük takibi' },
              { type: 'WASHING_MACHINE', name: 'Çamaşır M.', desc: 'Yüksek güç döngüsü optimizasyonu' },
              { type: 'TELEVISION', name: 'Televizyon', desc: 'Bekleme (standby) modu kaçağı tespiti' },
              { type: 'KETTLE', name: 'Su Isıtıcısı', desc: 'Ani pik çekim uyarısı' },
              { type: 'AIR_CONDITIONER', name: 'Klima', desc: 'Sürekli yüksek yük ve ceza sınırı kontrolü' },
              { type: 'OVEN', name: 'Fırın', desc: 'Bütçe aşım riski uyarısı' },
              { type: 'MICROWAVE', name: 'Mikrodalga', desc: 'Kısa süreli akım kontrolü' },
              { type: 'LAMP', name: 'Aydınlatma', desc: 'Arka plan tüketim analizi' },
              { type: 'COMPUTER', name: 'Bilgisayar', desc: 'Çalışma saati enerji maliyeti' },
            ].map((item) => (
              <div key={item.type} className="landing-device-card">
                <ApplianceCharacter type={item.type as any} state="idle" />
                <span className="landing-device-card__name">{item.name}</span>
                <div className="landing-device-card__tooltip">{item.desc}</div>
              </div>
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

        {/* 3. Tarife Karşılaştırma / Fiyatlandırma */}
        <section className="landing-tariff" aria-labelledby="tariff-title">
          <div className="landing-tariff__inner">
            <div className="landing-tariff__copy">
              <p className="eyebrow">Dinamik Tarife Koruması</p>
              <h2 id="tariff-title">Ceza tarifesine geçmeden müdahale edin.</h2>
              <p>
                Aylık bütçe kotanız %100 aşılınca elektrik birim fiyatınız ceza kademesine yükselir.
                VoltFlow %80 seviyesinde yapay zeka uyarısı vererek ekstra fatura maliyetini engeller.
              </p>
              <div className="landing-tariff__savings">
                <strong>%23&apos;e varan</strong>
                <span>Fatura tasarrufu ile ceza kademesinden korunun</span>
              </div>
            </div>

            <div className="landing-tariff__chart">
              <div className="tariff-bar">
                <span className="tariff-bar__label">Normal Tarife</span>
                <div className="tariff-bar__track">
                  <div className="tariff-bar__fill tariff-bar__fill--normal" style={{ width: '45%' }} />
                </div>
                <span className="tariff-bar__value tariff-bar__value--normal">2,07 ₺/kWh</span>
              </div>

              <div className="tariff-bar">
                <span className="tariff-bar__label">Ceza Tarifesi</span>
                <div className="tariff-bar__track">
                  <div className="tariff-bar__fill tariff-bar__fill--penalty" style={{ width: '90%' }} />
                </div>
                <span className="tariff-bar__value tariff-bar__value--penalty">3,11 ₺/kWh</span>
              </div>

              <div className="tariff-bar">
                <span className="tariff-bar__label">VoltFlow ile</span>
                <div className="tariff-bar__track">
                  <div className="tariff-bar__fill tariff-bar__fill--savings" style={{ width: '40%' }} />
                </div>
                <span className="tariff-bar__value tariff-bar__value--savings">Tasarruflu</span>
              </div>
            </div>
          </div>
        </section>

        <ScrollDrivenSection />

        {/* 4. Kullanıcı Yorumları (Testimonials) */}
        <section className="landing-testimonials" aria-labelledby="testimonials-title">
          <div className="landing-testimonials__heading">
            <p className="eyebrow">Kullanıcı Deneyimleri</p>
            <h2 id="testimonials-title">Akıllı ev sahipleri ne diyor?</h2>
            <p>VoltFlow ile enerji tüketim alışkanlıklarını değiştiren kullanıcılarımızın geri bildirimleri.</p>
          </div>
          <div className="landing-testimonials__grid">
            <div className="testimonial-card">
              <p className="testimonial-card__quote">
                &ldquo;Buzdolabımın gece saatlerinde sürekli anomaliye girdiğini VoltFlow sayesinden öğrendim. Eskiyen contayı değiştirip faturamda ciddi fark yarattım.&rdquo;
              </p>
              <div className="testimonial-card__author">
                <ApplianceCharacter type="REFRIGERATOR" state="happy" />
                <div className="testimonial-card__author-info">
                  <span className="testimonial-card__name">Mert Y.</span>
                  <span className="testimonial-card__role">İstanbul · Mühendis</span>
                </div>
              </div>
            </div>

            <div className="testimonial-card">
              <p className="testimonial-card__quote">
                &ldquo;Bütçemin %80&apos;ine ulaştığımda gelen akıllı e-posta uyarısı sayesinde ceza tarifesine girmekten son anda kurtulduk. Gerçekten hayat kurtarıyor.&rdquo;
              </p>
              <div className="testimonial-card__author">
                <ApplianceCharacter type="AIR_CONDITIONER" state="approved" />
                <div className="testimonial-card__author-info">
                  <span className="testimonial-card__name">Selin K.</span>
                  <span className="testimonial-card__role">İzmir · Mimar</span>
                </div>
              </div>
            </div>

            <div className="testimonial-card">
              <p className="testimonial-card__quote">
                &ldquo;Cihazların şirin karakterlerle anlık durumunu izlemek harika bir fikir. Çocuklar bile evdeki gereksiz lambaları kapatmaya başladı!&rdquo;
              </p>
              <div className="testimonial-card__author">
                <ApplianceCharacter type="TELEVISION" state="active" />
                <div className="testimonial-card__author-info">
                  <span className="testimonial-card__name">Ahmet T.</span>
                  <span className="testimonial-card__role">Ankara · Öğretmen</span>
                </div>
              </div>
            </div>
          </div>
        </section>

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
