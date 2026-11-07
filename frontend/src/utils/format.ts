export function formatPrice(price: number | null): string {
  return price == null ? 'Price not listed' : `$${price.toFixed(2)}`;
}

const SORT_LABELS: Record<string, string> = {
  price_low_to_high: 'Price: low to high',
  price_high_to_low: 'Price: high to low',
  rating: 'Top rated',
};

/** Human-readable label for one active search filter (keys match the backend's activeFilters). */
export function filterLabel(key: string, value: string | number): string {
  switch (key) {
    case 'query':
      return `"${value}"`;
    case 'category':
    case 'color':
      return String(value).charAt(0).toUpperCase() + String(value).slice(1);
    case 'min_price':
      return `Over $${value}`;
    case 'max_price':
      return `Under $${value}`;
    case 'sort':
      return SORT_LABELS[String(value)] ?? String(value);
    default:
      return `${key}: ${value}`;
  }
}

/** Plain-language name used when asking the assistant to drop a filter. */
export const FILTER_NAMES: Record<string, string> = {
  category: 'category',
  color: 'colour',
  min_price: 'minimum price',
  max_price: 'maximum price',
  sort: 'sort order',
};
