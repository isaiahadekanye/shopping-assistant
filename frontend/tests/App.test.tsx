import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from '../src/App';
import { chatResponse, jsonResponse, product } from './fixtures';

function bodyOf(call: unknown[]) {
  return JSON.parse((call[1] as RequestInit).body as string);
}

describe('App', () => {
  it('sends a message and shows the reply, product cards and filters', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(chatResponse()));
    vi.stubGlobal('fetch', fetchMock);
    render(<App />);

    await userEvent.type(screen.getByLabelText('Message'), 'Show me running shoes under $120{enter}');

    expect(await screen.findByText('Here are some running shoes under $120.')).toBeInTheDocument();
    expect(screen.getByTestId('msg-user')).toHaveTextContent('Show me running shoes under $120');
    const grid = screen.getByRole('region', { name: 'Recommended products' });
    expect(within(grid).getAllByRole('article')).toHaveLength(2);
    expect(within(grid).getByText('$59.50')).toBeInTheDocument();
    expect(screen.getByText('Under $120')).toBeInTheDocument();
    expect(bodyOf(fetchMock.mock.calls[0])).toEqual({ message: 'Show me running shoes under $120', sessionId: null });
  });

  it('keeps the session for follow-up refinements', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(chatResponse()))
      .mockResolvedValueOnce(
        jsonResponse(
          chatResponse({
            message: 'Here is a cheaper pair.',
            products: [product({ id: 'RS3', title: 'Budget Runner', price: 49 })],
            activeFilters: { query: 'running shoes', max_price: 59 },
          }),
        ),
      );
    vi.stubGlobal('fetch', fetchMock);
    render(<App />);

    await userEvent.type(screen.getByLabelText('Message'), 'running shoes{enter}');
    await screen.findByText('Here are some running shoes under $120.');
    await userEvent.type(screen.getByLabelText('Message'), 'Anything cheaper?{enter}');

    expect(await screen.findByText('Here is a cheaper pair.')).toBeInTheDocument();
    expect(bodyOf(fetchMock.mock.calls[1]).sessionId).toBe('session-1');
    expect(screen.getAllByRole('article')).toHaveLength(1);
    expect(screen.getByText('Under $59')).toBeInTheDocument();
  });

  it('starter suggestions send a message', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(chatResponse()));
    vi.stubGlobal('fetch', fetchMock);
    render(<App />);

    await userEvent.click(screen.getByRole('button', { name: /orange running shorts/i }));
    await screen.findByText('Here are some running shoes under $120.');
    expect(bodyOf(fetchMock.mock.calls[0]).message).toMatch(/orange running shorts in size small/);
  });

  it('removing a filter chip asks the assistant to refine', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(chatResponse()));
    vi.stubGlobal('fetch', fetchMock);
    render(<App />);

    await userEvent.type(screen.getByLabelText('Message'), 'running shoes{enter}');
    await screen.findByText('Under $120');
    await userEvent.click(screen.getByRole('button', { name: 'Remove max price filter' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    expect(bodyOf(fetchMock.mock.calls[1]).message).toBe('Remove the maximum price filter (Under $120) and search again without it.');
  });

  it('shows errors from the server in the chat', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ error: 'No LLM is configured.' }, 503)));
    render(<App />);
    await userEvent.type(screen.getByLabelText('Message'), 'hi{enter}');
    const msg = await screen.findByText('No LLM is configured.');
    expect(msg).toHaveAttribute('role', 'alert');
  });

  it('disables sending while a reply is pending', async () => {
    let resolve!: (r: Response) => void;
    vi.stubGlobal('fetch', vi.fn().mockReturnValue(new Promise<Response>((r) => (resolve = r))));
    render(<App />);
    await userEvent.type(screen.getByLabelText('Message'), 'hi{enter}');

    expect(screen.getByText('Searching the catalog…')).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Message'), 'again');
    expect(screen.getByRole('button', { name: 'Send' })).toBeDisabled();

    resolve(jsonResponse(chatResponse({ products: [] })));
    expect(await screen.findByText('Products the assistant recommends will appear here.')).toBeInTheDocument();
  });

  it('new chat clears the conversation and resets the server session', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(chatResponse()))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);
    render(<App />);

    await userEvent.type(screen.getByLabelText('Message'), 'running shoes{enter}');
    await screen.findByText('Here are some running shoes under $120.');
    await userEvent.click(screen.getByRole('button', { name: 'New chat' }));

    await waitFor(() => expect(screen.queryByRole('article')).not.toBeInTheDocument());
    expect(fetchMock.mock.calls[1]).toEqual(['/api/chat/session-1', { method: 'DELETE' }]);
    expect(screen.getByText(/Tell me what you're shopping for/)).toBeInTheDocument();
  });
});
