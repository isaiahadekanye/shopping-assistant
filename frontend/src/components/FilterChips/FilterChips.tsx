import type { Filters } from '../../types';
import { filterLabel } from '../../utils/format';

interface Props {
  filters: Filters;
  onRemove: (key: string) => void;
  /** Only the assistant's top pick is shown; removing the "Top pick" chip shows every product. */
  topPickOnly?: boolean;
  /** How many other products are hidden behind the top pick. */
  hiddenCount?: number;
  onShowAll?: () => void;
  disabled?: boolean;
}

const chip = 'flex items-center gap-1 rounded-full bg-blue-50 px-3 py-1 text-sm dark:bg-slate-800';
const removeButton =
  'px-1 text-base leading-none text-slate-500 hover:text-slate-900 disabled:opacity-50 dark:hover:text-white';

/** Shows the filters behind the current results; removing one asks the assistant to refine. */
export function FilterChips({ filters, onRemove, topPickOnly, hiddenCount = 0, onShowAll, disabled }: Props) {
  const entries = Object.entries(filters);
  if (entries.length === 0 && !topPickOnly) return null;
  return (
    <ul className="mb-3.5 flex flex-wrap gap-2" aria-label="Active filters">
      {topPickOnly && (
        <li className={`${chip} font-medium`}>
          <span>Top pick{hiddenCount > 0 && ` · ${hiddenCount} more`}</span>
          <button type="button" aria-label={`Show all ${hiddenCount + 1} products`} onClick={onShowAll} className={removeButton}>
            ×
          </button>
        </li>
      )}
      {entries.map(([key, value]) => (
        <li key={key} className={chip}>
          <span>{filterLabel(key, value)}</span>
          {key !== 'query' && (
            <button
              type="button"
              aria-label={`Remove ${key.replace('_', ' ')} filter`}
              onClick={() => onRemove(key)}
              disabled={disabled}
              className={removeButton}
            >
              ×
            </button>
          )}
        </li>
      ))}
    </ul>
  );
}
