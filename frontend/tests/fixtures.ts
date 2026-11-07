import type { ChatResponse, Product } from '../src/types';

export function product(overrides: Partial<Product> = {}): Product {
  return {
    id: 'RS1',
    title: "Men's Road Running Shoes",
    brand: 'Stride',
    price: 89.99,
    rating: 4.5,
    ratingCount: 1200,
    color: 'Black/White',
    size: '10',
    material: null,
    imageUrl: 'https://img.example/RS1.jpg',
    features: [],
    ...overrides,
  };
}

export function chatResponse(overrides: Partial<ChatResponse> = {}): ChatResponse {
  return {
    sessionId: 'session-1',
    message: 'Here are some running shoes under $120.',
    products: [product(), product({ id: 'RS3', title: 'Cushioned Running Shoes', price: 59.5 })],
    activeFilters: { query: 'running shoes', max_price: 120 },
    recommendedId: null,
    ...overrides,
  };
}

export function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}
