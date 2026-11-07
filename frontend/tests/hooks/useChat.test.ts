import { act, renderHook } from '@testing-library/react';
import { resetSession, sendMessage } from '../../src/services/chatApi';
import { chatResponse, product } from '../fixtures';
import { useChat } from '../../src/hooks/useChat';

vi.mock('../../src/services/chatApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../src/services/chatApi')>()),
  sendMessage: vi.fn(),
  resetSession: vi.fn(),
}));

const sendMock = vi.mocked(sendMessage);
const resetMock = vi.mocked(resetSession);

describe('useChat', () => {
  it('records both sides of the conversation and the latest results', async () => {
    sendMock.mockResolvedValue(chatResponse());
    const { result } = renderHook(() => useChat());

    await act(() => result.current.send('running shoes'));

    expect(result.current.messages.map((m) => [m.role, m.text])).toEqual([
      ['user', 'running shoes'],
      ['assistant', 'Here are some running shoes under $120.'],
    ]);
    expect(result.current.products).toHaveLength(2);
    expect(result.current.filters).toEqual({ query: 'running shoes', max_price: 120 });
    expect(result.current.loading).toBe(false);
  });

  it('reuses the session id for follow-ups', async () => {
    sendMock.mockResolvedValue(chatResponse({ sessionId: 'abc' }));
    const { result } = renderHook(() => useChat());

    await act(() => result.current.send('running shoes'));
    await act(() => result.current.send('anything cheaper?'));

    expect(sendMock).toHaveBeenNthCalledWith(1, 'running shoes', null);
    expect(sendMock).toHaveBeenNthCalledWith(2, 'anything cheaper?', 'abc');
  });

  it('shows API failures as an error message and keeps previous results', async () => {
    sendMock.mockResolvedValueOnce(chatResponse()).mockRejectedValueOnce(new Error('LLM not configured'));
    const { result } = renderHook(() => useChat());

    await act(() => result.current.send('running shoes'));
    await act(() => result.current.send('only blue'));

    const last = result.current.messages.at(-1);
    expect(last).toMatchObject({ role: 'assistant', text: 'LLM not configured', error: true });
    expect(result.current.products).toHaveLength(2);
  });

  it('turns removing a filter into a follow-up message', async () => {
    sendMock.mockResolvedValue(chatResponse());
    const { result } = renderHook(() => useChat());
    await act(() => result.current.send('running shoes'));

    await act(() => result.current.removeFilter('max_price'));

    expect(sendMock).toHaveBeenLastCalledWith(
      'Remove the maximum price filter (Under $120) and search again without it.',
      'session-1',
    );
  });

  it('asks to drop the category words too when the category chip is removed', async () => {
    sendMock.mockResolvedValue(chatResponse({ activeFilters: { query: 'hiking boots', category: 'boots' } }));
    const { result } = renderHook(() => useChat());
    await act(() => result.current.send('boots'));

    await act(() => result.current.removeFilter('category'));

    expect(sendMock).toHaveBeenLastCalledWith(
      "Remove the category filter (Boots) and search again without it, so results aren't limited to boots.",
      'session-1',
    );
  });

  it('hides a removed filter chip immediately, before the reply arrives', async () => {
    sendMock.mockResolvedValueOnce(chatResponse());
    const { result } = renderHook(() => useChat());
    await act(() => result.current.send('running shoes'));

    let resolveReply!: (r: ReturnType<typeof chatResponse>) => void;
    sendMock.mockReturnValueOnce(new Promise((r) => (resolveReply = r)));
    let pending!: Promise<void>;
    act(() => {
      pending = result.current.removeFilter('max_price');
    });

    expect(result.current.filters).toEqual({ query: 'running shoes' }); // gone while waiting
    resolveReply(chatResponse({ activeFilters: { query: 'running shoes' } }));
    await act(() => pending);
    expect(result.current.filters).toEqual({ query: 'running shoes' });
  });

  it('shows only the top pick until the shopper asks for all products', async () => {
    sendMock.mockResolvedValue(chatResponse({ recommendedId: 'RS3' }));
    const { result } = renderHook(() => useChat());
    await act(() => result.current.send('running shoes'));

    expect(result.current.topPickOnly).toBe(true);
    expect(result.current.hiddenCount).toBe(1);
    expect(result.current.products.map((p) => p.id)).toEqual(['RS3']);

    act(() => result.current.showAllProducts());

    expect(result.current.topPickOnly).toBe(false);
    expect(result.current.products.map((p) => p.id)).toEqual(['RS1', 'RS3']);
  });

  it('has no top-pick view when only one product is shown', async () => {
    sendMock.mockResolvedValue(chatResponse({ products: [product()], recommendedId: 'RS1' }));
    const { result } = renderHook(() => useChat());
    await act(() => result.current.send('running shoes'));

    expect(result.current.topPickOnly).toBe(false);
    expect(result.current.products).toHaveLength(1);
  });

  it('new chat resets the server session and clears state', async () => {
    sendMock.mockResolvedValue(chatResponse());
    resetMock.mockResolvedValue();
    const { result } = renderHook(() => useChat());
    await act(() => result.current.send('running shoes'));

    await act(() => result.current.newChat());

    expect(resetMock).toHaveBeenCalledWith('session-1');
    expect(result.current.messages).toEqual([]);
    expect(result.current.products).toEqual([]);
    expect(result.current.filters).toEqual({});
  });
});
