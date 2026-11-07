import { useCallback, useRef, useState } from 'react';
import { resetSession, sendMessage } from '../services/chatApi';
import type { ChatMessage, Filters, Product } from '../types';
import { FILTER_NAMES, filterLabel } from '../utils/format';

/**
 * Conversation state and actions. Components call this hook; only this hook calls the API.
 */
export function useChat() {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [filters, setFilters] = useState<Filters>({});
  const [recommendedId, setRecommendedId] = useState<string | null>(null);
  const [showAll, setShowAll] = useState(false);
  const [loading, setLoading] = useState(false);
  const sessionId = useRef<string | null>(null);
  const nextId = useRef(1);

  const add = useCallback((role: ChatMessage['role'], text: string, error = false) => {
    setMessages((prev) => [...prev, { id: nextId.current++, role, text, error }]);
  }, []);

  const send = useCallback(
    async (text: string) => {
      add('user', text);
      setLoading(true);
      try {
        const res = await sendMessage(text, sessionId.current);
        sessionId.current = res.sessionId;
        add('assistant', res.message);
        setProducts(res.products);
        setFilters(res.activeFilters ?? {});
        setRecommendedId(res.recommendedId ?? null);
        setShowAll(false);
      } catch (e) {
        add('assistant', e instanceof Error ? e.message : 'Something went wrong.', true);
      } finally {
        setLoading(false);
      }
    },
    [add],
  );

  /** Filters live server-side, so removing one is a follow-up message the assistant acts on. */
  const removeFilter = useCallback(
    (key: string) => {
      const name = FILTER_NAMES[key] ?? key;
      const label = filterLabel(key, filters[key]);
      // Hide the chip right away; the reply brings the updated filters a few seconds later.
      setFilters(({ [key]: _removed, ...rest }) => rest);
      // Spelled out so the model also drops the filter's words from its query ("hiking boots" -> "hiking").
      const scope = key === 'category' ? `, so results aren't limited to ${label.toLowerCase()}` : '';
      return send(`Remove the ${name} filter (${label}) and search again without it${scope}.`);
    },
    [filters, send],
  );

  const newChat = useCallback(async () => {
    if (sessionId.current) {
      await resetSession(sessionId.current).catch(() => undefined);
    }
    sessionId.current = null;
    setMessages([]);
    setProducts([]);
    setFilters({});
    setRecommendedId(null);
    setShowAll(false);
  }, []);

  // When the assistant names a top pick, show only it until the shopper removes the "Top pick" chip.
  const topPickOnly = recommendedId != null && !showAll && products.length > 1 && products.some((p) => p.id === recommendedId);
  const visibleProducts = topPickOnly ? products.filter((p) => p.id === recommendedId) : products;
  const hiddenCount = products.length - visibleProducts.length;
  const showAllProducts = useCallback(() => setShowAll(true), []);

  return {
    messages, products: visibleProducts, filters, topPickOnly, hiddenCount,
    loading, send, removeFilter, showAllProducts, newChat,
  };
}
