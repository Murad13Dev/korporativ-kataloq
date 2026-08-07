import { useEffect, useRef, useState } from 'react';
import clsx from 'clsx';
import { Loader2, MessageCircle, Send, WifiOff, X } from 'lucide-react';
import { useChat, useChatAvailability, type ChatMessage } from '@/hooks/useChat';
import { useSiteConfig } from '@/hooks/useSiteConfig';

/**
 * Floating support widget.
 *
 * Mounted once in the storefront layout. It renders nothing until chat is confirmed
 * available, and the conversation itself is only fetched after the panel is first
 * opened — a visitor who never clicks it costs one cheap availability call.
 */
export function ChatWidget() {
  const availability = useChatAvailability();
  const { features } = useSiteConfig();
  const [open, setOpen] = useState(false);
  const [everOpened, setEverOpened] = useState(false);

  const enabled = features.chatEnabled && availability.data?.enabled === true;

  const { conversation, isLoading, connected, start, send, markRead } = useChat(
    enabled && everOpened,
  );

  const [draft, setDraft] = useState('');
  const messagesEnd = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  // Keep the newest message in view as the thread grows.
  useEffect(() => {
    if (open) {
      messagesEnd.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });
    }
  }, [conversation?.messages.length, open]);

  // Opening the panel is the moment the visitor actually reads the replies.
  useEffect(() => {
    if (open && conversation && conversation.unread > 0) {
      markRead(conversation.id);
    }
  }, [open, conversation, markRead]);

  useEffect(() => {
    if (open) {
      setEverOpened(true);
      window.setTimeout(() => inputRef.current?.focus(), 150);
    }
  }, [open]);

  if (!enabled) {
    return null;
  }

  const submit = () => {
    const text = draft.trim();
    if (!text) {
      return;
    }

    if (conversation) {
      send.mutate({ conversationId: conversation.id, message: text });
    } else {
      // The first message opens the thread, so the visitor never fills in a form
      // before being allowed to ask a question.
      start.mutate({ message: text });
    }
    setDraft('');
  };

  const unread = conversation?.unread ?? 0;
  const pending = send.isPending || start.isPending;

  return (
    <>
      {/* ---------------- launcher ---------------- */}
      <button
        type="button"
        onClick={() => setOpen((current) => !current)}
        aria-label={open ? 'Dəstək pəncərəsini bağla' : 'Canlı dəstək'}
        aria-expanded={open}
        className={clsx(
          'fixed bottom-5 end-5 z-[90] flex h-14 w-14 items-center justify-center rounded-full',
          'bg-[var(--color-primary)] text-[var(--color-primary-contrast)] shadow-[var(--shadow-popover)]',
          'transition-transform duration-200 hover:scale-105 active:scale-95',
        )}
      >
        {open ? (
          <X size={22} aria-hidden="true" />
        ) : (
          <>
            <MessageCircle size={22} aria-hidden="true" />
            {unread > 0 && (
              <span className="absolute -end-0.5 -top-0.5 flex h-5 min-w-5 items-center justify-center rounded-full bg-[var(--color-sale)] px-1 text-[0.6875rem] font-bold text-white tabular">
                {unread > 9 ? '9+' : unread}
              </span>
            )}
          </>
        )}
      </button>

      {/* ---------------- panel ---------------- */}
      {open && (
        <div
          className={clsx(
            'fixed bottom-24 end-5 z-[90] flex w-[min(23rem,calc(100vw-2.5rem))] flex-col',
            'overflow-hidden rounded-[var(--radius-card)] border border-[var(--color-border)]',
            'bg-[var(--color-surface)] shadow-[var(--shadow-popover)]',
            'animate-[chat-in_200ms_var(--ease-out-quint)]',
          )}
          style={{ maxHeight: 'min(32rem, calc(100vh - 8rem))' }}
          role="dialog"
          aria-label="Canlı dəstək"
        >
          {/* header */}
          <div className="flex items-center gap-3 bg-[var(--color-primary)] px-4 py-3.5 text-[var(--color-primary-contrast)]">
            <span className="flex h-9 w-9 items-center justify-center rounded-full bg-white/20">
              <MessageCircle size={18} aria-hidden="true" />
            </span>
            <div className="min-w-0 flex-1">
              <p className="text-sm font-semibold">Canlı dəstək</p>
              <p className="text-xs opacity-80">
                {conversation?.assigned && conversation.assignedAdminName
                  ? conversation.assignedAdminName
                  : 'Adətən bir neçə dəqiqə ərzində cavab veririk'}
              </p>
            </div>
            <button
              type="button"
              onClick={() => setOpen(false)}
              aria-label="Bağla"
              className="rounded p-1 transition-colors hover:bg-white/15"
            >
              <X size={18} aria-hidden="true" />
            </button>
          </div>

          {/* Honest about the socket: a silent dead connection is worse than a warning. */}
          {everOpened && !connected && (
            <p className="flex items-center gap-2 bg-[var(--color-warning)]/12 px-4 py-2 text-xs text-[var(--color-warning)]">
              <WifiOff size={13} aria-hidden="true" />
              Bağlantı bərpa olunur…
            </p>
          )}

          {/* messages */}
          <div className="flex-1 overflow-y-auto bg-[var(--color-muted)] px-4 py-4">
            {isLoading ? (
              <div className="flex justify-center py-8">
                <Loader2
                  size={20}
                  className="animate-spin text-[var(--color-subtle)]"
                  aria-hidden="true"
                />
              </div>
            ) : !conversation || conversation.messages.length === 0 ? (
              <div className="py-8 text-center">
                <p className="text-sm font-medium text-[var(--color-heading)]">
                  Sizə necə kömək edə bilərik?
                </p>
                <p className="mt-1.5 text-xs text-[var(--color-body)]">
                  Sualınızı yazın — komandamız qısa zamanda cavablandıracaq.
                </p>
              </div>
            ) : (
              <ul className="space-y-3" aria-live="polite">
                {conversation.messages.map((message) => (
                  <MessageBubble key={message.id} message={message} />
                ))}
              </ul>
            )}
            <div ref={messagesEnd} />
          </div>

          {/* composer */}
          {conversation?.status === 'CLOSED' ? (
            <p className="border-t border-[var(--color-border)] px-4 py-4 text-center text-xs text-[var(--color-subtle)]">
              Bu söhbət bağlanıb. Yeni sualınız varsa səhifəni yeniləyin.
            </p>
          ) : (
            <div className="flex items-end gap-2 border-t border-[var(--color-border)] p-3">
              <label htmlFor="chat-input" className="sr-only">
                Mesajınız
              </label>
              <textarea
                id="chat-input"
                ref={inputRef}
                rows={1}
                value={draft}
                onChange={(event) => {
                  setDraft(event.target.value);
                  // Grow with the text, capped so the thread stays visible.
                  event.target.style.height = 'auto';
                  event.target.style.height = `${Math.min(event.target.scrollHeight, 96)}px`;
                }}
                onKeyDown={(event) => {
                  // Enter sends, Shift+Enter breaks the line — the convention every
                  // messaging app has trained people on.
                  if (event.key === 'Enter' && !event.shiftKey) {
                    event.preventDefault();
                    submit();
                  }
                }}
                placeholder="Mesajınızı yazın…"
                maxLength={4000}
                className="max-h-24 min-h-[2.5rem] flex-1 resize-none rounded-[var(--radius-input)] border border-[var(--color-border)] bg-[var(--color-canvas)] px-3 py-2.5 text-sm outline-none transition-colors focus:border-[var(--color-primary)]"
              />
              <button
                type="button"
                onClick={submit}
                disabled={pending || !draft.trim()}
                aria-label="Göndər"
                className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[var(--radius-button)] bg-[var(--color-primary)] text-[var(--color-primary-contrast)] transition-colors hover:bg-[var(--color-primary-hover)] disabled:opacity-40"
              >
                {pending ? (
                  <Loader2 size={16} className="animate-spin" aria-hidden="true" />
                ) : (
                  <Send size={16} aria-hidden="true" />
                )}
              </button>
            </div>
          )}
        </div>
      )}

      <style>{`
        @keyframes chat-in {
          from { opacity: 0; transform: translateY(12px) scale(0.97); }
          to   { opacity: 1; transform: translateY(0) scale(1); }
        }
      `}</style>
    </>
  );
}

function MessageBubble({ message }: { message: ChatMessage }) {
  // System notices are neither side's message and read as interruptions if bubbled.
  if (message.messageType === 'SYSTEM_EVENT') {
    return (
      <li className="text-center">
        <span className="inline-block rounded-full bg-[var(--color-border)]/60 px-3 py-1 text-[0.6875rem] text-[var(--color-body)]">
          {message.message}
        </span>
      </li>
    );
  }

  const fromVisitor = message.senderType === 'CUSTOMER';

  return (
    <li className={clsx('flex', fromVisitor ? 'justify-end' : 'justify-start')}>
      <div className={clsx('max-w-[85%]', fromVisitor && 'items-end')}>
        {!fromVisitor && message.senderName && (
          <p className="mb-0.5 ps-1 text-[0.6875rem] font-medium text-[var(--color-subtle)]">
            {message.senderName}
          </p>
        )}

        <div
          className={clsx(
            'rounded-2xl px-3.5 py-2.5 text-sm leading-relaxed',
            fromVisitor
              ? 'rounded-ee-sm bg-[var(--color-primary)] text-[var(--color-primary-contrast)]'
              : 'rounded-es-sm bg-[var(--color-surface)] text-[var(--color-heading)] shadow-sm',
          )}
        >
          {message.mediaUrl && (
            <img
              src={message.mediaUrl}
              alt=""
              loading="lazy"
              className="mb-1.5 max-h-48 rounded-[var(--radius-button)] object-cover"
            />
          )}
          {/* Rendered as text, never markup — the server strips tags, and this is the
              second line of defence. */}
          <span className="whitespace-pre-wrap break-words">{message.message}</span>
        </div>

        <p
          className={clsx(
            'mt-0.5 text-[0.625rem] text-[var(--color-subtle)]',
            fromVisitor ? 'pe-1 text-end' : 'ps-1',
          )}
        >
          {new Date(message.createdAt).toLocaleTimeString('az-AZ', {
            hour: '2-digit',
            minute: '2-digit',
          })}
        </p>
      </div>
    </li>
  );
}
