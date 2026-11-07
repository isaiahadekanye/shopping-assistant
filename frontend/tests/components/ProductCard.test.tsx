import { render, screen } from '@testing-library/react';
import { ProductCard } from '../../src/components/ProductCard/ProductCard';
import { product } from '../fixtures';

describe('ProductCard', () => {
  it('shows catalog facts: brand, title, price, rating, colour and size', () => {
    render(<ProductCard product={product()} />);
    expect(screen.getByText('Stride')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: "Men's Road Running Shoes" })).toBeInTheDocument();
    expect(screen.getByText('$89.99')).toBeInTheDocument();
    expect(screen.getByText('★ 4.5 (1,200)')).toBeInTheDocument();
    expect(screen.getByText('Black/White · Size 10')).toBeInTheDocument();
    expect(screen.getByRole('img')).toHaveAttribute('alt', "Men's Road Running Shoes");
    expect(screen.queryByRole('link')).not.toBeInTheDocument(); // the catalog has no product URLs
  });

  it('says when data is missing instead of inventing it', () => {
    render(
      <ProductCard
        product={product({ price: null, rating: null, brand: null, imageUrl: null, color: null, size: null })}
      />,
    );
    expect(screen.getByText('Price not listed')).toBeInTheDocument();
    expect(screen.getByText('No ratings')).toBeInTheDocument();
    expect(screen.getByText('No image')).toBeInTheDocument();
    expect(screen.queryByText(/Size/)).not.toBeInTheDocument();
  });
});
