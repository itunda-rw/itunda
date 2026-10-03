import React from 'react';

import { Button } from './Button';

export interface EmptyStateAction {
  label: string;
  onClick: () => void;
  variant?: 'primary' | 'secondary';
}

export interface EmptyStateProps {
  title: React.ReactNode;
  message?: React.ReactNode;
  icon?: React.ReactNode;
  actions?: EmptyStateAction[];
  ariaLabel?: string;
}

export function EmptyState({
  title,
  message,
  icon,
  actions = [],
  ariaLabel,
}: EmptyStateProps) {
  const reactId = React.useId();
  const titleId = `ids-empty-state-${reactId}`;
  const actionId = `ids-empty-state-${reactId}-actions`;
  return (
    <section
      className="ids-empty-state"
      aria-label={ariaLabel || undefined}
      aria-labelledby={ariaLabel ? undefined : titleId}
    >
      {icon ? <div className="ids-empty-state__icon" aria-hidden="true">{icon}</div> : null}
      <h2 id={titleId} className="ids-empty-state__title">{title}</h2>
      {message ? <p className="ids-empty-state__message">{message}</p> : null}
      {actions.length ? (
        <div id={actionId} className="ids-empty-state__actions" aria-label="Available actions">
          {actions.slice(0, 2).map((action) => (
            <Button
              key={action.label}
              onClick={action.onClick}
              variant={action.variant === 'primary' ? 'primary' : 'secondary'}
              size="md"
            >
              {action.label}
            </Button>
          ))}
        </div>
      ) : null}
    </section>
  );
}
