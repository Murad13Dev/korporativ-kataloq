import { useCallback, useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import {
  CheckCheck,
  Loader2,
  MessageCircle,
  Send,
  UserCheck,
  WifiOff,
  Zap,
} from 'lucide-react';
import {
  Badge,
  Button,
  Card,
  EmptyState,
  InlineLoader,
  Input,
  PageHeader,
  Select,
  type BadgeTone,
} from '../components/ui';
import { useToast } from '@/components/ui/Toast';
import { useAdminAuth } from '../auth/adminAuth';
import { ApiError, apiClient, getData, getPage, patchData, postData } from '@/lib/api';
import { onConnectionChange, subscribe, topics } from '@/lib/stomp';
import { formatRelative } from '@/lib/format';
import type { ChatMessage } from '@/hooks/useChat';

type Status = 'OPEN' | 'PENDING' | 'RESOLVED' | 'CLOSED';

interface InboxRow {
  id: number;
  displayName: string;
  contactEmail: string | null;
  subject: string | null;
  status: Status;
  statusLabel: string;
  priority: 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';
  assignedAdminId: number | null;
  assignedAdminName: string | null;
  lastMessagePreview: string | null;
  lastMessageAt: string | null;
  unreadByAdmin: number;
  registeredCustomer: boolean;
  createdAt: string;
}

interface ConversationDetail {
  id: number;
  channelKey: string;
  displayName: string;
  contactEmail: string | null;
  contactPhone: string | null;
  subject: string | null;
  status: Status;
  statusLabel: string;
  priority: 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';
  customerId: number | null;
  assignedAdminId: number | null;
  assignedAdminName: string | null;
  sourceUrl: string | null;
  messages: ChatMessage[];
  createdAt: string;
}

interface QuickReply {
  id: number;
  code: string;
  title: string;
  body: string;
}

interface InboxEvent {
  type: 'NEW_MESSAGE' | 'UPDATED';
  conversationId: number;
  conversation: InboxRow;
  message: ChatMessage | null;
}

const STATUS_TONE: Record<Status, BadgeTone> = {
  OPEN: 'success',
  PENDING: 'warning',
  RESOLVED: 'info',
  CLOSED: 'neutral',
};

/**
 * Support console.
 *
 * A two-pane inbox: threads on the left, the open conversation on the right. Both stay
 * live over one STOMP subscription to the admin topic — polling an inbox that must feel
 * instant would mean a request every second per operator.
 */
export function ChatPage() {
  const queryClient = useQueryClient();
  const toast = useToast();
  const currentUser = useAdminAuth((state) => state.user);

  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [status, setStatus] = useState<string>('');
  const [search, setSearch] = useState('');
  const [mineOnly, setMineOnly] = useState(false);
  const [draft, setDraft] = useState('');
  const [connected, setConnected] = useState(false);
  const [quickRepliesOpen, setQuickRepliesOpen] = useState(false);

  const messagesEnd = useRef<HTMLDivElement>(null);

  const inbox = useQuery({
    queryKey: ['admin', 'chat', 'inbox', { status, search, mineOnly }],
    queryFn: () =>
      getPage<InboxRow>('/admin/chat/conversations', {
        status: status || undefined,
        search: search || undefined,
        assignedAdminId: mineOnly ? currentUser?.id : undefined,
        size: 50,
      }),
    placeholderData: (previous) => previous,
  });

  const conversation = useQuery({
    queryKey: ['admin', 'chat', 'conversation', selectedId],
    queryFn: () => getData<ConversationDetail>(`/admin/chat/conversations/${selectedId}`),
    enabled: selectedId !== null,
  });

  const quickReplies = useQuery({
    queryKey: ['admin', 'chat', 'quick-replies'],
    queryFn: () => getData<QuickReply[]>('/admin/chat/quick-replies'),
    staleTime: 30 * 60 * 1000,
  });

  const refreshInbox = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'chat', 'inbox'] });
    queryClient.invalidateQueries({ queryKey: ['admin', 'chat', 'unread'] });
  }, [queryClient]);

  // --- live inbox ---------------------------------------------------------

  useEffect(() => {
    const unsubscribe = subscribe(topics.adminInbox, (payload) => {
      const event = payload as InboxEvent;

      // A message for the thread already on screen is appended in place; anything
      // else just refreshes the list so the ordering and badges stay right.
      if (event.type === 'NEW_MESSAGE' && event.message && event.conversationId === selectedId) {
        queryClient.setQueryData<ConversationDetail | undefined>(
          ['admin', 'chat', 'conversation', selectedId],
          (current) => {
            if (!current) return current;
            if (current.messages.some((existing) => existing.id === event.message!.id)) {
              return current;
            }
            return { ...current, messages: [...current.messages, event.message!] };
          },
        );
      }
      refreshInbox();
    });

    const unsubscribeConnection = onConnectionChange(setConnected);
    return () => {
      unsubscribe();
      unsubscribeConnection();
    };
  }, [selectedId, queryClient, refreshInbox]);

  useEffect(() => {
    messagesEnd.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });
  }, [conversation.data?.messages.length]);

  // Opening a thread is what actually clears its unread count.
  useEffect(() => {
    if (selectedId !== null) {
      void apiClient.post(`/admin/chat/conversations/${selectedId}/read`).then(refreshInbox);
    }
  }, [selectedId, refreshInbox]);

  // --- mutations ----------------------------------------------------------

  const reply = useMutation({
    mutationFn: (message: string) =>
      postData<ChatMessage>(`/admin/chat/conversations/${selectedId}/messages`, { message }),
    onSuccess: (message) => {
      queryClient.setQueryData<ConversationDetail | undefined>(
        ['admin', 'chat', 'conversation', selectedId],
        (current) =>
          current && !current.messages.some((existing) => existing.id === message.id)
            ? { ...current, messages: [...current.messages, message] }
            : current,
      );
      setDraft('');
      refreshInbox();
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const assign = useMutation({
    mutationFn: (adminId: number | null) =>
      patchData<ConversationDetail>(`/admin/chat/conversations/${selectedId}/assign`, { adminId }),
    onSuccess: (detail) => {
      queryClient.setQueryData(['admin', 'chat', 'conversation', selectedId], detail);
      refreshInbox();
      toast.success('Söhbət təyin edildi');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const changeStatus = useMutation({
    mutationFn: (next: Status) =>
      patchData<ConversationDetail>(`/admin/chat/conversations/${selectedId}/status`, {
        status: next,
      }),
    onSuccess: (detail) => {
      queryClient.setQueryData(['admin', 'chat', 'conversation', selectedId], detail);
      refreshInbox();
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const detail = conversation.data;
  const rows = inbox.data?.content ?? [];

  return (
    <>
      <PageHeader
        title="Canlı dəstək"
        description="Saytdan gələn suallar. Yeni mesajlar canlı görünür."
        actions={
          <span
            className={clsx(
              'inline-flex items-center gap-1.5 rounded-full px-3 py-1.5 text-xs font-medium',
              connected
                ? 'bg-[var(--color-success)]/12 text-[var(--color-success)]'
                : 'bg-[var(--color-warning)]/12 text-[var(--color-warning)]',
            )}
          >
            {connected ? (
              <>
                <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-current" />
                Canlı
              </>
            ) : (
              <>
                <WifiOff size={12} aria-hidden="true" />
                Bağlantı yoxdur
              </>
            )}
          </span>
        }
      />

      <div className="grid gap-6 lg:grid-cols-[340px_1fr]">
        {/* ---------------- inbox ---------------- */}
        <Card bodyClassName="p-0" className="lg:max-h-[calc(100vh-12rem)] lg:overflow-hidden">
          <div className="space-y-2 border-b border-[var(--color-border)] p-3">
            <Input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Ad və ya e-poçt"
            />
            <div className="flex gap-2">
              <Select
                value={status}
                onChange={(event) => setStatus(event.target.value)}
                className="flex-1 py-1.5 text-xs"
              >
                <option value="">Hamısı</option>
                <option value="OPEN">Açıq</option>
                <option value="PENDING">Cavab gözləyir</option>
                <option value="RESOLVED">Həll olunub</option>
                <option value="CLOSED">Bağlanıb</option>
              </Select>
              <button
                type="button"
                onClick={() => setMineOnly((current) => !current)}
                className={clsx(
                  'rounded-[var(--radius-button)] px-3 py-1.5 text-xs font-medium transition-colors',
                  mineOnly
                    ? 'bg-[var(--color-primary)] text-[var(--color-primary-contrast)]'
                    : 'border border-[var(--color-border)] text-[var(--color-body)]',
                )}
              >
                Mənim
              </button>
            </div>
          </div>

          <div className="overflow-y-auto lg:max-h-[calc(100vh-19rem)]">
            {inbox.isLoading ? (
              <InlineLoader />
            ) : rows.length === 0 ? (
              <EmptyState
                icon={<MessageCircle size={30} aria-hidden="true" />}
                title="Söhbət yoxdur"
                description="Seçilmiş filtrlərə uyğun söhbət tapılmadı."
              />
            ) : (
              <ul className="divide-y divide-[var(--color-border)]">
                {rows.map((row) => (
                  <li key={row.id}>
                    <button
                      type="button"
                      onClick={() => setSelectedId(row.id)}
                      className={clsx(
                        'w-full px-4 py-3 text-start transition-colors',
                        selectedId === row.id
                          ? 'bg-[var(--color-primary)]/6'
                          : 'hover:bg-[var(--color-muted)]',
                      )}
                    >
                      <div className="flex items-center gap-2">
                        <span
                          className={clsx(
                            'min-w-0 flex-1 truncate text-sm',
                            row.unreadByAdmin > 0
                              ? 'font-semibold text-[var(--color-heading)]'
                              : 'font-medium text-[var(--color-body)]',
                          )}
                        >
                          {row.displayName}
                        </span>
                        {row.unreadByAdmin > 0 && (
                          <span className="flex h-5 min-w-5 items-center justify-center rounded-full bg-[var(--color-sale)] px-1.5 text-[0.625rem] font-bold text-white tabular">
                            {row.unreadByAdmin}
                          </span>
                        )}
                      </div>

                      <p className="mt-0.5 truncate text-xs text-[var(--color-subtle)]">
                        {row.lastMessagePreview ?? '—'}
                      </p>

                      <div className="mt-1.5 flex items-center gap-2">
                        <Badge tone={STATUS_TONE[row.status]}>{row.statusLabel}</Badge>
                        {row.assignedAdminName && (
                          <span className="truncate text-[0.625rem] text-[var(--color-subtle)]">
                            {row.assignedAdminName}
                          </span>
                        )}
                        <span className="ms-auto shrink-0 text-[0.625rem] text-[var(--color-subtle)]">
                          {formatRelative(row.lastMessageAt ?? row.createdAt)}
                        </span>
                      </div>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </Card>

        {/* ---------------- conversation ---------------- */}
        <Card bodyClassName="p-0" className="flex flex-col lg:h-[calc(100vh-12rem)]">
          {!selectedId ? (
            <EmptyState
              icon={<MessageCircle size={34} aria-hidden="true" />}
              title="Söhbət seçin"
              description="Cavab yazmaq üçün soldakı siyahıdan söhbətə klikləyin."
            />
          ) : conversation.isLoading || !detail ? (
            <InlineLoader />
          ) : (
            <>
              {/* header */}
              <div className="flex flex-wrap items-center gap-3 border-b border-[var(--color-border)] px-5 py-3.5">
                <div className="min-w-0 flex-1">
                  <p className="truncate font-semibold text-[var(--color-heading)]">
                    {detail.displayName}
                    {/* A linked customer id is what distinguishes a registered
                        shopper from an anonymous visitor. */}
                    {detail.customerId !== null && (
                      <Badge tone="info" className="ms-2">
                        Qeydiyyatlı
                      </Badge>
                    )}
                  </p>
                  <p className="truncate text-xs text-[var(--color-subtle)]">
                    {detail.contactEmail ?? 'E-poçt yoxdur'}
                    {detail.sourceUrl && ` · ${new URL(detail.sourceUrl).pathname}`}
                  </p>
                </div>

                <Select
                  value={detail.status}
                  onChange={(event) => changeStatus.mutate(event.target.value as Status)}
                  className="max-w-[150px] py-1.5 text-xs"
                >
                  <option value="OPEN">Açıq</option>
                  <option value="PENDING">Cavab gözləyir</option>
                  <option value="RESOLVED">Həll olunub</option>
                  <option value="CLOSED">Bağlanıb</option>
                </Select>

                {detail.assignedAdminId === currentUser?.id ? (
                  <Badge tone="success">
                    <UserCheck size={11} aria-hidden="true" />
                    Sizdə
                  </Badge>
                ) : (
                  <Button
                    variant="secondary"
                    size="sm"
                    loading={assign.isPending}
                    onClick={() => assign.mutate(currentUser?.id ?? null)}
                  >
                    Öz üzərimə götür
                  </Button>
                )}
              </div>

              {/* messages */}
              <div className="flex-1 overflow-y-auto bg-[var(--color-muted)] px-5 py-4">
                <ul className="space-y-3">
                  {detail.messages.map((message) => (
                    <AdminBubble key={message.id} message={message} />
                  ))}
                </ul>
                <div ref={messagesEnd} />
              </div>

              {/* quick replies */}
              {quickRepliesOpen && (quickReplies.data ?? []).length > 0 && (
                <div className="border-t border-[var(--color-border)] bg-[var(--color-canvas)] px-4 py-3">
                  <div className="flex flex-wrap gap-2">
                    {quickReplies.data!.map((entry) => (
                      <button
                        key={entry.id}
                        type="button"
                        onClick={() => {
                          setDraft(entry.body);
                          setQuickRepliesOpen(false);
                        }}
                        title={entry.body}
                        className="rounded-full border border-[var(--color-border)] px-3 py-1.5 text-xs font-medium text-[var(--color-body)] transition-colors hover:border-[var(--color-primary)] hover:text-[var(--color-primary)]"
                      >
                        {entry.title}
                      </button>
                    ))}
                  </div>
                </div>
              )}

              {/* composer */}
              {detail.status === 'CLOSED' ? (
                <div className="border-t border-[var(--color-border)] px-5 py-4 text-center">
                  <p className="mb-2 text-sm text-[var(--color-subtle)]">
                    Bu söhbət bağlanıb.
                  </p>
                  <Button variant="secondary" size="sm" onClick={() => changeStatus.mutate('OPEN')}>
                    Yenidən aç
                  </Button>
                </div>
              ) : (
                <div className="flex items-end gap-2 border-t border-[var(--color-border)] p-3">
                  <Button
                    variant="ghost"
                    size="sm"
                    title="Hazır cavablar"
                    onClick={() => setQuickRepliesOpen((current) => !current)}
                    icon={<Zap size={16} aria-hidden="true" />}
                  />

                  <label htmlFor="admin-chat-input" className="sr-only">
                    Cavabınız
                  </label>
                  <textarea
                    id="admin-chat-input"
                    rows={1}
                    value={draft}
                    onChange={(event) => {
                      setDraft(event.target.value);
                      event.target.style.height = 'auto';
                      event.target.style.height = `${Math.min(event.target.scrollHeight, 120)}px`;
                    }}
                    onKeyDown={(event) => {
                      if (event.key === 'Enter' && !event.shiftKey) {
                        event.preventDefault();
                        if (draft.trim()) reply.mutate(draft.trim());
                      }
                    }}
                    placeholder="Cavabınızı yazın…  (Enter göndərir, Shift+Enter yeni sətir)"
                    maxLength={4000}
                    className="max-h-32 min-h-[2.5rem] flex-1 resize-none rounded-[var(--radius-input)] border border-[var(--color-border)] bg-[var(--color-canvas)] px-3 py-2.5 text-sm outline-none transition-colors focus:border-[var(--color-primary)]"
                  />

                  <button
                    type="button"
                    onClick={() => draft.trim() && reply.mutate(draft.trim())}
                    disabled={reply.isPending || !draft.trim()}
                    aria-label="Göndər"
                    className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[var(--radius-button)] bg-[var(--color-primary)] text-[var(--color-primary-contrast)] transition-colors hover:bg-[var(--color-primary-hover)] disabled:opacity-40"
                  >
                    {reply.isPending ? (
                      <Loader2 size={16} className="animate-spin" aria-hidden="true" />
                    ) : (
                      <Send size={16} aria-hidden="true" />
                    )}
                  </button>
                </div>
              )}
            </>
          )}
        </Card>
      </div>
    </>
  );
}

function AdminBubble({ message }: { message: ChatMessage }) {
  if (message.messageType === 'SYSTEM_EVENT') {
    return (
      <li className="text-center">
        <span className="inline-block rounded-full bg-[var(--color-border)]/60 px-3 py-1 text-[0.6875rem] text-[var(--color-body)]">
          {message.message}
        </span>
      </li>
    );
  }

  // The operator's own side is on the right, mirroring what the visitor sees inverted.
  const fromAdmin = message.senderType === 'ADMIN';
  // The auto-suggested FAQ reply sits on the visitor's side (it isn't the operator
  // speaking) but still needs its own label — otherwise it reads as something the
  // customer typed themselves, which is actively misleading in a support transcript.
  const fromBot = message.senderType === 'BOT';

  return (
    <li className={clsx('flex', fromAdmin ? 'justify-end' : 'justify-start')}>
      <div className="max-w-[70%]">
        {(fromAdmin || fromBot) && message.senderName && (
          <p
            className={clsx(
              'mb-0.5 text-[0.6875rem] font-medium text-[var(--color-subtle)]',
              fromAdmin ? 'pe-1 text-end' : 'ps-1',
            )}
          >
            {message.senderName}
          </p>
        )}

        <div
          className={clsx(
            'rounded-2xl px-3.5 py-2.5 text-sm leading-relaxed',
            fromAdmin
              ? 'rounded-ee-sm bg-[var(--color-primary)] text-[var(--color-primary-contrast)]'
              : fromBot
                ? 'rounded-es-sm bg-[var(--color-primary)]/8 text-[var(--color-heading)] shadow-sm'
                : 'rounded-es-sm bg-[var(--color-surface)] text-[var(--color-heading)] shadow-sm',
          )}
        >
          {message.mediaUrl && (
            <img
              src={message.mediaUrl}
              alt=""
              loading="lazy"
              className="mb-1.5 max-h-56 rounded-[var(--radius-button)] object-cover"
            />
          )}
          <span className="whitespace-pre-wrap break-words">{message.message}</span>
        </div>

        <p
          className={clsx(
            'mt-0.5 flex items-center gap-1 text-[0.625rem] text-[var(--color-subtle)]',
            fromAdmin ? 'justify-end pe-1' : 'ps-1',
          )}
        >
          {new Date(message.createdAt).toLocaleTimeString('az-AZ', {
            hour: '2-digit',
            minute: '2-digit',
          })}
          {fromAdmin && message.read && (
            <CheckCheck size={11} className="text-[var(--color-success)]" aria-hidden="true" />
          )}
        </p>
      </div>
    </li>
  );
}
