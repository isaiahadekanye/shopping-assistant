export interface Product {
  id: string;
  title: string;
  brand: string | null;
  price: number | null;
  rating: number | null;
  ratingCount: number | null;
  color: string | null;
  size: string | null;
  material: string | null;
  imageUrl: string | null;
  features: string[];
}

export type Filters = Record<string, string | number>;

export interface ChatResponse {
  sessionId: string;
  message: string;
  products: Product[];
  activeFilters: Filters;
  /** The assistant's single top pick among products, if it named one. */
  recommendedId: string | null;
}

export interface ChatMessage {
  id: number;
  role: 'user' | 'assistant';
  text: string;
  error?: boolean;
}
