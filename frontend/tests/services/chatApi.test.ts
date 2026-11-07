import { ApiError, resetSession, sendMessage } from '../../src/services/chatApi';
import { chatResponse, jsonResponse } from '../fixtures';

describe('api', () => {
  it('posts the message and session id as JSON', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(chatResponse()));
    vi.stubGlobal('fetch', fetchMock);

    const res = await sendMessage('anything cheaper?', 'session-1');

    expect(res.sessionId).toBe('session-1');
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/chat');
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body)).toEqual({ message: 'anything cheaper?', sessionId: 'session-1' });
  });

  it('surfaces the server error message', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ error: 'No LLM is configured.' }, 503)));
    const err = await sendMessage('hi', null).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.message).toBe('No LLM is configured.');
    expect(err.status).toBe(503);
  });

  it('falls back to a generic message for non-JSON errors', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('oops', { status: 500 })));
    await expect(sendMessage('hi', null)).rejects.toThrow('Request failed (500)');
  });

  it('resets a session with DELETE', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);
    await resetSession('a b');
    expect(fetchMock).toHaveBeenCalledWith('/api/chat/a%20b', { method: 'DELETE' });
  });
});
