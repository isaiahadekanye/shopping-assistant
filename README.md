# Shopping Assistant

A chat assistant for recommended fashion products. Shoppers ask for things ("running shoes under $120"), refine them ("anything cheaper?", "only blue ones"), and see real catalog products as cards.

- **Backend:** Java 21, Spring Boot 3.5, Spring Data JPA, H2 in-memory database (`backend/`)
- **Frontend:** React 19, TypeScript, Vite, Tailwind CSS (`frontend/`)
- **LLM:** OpenAI Chat Completions with tool calling

## Quick start

Prerequisites: JDK 21+, Maven 3.9+, Node 20+. Put `products.json` in the repo root.

1. Set `openai.api-key` and `openai.model` in `backend/src/main/resources/application.properties`. For reasoning models (e.g. gpt-6-luna) keep `openai.reasoning-effort=none`, since they reject tools otherwise. Don't commit your key.

2. Start the backend:
   ```bash
   cd backend && mvn spring-boot:run      # http://localhost:8080
   ```
   It loads `products.json` into memory on every start, which takes a few seconds.
3. Start the frontend:
   ```bash
   cd frontend && npm install && npm run dev   # http://localhost:5173 (proxies /api to :8080)
   ```

Every setting lives in [application.properties](backend/src/main/resources/application.properties): the OpenAI URL, key, model and timeout, the agent's step and history limits, and the catalog path and search sizes.

## Tests

```bash
cd backend  && mvn test     # 35 tests: ingest cleaning, search, chat agent, controller
cd frontend && npm test     # 40 tests: components, useChat hook, API client, formatters, full chat flow
```

No test calls a real LLM. The agent tests script the model's replies with Mockito.

## How it works

```
React UI ─POST /api/chat─▶ ChatController ─▶ ChatService (agent loop, max 5 steps)
                                               ├─ OpenAiClient ──▶ /chat/completions
                                               └─ ShoppingTools
                                                    ├─ search_products ─▶ ProductService ─▶ ProductRepository (H2)
                                                    └─ show_products   (only ids a search returned)
products.json ──(streamed + cleaned on startup by CatalogIngestService)──▶ H2
```

- **The model decides what to search for. Code supplies the facts.** Keywords, category, colour, price and sort become a JPA Specification, so the model never writes queries. The products in each reply are loaded from the database by id, so prices and images always come from the catalog.
- **Grounding:** `show_products` rejects any id the model didn't get from a search. Search results are deduplicated by title, so colour variants of one product appear once.
- **Top pick:** `show_products` can name one `recommended` product. The UI shows only that card plus a chip to reveal the rest.
- **Follow-ups:** each session (a `ChatSession` row in H2, saved after every message) keeps the conversation, the last search's filters and the ids of the products on screen, stored as JSON columns. The filters are added to the system prompt, so "anything cheaper?" refines the previous search. The UI shows them as removable chips.
- **Token use:** the UI keeps and scrolls the whole chat, but OpenAI gets a compact version.
  - After each turn, the server keeps only the shopper's message and the final reply. Tool calls and search results, which are most of the tokens, are dropped.
  - The system prompt restates the current filters and a one-line summary of each product on screen. So "cheaper?" or "is the second one leather?" still work without re-sending old search results.
  - Only the last `chat.max-history-turns` exchanges (default 10) are sent.
- **Search** (`ProductService.search`, one JPA Specification):
  - Every keyword must start a word in the title, brand or category, so "men" doesn't match "women". A plural "s" is trimmed, so "shoes" also matches "shoe".
  - The optional `category` (e.g. "running shoes") must appear in the store's category path. Keywords alone can't tell running shoes from shoelaces: sorted by price, the cheapest "running shoe" keyword matches are all laces. The category filter fixes that.
  - Results are sorted by review count, or by price or rating when asked.
  - Tool results include each product's category, so the model can skip off-target items.
  - A tight price filter returns an honest "no match".


## API

| Method | Path | Notes |
|---|---|---|
| POST | `/api/chat` | `{sessionId?, message}` returns `{sessionId, message, products[], activeFilters, recommendedId}`. When `recommendedId` is set, the UI shows only that product, with a "Top pick" chip that reveals the rest |
| DELETE | `/api/chat/{sessionId}` | Reset a conversation |

