import { useCallback, useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient, getData, getVisitorToken, postData } from '@/lib/api';
import { onConnectionChange, subscribe, topics } from '@/lib/stomp';

export type SenderType = 'CUSTOMER' | 'ADMIN' | 'SYSTEM' | 'BOT';

export interface ChatMessage {
  id: number;
  conversationId: number;
  senderType: SenderType;
  senderId: number | null;
  senderName: string | null;
  message: string | null;
  messageType: 'TEXT' | 'IMAGE' | 'FILE' | 'PRODUCT' | 'ORDER' | 'SYSTEM_EVENT';
  mediaUrl: string | null;
  read: boolean;
  createdAt: string;
}

export interface Conversation {
  id: number;
  channelKey: string;
  status: 'OPEN' | 'PENDING' | 'RESOLVED' | 'CLOSED';
  statusLabel: string;
  subject: string | null;
  assigned: boolean;
  assignedAdminName: string | null;
  unread: number;
  messages: ChatMessage[];
  createdAt: string;
}

const CHAT_KEY = ['chat', 'conversation'] as const;

/**
 * Storefront chat.
 *
 * History comes from REST and live updates from STOMP. Both paths write into the same
 * React Query cache, and incoming messages are de-duplicated by id — the sender receives
 * its own message back over the socket, and showing it twice is the classic chat bug.
 */
export function useChat(enabled = true) {
  const queryClient = useQueryClient();
  const [connected, setConnected] = useState(false);
  const visitorToken = useRef<string>(getVisitorToken());

  const conversation = useQuery({
    queryKey: CHAT_KEY,
    queryFn: () => getData<Conversation | null>('/public/chat'),
    enabled,
    staleTime: 30 * 1000,
    retry: 1,
  });

  const appendMessage = useCallback(
    (message: ChatMessage) => {
      queryClient.setQueryData<Conversation | null>(CHAT_KEY, (current) => {
        if (!current) {
          return current;
        }
        if (current.messages.some((existing) => existing.id === message.id)) {
          return current;
        }
        return { ...current, messages: [...current.messages, message] };
      });
    },
    [queryClient],
  );

  // Subscribing on the visitor's own token means the server needs no per-connection
  // authorisation check for this channel.
  useEffect(() => {
    if (!enabled) {
      return;
    }
    const unsubscribeMessages = subscribe(
      topics.conversation(visitorToken.current),
      (payload) => appendMessage(payload as ChatMessage),
    );
    const unsubscribeConnection = onConnectionChange(setConnected);

    return () => {
      unsubscribeMessages();
      unsubscribeConnection();
    };
  }, [enabled, appendMessage]);

  const start = useMutation({
    mutationFn: (request?: {
      name?: string;
      email?: string;
      message?: string;
      productId?: number;
    }) =>
      postData<Conversation>('/public/chat', {
        ...request,
        sourceUrl: window.location.href,
      }),
    onSuccess: (data) => queryClient.setQueryData(CHAT_KEY, data),
  });

  const send = useMutation({
    mutationFn: ({ conversationId, message }: { conversationId: number; message: string }) =>
      postData<ChatMessage>(`/public/chat/${conversationId}/messages`, { message }),
    // The socket echo may arrive first or second; appendMessage de-duplicates either way.
    onSuccess: (message) => appendMessage(message),
  });

  const markRead = useCallback(
    (conversationId: number) => {
      void apiClient.post(`/public/chat/${conversationId}/read`);
      queryClient.setQueryData<Conversation | null>(CHAT_KEY, (current) =>
        current ? { ...current, unread: 0 } : current,
      );
    },
    [queryClient],
  );

  return {
    conversation: conversation.data ?? null,
    isLoading: conversation.isLoading,
    connected,
    start,
    send,
    markRead,
  };
}

/** Whether the widget should be mounted at all. */
export function useChatAvailability() {
  return useQuery({
    queryKey: ['chat', 'availability'],
    queryFn: () => getData<{ enabled: boolean }>('/public/chat/availability'),
    staleTime: 30 * 60 * 1000,
    retry: false,
  });
}
