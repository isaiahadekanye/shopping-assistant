import { useEffect, useRef, useState, type FormEvent } from 'react';
import type { ChatMessage } from '../../types';

export const STARTERS = [
  'Show me running shoes under $120',
  "I'm looking for orange running shorts in size small",
  'I need a gift for someone who loves hiking',
];

interface Props {
  messages: ChatMessage[];
  loading: boolean;
  onSend: (text: string) => void;
}

export function ChatPanel({ messages, loading, onSend }: Props) {
  const [draft, setDraft] = useState('');
  const endRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    endRef.current?.scrollIntoView?.({ behavior: 'smooth' });
  }, [messages, loading]);

  function submit(e: FormEvent) {
    e.preventDefault();
    const text = draft.trim();
    if (!text || loading) return;
    onSend(text);
    setDraft('');
  }

  return (
    <section className="flex min-h-0 flex-col border-b border-slate-200 bg-white md:border-r md:border-b-0 dark:border-slate-800 dark:bg-slate-900" aria-label="Chat">
      <div className="flex flex-1 flex-col gap-2.5 overflow-y-auto p-4" aria-live="polite">
        {messages.length === 0 && (
          <div className="flex flex-col gap-2 text-slate-500 dark:text-slate-400">
            <p>Hi! Tell me what you're shopping for. Try:</p>
            {STARTERS.map((s) => (
              <button key={s} type="button" className="rounded-lg border border-slate-200 bg-blue-50 px-3 py-2 text-left text-slate-800 hover:bg-blue-100 disabled:opacity-50 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-100" onClick={() => onSend(s)} disabled={loading}>
                {s}
              </button>
            ))}
          </div>
        )}
        {messages.map((m) => (
          <div
            key={m.id}
            data-testid={`msg-${m.role}`}
            role={m.error ? 'alert' : undefined}
            className={`max-w-[90%] whitespace-pre-wrap rounded-xl px-3 py-2.5 leading-relaxed ${
              m.role === 'user'
                ? 'self-end bg-blue-600 text-white'
                : 'self-start border border-slate-200 bg-slate-50 dark:border-slate-700 dark:bg-slate-800'
            } ${m.error ? 'text-red-700 dark:text-red-400' : ''}`}
          >
            {m.text}
          </div>
        ))}
        {loading && (
          <div className="self-start rounded-xl px-3 py-2.5 italic text-slate-500" role="status">
            Searching the catalog…
          </div>
        )}
        <div ref={endRef} />
      </div>
      <form className="flex gap-2 border-t border-slate-200 p-3 dark:border-slate-800" onSubmit={submit}>
        <input
          className="flex-1 rounded-lg border border-slate-300 bg-slate-50 px-3 py-2.5 outline-none focus:border-blue-500 dark:border-slate-700 dark:bg-slate-800"
          aria-label="Message"
          placeholder="Ask for something, or refine: “anything cheaper?”"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          maxLength={2000}
        />
        <button
          type="submit"
          disabled={loading || !draft.trim()}
          className="rounded-lg bg-blue-600 px-4 text-white hover:bg-blue-700 disabled:opacity-50"
        >
          Send
        </button>
      </form>
    </section>
  );
}
