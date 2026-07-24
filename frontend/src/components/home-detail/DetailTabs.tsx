import { useRef, type KeyboardEvent } from 'react';

export type HomeDetailTab = 'overview' | 'appliances' | 'analytics' | 'insights';

interface DetailTabsProps {
  activeTab: HomeDetailTab;
  idPrefix: string;
  anomalyCount: number;
  onChange: (tab: HomeDetailTab) => void;
}

const tabs: Array<{ id: HomeDetailTab; label: string }> = [
  { id: 'overview', label: 'Genel bakış' },
  { id: 'appliances', label: 'Cihazlar' },
  { id: 'analytics', label: 'Analitik' },
  { id: 'insights', label: 'Öneriler & olaylar' },
];

export function DetailTabs({
  activeTab,
  idPrefix,
  anomalyCount,
  onChange,
}: DetailTabsProps) {
  const tabRefs = useRef<Array<HTMLButtonElement | null>>([]);

  const handleKeyDown = (
    event: KeyboardEvent<HTMLButtonElement>,
    currentIndex: number,
  ) => {
    let nextIndex: number | undefined;
    if (event.key === 'ArrowRight') nextIndex = (currentIndex + 1) % tabs.length;
    if (event.key === 'ArrowLeft') {
      nextIndex = (currentIndex - 1 + tabs.length) % tabs.length;
    }
    if (event.key === 'Home') nextIndex = 0;
    if (event.key === 'End') nextIndex = tabs.length - 1;
    if (nextIndex === undefined) return;

    event.preventDefault();
    const nextTab = tabs[nextIndex];
    onChange(nextTab.id);
    tabRefs.current[nextIndex]?.focus();
  };

  return (
    <div className="detail-tabs" role="tablist" aria-label="Ev detayı bölümleri">
      {tabs.map((tab, index) => {
        const selected = tab.id === activeTab;
        const label =
          tab.id === 'appliances' && anomalyCount > 0
            ? `${tab.label} (${anomalyCount} uyarı)`
            : tab.label;
        return (
          <button
            className={`detail-tab${selected ? ' is-active' : ''}`}
            id={`${idPrefix}-tab-${tab.id}`}
            key={tab.id}
            type="button"
            role="tab"
            aria-selected={selected}
            aria-controls={`${idPrefix}-panel-${tab.id}`}
            tabIndex={selected ? 0 : -1}
            ref={(node) => {
              tabRefs.current[index] = node;
            }}
            onClick={() => onChange(tab.id)}
            onKeyDown={(event) => handleKeyDown(event, index)}
          >
            {label}
          </button>
        );
      })}
    </div>
  );
}
