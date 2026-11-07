import { render, screen, within } from '@testing-library/react';
import { product } from '../fixtures';
import { ProductGrid } from '../../src/components/ProductGrid/ProductGrid';

describe('ProductGrid', () => {
  it('shows an empty state before any recommendations', () => {
    render(<ProductGrid products={[]} />);

    expect(screen.getByRole('status')).toHaveTextContent('Products the assistant recommends will appear here.');
  });

  it('renders one card per product in order', () => {
    render(<ProductGrid products={[product({ id: 'A', title: 'First' }), product({ id: 'B', title: 'Second' })]} />);

    const cards = within(screen.getByRole('region', { name: 'Recommended products' })).getAllByRole('article');
    expect(cards.map((c) => within(c).getByRole('heading').textContent)).toEqual(['First', 'Second']);
  });
});
