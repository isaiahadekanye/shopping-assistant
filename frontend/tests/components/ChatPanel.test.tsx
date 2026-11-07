import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ChatPanel, STARTERS } from '../../src/components/ChatPanel/ChatPanel';

describe('ChatPanel', () => {
  it('offers starter prompts on an empty conversation', async () => {
    const onSend = vi.fn();
    render(<ChatPanel messages={[]} loading={false} onSend={onSend} />);

    await userEvent.click(screen.getByRole('button', { name: STARTERS[0] }));

    expect(onSend).toHaveBeenCalledWith(STARTERS[0]);
  });

  it('sends the trimmed draft and clears the input', async () => {
    const onSend = vi.fn();
    render(<ChatPanel messages={[]} loading={false} onSend={onSend} />);
    const input = screen.getByLabelText('Message');

    await userEvent.type(input, '  only blue ones  {enter}');

    expect(onSend).toHaveBeenCalledWith('only blue ones');
    expect(input).toHaveValue('');
  });

  it('blocks sending while a reply is loading', () => {
    render(
      <ChatPanel messages={[{ id: 1, role: 'user', text: 'hi' }]} loading onSend={vi.fn()} />,
    );

    expect(screen.getByRole('status')).toHaveTextContent('Searching the catalog…');
    expect(screen.getByRole('button', { name: 'Send' })).toBeDisabled();
  });
});
