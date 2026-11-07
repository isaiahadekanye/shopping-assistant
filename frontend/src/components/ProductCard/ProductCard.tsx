import type { Product } from '../../types';
import { formatPrice } from '../../utils/format';

export function ProductCard({ product }: { product: Product }) {
  const variant = [product.color, product.size && `Size ${product.size}`].filter(Boolean).join(' · ');
  return (
    <article className="flex flex-col overflow-hidden rounded-xl border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900" aria-label={product.title}>
      <div className="flex aspect-square items-center justify-center bg-white">
        {product.imageUrl ? (
          <img className="max-h-full max-w-full object-contain" src={product.imageUrl} alt={product.title} loading="lazy" />
        ) : (
          <div className="text-sm text-slate-500">No image</div>
        )}
      </div>
      <div className="flex flex-col gap-1 p-3 text-sm">
        {product.brand && <div className="text-xs uppercase tracking-wide text-slate-500 dark:text-slate-400">{product.brand}</div>}
        <h3 className="line-clamp-3 font-semibold" title={product.title}>{product.title}</h3>
        <div className="text-base font-bold">{formatPrice(product.price)}</div>
        <div className="text-slate-500 dark:text-slate-400">
          {product.rating != null
            ? `★ ${product.rating.toFixed(1)} (${(product.ratingCount ?? 0).toLocaleString('en-US')})`
            : 'No ratings'}
        </div>
        {variant && <div className="text-slate-500 dark:text-slate-400">{variant}</div>}
      </div>
    </article>
  );
}
