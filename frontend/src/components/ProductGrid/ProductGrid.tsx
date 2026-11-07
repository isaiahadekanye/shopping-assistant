import type { Product } from '../../types';
import { ProductCard } from '../ProductCard/ProductCard';

export function ProductGrid({ products }: { products: Product[] }) {
  if (products.length === 0) {
    return (
      <div className="rounded-xl border border-dashed border-slate-300 p-10 text-center text-slate-500 dark:border-slate-700" role="status">
        Products the assistant recommends will appear here.
      </div>
    );
  }
  return (
    <section className="grid grid-cols-[repeat(auto-fill,minmax(150px,1fr))] gap-3.5 md:grid-cols-[repeat(auto-fill,minmax(200px,1fr))]" aria-label="Recommended products">
      {products.map((p) => (
        <ProductCard key={p.id} product={p} />
      ))}
    </section>
  );
}
