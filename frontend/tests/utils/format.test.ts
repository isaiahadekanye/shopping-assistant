import { filterLabel, formatPrice } from '../../src/utils/format';

describe('formatPrice', () => {
  it('formats to two decimals', () => {
    expect(formatPrice(59.5)).toBe('$59.50');
  });

  it('says so when the catalog has no price', () => {
    expect(formatPrice(null)).toBe('Price not listed');
  });
});

describe('filterLabel', () => {
  it.each([
    ['query', 'running shoes', '"running shoes"'],
    ['color', 'blue', 'Blue'],
    ['category', 'running shoes', 'Running shoes'],
    ['max_price', 120, 'Under $120'],
    ['min_price', 50, 'Over $50'],
    ['sort', 'price_low_to_high', 'Price: low to high'],
    ['unknown', 'x', 'unknown: x'],
  ])('%s=%s -> %s', (key, value, expected) => {
    expect(filterLabel(key, value)).toBe(expected);
  });
});
