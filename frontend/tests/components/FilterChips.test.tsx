import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { FilterChips } from '../../src/components/FilterChips/FilterChips';

describe('FilterChips', () => {
  it('renders readable labels for each active filter', () => {
    render(
      <FilterChips
        filters={{ query: 'running shorts', color: 'orange', min_price: 10, max_price: 50, sort: 'price_low_to_high' }}
        onRemove={() => {}}
      />,
    );
    for (const label of ['"running shorts"', 'Orange', 'Over $10', 'Under $50', 'Price: low to high']) {
      expect(screen.getByText(label)).toBeInTheDocument();
    }
  });

  it('calls onRemove with the filter key; the keyword chip is not removable', async () => {
    const onRemove = vi.fn();
    render(<FilterChips filters={{ query: 'shoes', color: 'blue' }} onRemove={onRemove} />);
    await userEvent.click(screen.getByRole('button', { name: 'Remove color filter' }));
    expect(onRemove).toHaveBeenCalledWith('color');
    expect(screen.getAllByRole('button')).toHaveLength(1);
  });

  it('shows a removable "Top pick" chip that reveals all products', async () => {
    const onShowAll = vi.fn();
    render(<FilterChips filters={{}} onRemove={() => {}} topPickOnly hiddenCount={2} onShowAll={onShowAll} />);
    expect(screen.getByText('Top pick · 2 more')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Show all 3 products' }));
    expect(onShowAll).toHaveBeenCalled();
  });

  it('renders nothing when there are no filters', () => {
    const { container } = render(<FilterChips filters={{}} onRemove={() => {}} />);
    expect(container).toBeEmptyDOMElement();
  });
});
