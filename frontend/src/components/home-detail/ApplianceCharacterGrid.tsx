import { memo, useCallback } from 'react';
import { AlertTriangle, Clock3, RadioTower, Zap } from 'lucide-react';
import { ApplianceCharacter } from '../../characters';
import type { AppliancePresentation } from '../../presentation/appliancePresentation';
import { applianceTypeLabels, formatPower } from '../../utils/format';
import { InlineSpinner } from '../PageStates';

interface ApplianceCharacterGridProps {
  items: AppliancePresentation[];
  selectedApplianceId: number | null;
  isRefreshing: boolean;
  onSelect: (applianceId: number) => void;
}

interface ApplianceCharacterCardProps {
  item: AppliancePresentation;
  selected: boolean;
  onSelect: (applianceId: number) => void;
}

const ApplianceCharacterCard = memo(function ApplianceCharacterCard({
  item,
  selected,
  onSelect,
}: ApplianceCharacterCardProps) {
  const { appliance } = item;
  const descriptionId = `appliance-character-description-${appliance.applianceId}`;
  const telemetryPanelId = `appliance-telemetry-panel-${appliance.applianceId}`;
  const activate = useCallback(
    () => onSelect(appliance.applianceId),
    [appliance.applianceId, onSelect],
  );

  return (
    <article
      className={[
        'appliance-character-card',
        `appliance-character-card--${item.tone}`,
        selected ? 'is-selected' : '',
      ]
        .filter(Boolean)
        .join(' ')}
      data-selected={selected}
      data-freshness={item.freshness}
      role="listitem"
    >
      <div className="appliance-character-card__visual">
        <ApplianceCharacter
          type={appliance.type}
          state={item.characterState}
          size="md"
          selected={selected}
          interactive
          accessibleLabel={`${item.accessibleLabel}. Telemetri ayrıntılarını aç.`}
          accessibleDescriptionId={descriptionId}
          accessibleControlsId={telemetryPanelId}
          onActivate={activate}
        />
        {(item.anomalous ||
          item.consecutiveViolation ||
          item.thresholdExceeded) && (
          <span
            className="appliance-character-card__warning"
            title={item.warningTitle}
            aria-hidden="true"
          >
            <AlertTriangle size={15} />
          </span>
        )}
      </div>

      <div className="appliance-character-card__copy">
        <strong>{appliance.name}</strong>
        <span>{applianceTypeLabels[appliance.type]}</span>
      </div>

      <div className="appliance-character-card__reading">
        <span>
          <Zap aria-hidden="true" size={13} /> Anlık güç
        </span>
        <strong>{formatPower(appliance.currentPowerWatts)}</strong>
      </div>

      <span
        className={`appliance-character-card__status appliance-character-card__status--${item.tone}`}
      >
        {item.freshness !== 'live' && <Clock3 aria-hidden="true" size={12} />}
        {item.statusLabel}
      </span>
      <span className="sr-only" id={descriptionId}>
        {item.warningTitle
          ? `${item.warningTitle}. ${item.warningDescription ?? ''}`
          : 'Cihazın canlı ölçümü tanımlı güvenli sınırlar içinde.'}
      </span>
    </article>
  );
});

export const ApplianceCharacterGrid = memo(function ApplianceCharacterGrid({
  items,
  selectedApplianceId,
  isRefreshing,
  onSelect,
}: ApplianceCharacterGridProps) {
  if (!items.length) {
    return isRefreshing ? (
      <div className="section-loading">
        <InlineSpinner label="Cihazlar yükleniyor" />
      </div>
    ) : (
      <div className="inline-empty">
        <RadioTower aria-hidden="true" size={22} />
        Henüz canlı cihaz verisi bulunmuyor.
      </div>
    );
  }

  return (
    <div
      className="appliance-character-grid"
      aria-label="Evdeki canlı cihazlar"
      role="list"
    >
      {items.map((item) => {
        const selected =
          item.appliance.applianceId === selectedApplianceId;
        return (
          <ApplianceCharacterCard
            item={item}
            selected={selected}
            onSelect={onSelect}
            key={item.appliance.applianceId}
          />
        );
      })}
    </div>
  );
});
