import { ChatPanel, FilterChips, ProductGrid } from './components';
import { useChat } from './hooks/useChat';

export default function App() {
  const { messages, products, filters, topPickOnly, hiddenCount, loading, send, removeFilter, showAllProducts, newChat } =
    useChat();

  return (
    <div className="flex h-screen flex-col bg-slate-50 text-slate-900 dark:bg-slate-950 dark:text-slate-100">
      <header className="flex items-center justify-between border-b border-slate-200 bg-white px-5 py-3 dark:border-slate-800 dark:bg-slate-900">
        <h1 className="text-lg font-semibold">Shopping Assistant</h1>
        <button type="button" onClick={newChat} disabled={loading}
          className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-100 disabled:opacity-50 dark:border-slate-700 dark:hover:bg-slate-800"
        >
          New chat
        </button>
      </header>
      <main className="grid min-h-0 flex-1 grid-rows-[55vh_auto] md:grid-cols-[minmax(320px,420px)_1fr] md:grid-rows-1">
        <ChatPanel messages={messages} loading={loading} onSend={send} />
        <section className="overflow-y-auto p-4 md:px-5" aria-label="Results">
          <FilterChips
            filters={filters}
            onRemove={removeFilter}
            topPickOnly={topPickOnly}
            hiddenCount={hiddenCount}
            onShowAll={showAllProducts}
            disabled={loading}
          />
          <ProductGrid products={products} />
        </section>
      </main>
    </div>
  );
}
