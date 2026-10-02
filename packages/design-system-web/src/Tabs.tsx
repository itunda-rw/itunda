import React from 'react';

export interface TabsProps {
  items: Array<{ id: string; label: string; disabled?: boolean }>;
  value: string;
  onChange: (id: string) => void;
  ariaLabel?: string;
  tabPanelId?: string;
}

export function Tabs({ items, value, onChange, ariaLabel = 'Tabs', tabPanelId }: TabsProps) {
  const refs = React.useRef<Array<HTMLButtonElement | null>>([]);
  const enabledItems = items.filter(item => !item.disabled);
  const activeIndex = Math.max(0, enabledItems.findIndex(item => item.id === value));
  const rovingId = enabledItems[activeIndex]?.id;
  const move = (index: number) => {
    if (!enabledItems.length) return;
    const next = (index + enabledItems.length) % enabledItems.length;
    onChange(enabledItems[next].id);
    requestAnimationFrame(() => refs.current[items.indexOf(enabledItems[next])]?.focus());
  };

  return (
    <div
      className="ids-tabs"
      role="tablist"
      aria-label={ariaLabel}
      aria-orientation="horizontal"
      onKeyDown={(event) => {
        if (!enabledItems.length) return;
        if (event.key === 'ArrowRight' || event.key === 'ArrowDown') { event.preventDefault(); move(activeIndex + 1); }
        if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') { event.preventDefault(); move(activeIndex - 1); }
        if (event.key === 'Home') { event.preventDefault(); move(0); }
        if (event.key === 'End') { event.preventDefault(); move(enabledItems.length - 1); }
      }}
    >
      {items.map((item, index) => {
        const selected = value === item.id;
        const panelId = tabPanelId ? `${tabPanelId}-panel-${item.id}` : undefined;
        const tabId = `${tabPanelId ? `${tabPanelId}-` : ''}tab-${item.id}`;
        return (
          <button
            key={item.id}
            id={tabId}
            ref={node => { refs.current[index] = node; }}
            type="button"
            role="tab"
            aria-selected={selected}
            aria-controls={panelId}
            aria-disabled={item.disabled || undefined}
            disabled={item.disabled}
            tabIndex={item.id === rovingId && !item.disabled ? 0 : -1}
            className={`ids-tab ${selected ? 'ids-tab--active' : ''}`}
            onClick={() => { if (!item.disabled) onChange(item.id); }}
          >
            {item.label}
          </button>
        );
      })}
    </div>
  );
}
