import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import { Check, MessageSquare, Star, Trash2, X } from 'lucide-react';
import {
  Badge,
  Button,
  Card,
  EmptyState,
  PageHeader,
  Select,
  TableSkeleton,
  Textarea,
  type BadgeTone,
} from '../components/ui';
import { useToast } from '@/components/ui/Toast';
import { ApiError, deleteRequest, getPage, patchData, putData } from '@/lib/api';
import { formatDateTime } from '@/lib/format';

type ReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

interface ReviewRow {
  id: number;
  productId: number;
  productName: string;
  productSlug: string;
  customerId: number | null;
  authorName: string;
  rating: number;
  title: string | null;
  body: string | null;
  status: ReviewStatus;
  verifiedPurchase: boolean;
  adminReply: string | null;
  adminReplyAt: string | null;
  createdAt: string;
}

const STATUS_TONE: Record<ReviewStatus, BadgeTone> = {
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
};

const STATUS_LABEL: Record<ReviewStatus, string> = {
  PENDING: 'Gözləyir',
  APPROVED: 'Təsdiqlənib',
  REJECTED: 'Rədd edilib',
};

/**
 * Review moderation queue.
 *
 * Everything a review needs — approve, reject, reply, delete — happens inline in the row.
 * A review has none of a contact request's back-and-forth (no thread, no assignment), so
 * the master-detail split that page uses would just be an extra click here for no payoff.
 */
export function ReviewsPage() {
  const queryClient = useQueryClient();
  const toast = useToast();

  const [status, setStatus] = useState<string>('PENDING');
  const [page, setPage] = useState(0);
  const [replyingId, setReplyingId] = useState<number | null>(null);
  const [replyDraft, setReplyDraft] = useState('');

  const reviews = useQuery({
    queryKey: ['admin', 'reviews', { status, page }],
    queryFn: () => getPage<ReviewRow>('/admin/reviews', { status: status || undefined, page, size: 20 }),
    placeholderData: (previous) => previous,
  });

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['admin', 'reviews'] });

  const moderate = useMutation({
    mutationFn: ({ id, next }: { id: number; next: ReviewStatus }) =>
      patchData<ReviewRow>(`/admin/reviews/${id}/status`, { status: next }),
    onSuccess: refresh,
    onError: (error: ApiError) => toast.error(error.message),
  });

  const reply = useMutation({
    mutationFn: ({ id, reply }: { id: number; reply: string }) =>
      putData<ReviewRow>(`/admin/reviews/${id}/reply`, { reply }),
    onSuccess: () => {
      setReplyingId(null);
      refresh();
      toast.success('Cavab göndərildi');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const remove = useMutation({
    mutationFn: (id: number) => deleteRequest(`/admin/reviews/${id}`),
    onSuccess: () => {
      refresh();
      toast.success('Rəy silindi');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const rows = reviews.data;

  return (
    <>
      <PageHeader
        title="Rəylər"
        description="Məhsul rəyləri yalnız təsdiqlədikdən sonra saytda görünür"
      />

      <Card
        bodyClassName="p-0"
        actions={
          <Select
            value={status}
            onChange={(event) => {
              setStatus(event.target.value);
              setPage(0);
            }}
            className="max-w-[190px]"
          >
            <option value="">Hamısı</option>
            <option value="PENDING">Gözləyir</option>
            <option value="APPROVED">Təsdiqlənib</option>
            <option value="REJECTED">Rədd edilib</option>
          </Select>
        }
      >
        {reviews.isLoading ? (
          <TableSkeleton rows={6} columns={4} />
        ) : !rows || rows.content.length === 0 ? (
          <EmptyState
            icon={<Star size={32} aria-hidden="true" />}
            title="Rəy yoxdur"
            description="Seçilmiş filtrə uyğun rəy tapılmadı."
          />
        ) : (
          <ul className="divide-y divide-[var(--color-border)]">
            {rows.content.map((review) => (
              <li key={review.id} className="p-5">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <div className="flex items-center gap-0.5" aria-hidden="true">
                        {[1, 2, 3, 4, 5].map((star) => (
                          <Star
                            key={star}
                            size={14}
                            className={
                              star <= review.rating
                                ? 'fill-[var(--color-warning)] text-[var(--color-warning)]'
                                : 'text-[var(--color-border)]'
                            }
                          />
                        ))}
                      </div>
                      <span className="font-medium text-[var(--color-heading)]">{review.authorName}</span>
                      <Badge tone={STATUS_TONE[review.status]}>{STATUS_LABEL[review.status]}</Badge>
                      {review.verifiedPurchase && <Badge tone="info">Təsdiqlənmiş alış</Badge>}
                    </div>
                    <Link
                      to={`/admin/products/${review.productId}`}
                      className="mt-1 inline-block text-xs font-medium text-[var(--color-primary)] hover:underline"
                    >
                      {review.productName}
                    </Link>
                  </div>
                  <span className="shrink-0 text-xs text-[var(--color-subtle)] tabular">
                    {formatDateTime(review.createdAt)}
                  </span>
                </div>

                {review.title && (
                  <p className="mt-2 text-sm font-semibold text-[var(--color-heading)]">{review.title}</p>
                )}
                {review.body && (
                  <p className="mt-1 whitespace-pre-wrap text-sm text-[var(--color-body)]">{review.body}</p>
                )}

                {review.adminReply && (
                  <div className="mt-3 rounded-[var(--radius-button)] bg-[var(--color-muted)] p-3 text-sm">
                    <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-[var(--color-subtle)]">
                      Sizin cavabınız
                    </p>
                    <p className="text-[var(--color-body)]">{review.adminReply}</p>
                  </div>
                )}

                {replyingId === review.id && (
                  <div className="mt-3">
                    <Textarea
                      rows={2}
                      value={replyDraft}
                      onChange={(event) => setReplyDraft(event.target.value)}
                      placeholder="Müştəriyə açıq cavab yazın"
                    />
                    <div className="mt-2 flex gap-2">
                      <Button
                        size="sm"
                        loading={reply.isPending}
                        disabled={!replyDraft.trim()}
                        onClick={() => reply.mutate({ id: review.id, reply: replyDraft.trim() })}
                      >
                        Göndər
                      </Button>
                      <Button variant="ghost" size="sm" onClick={() => setReplyingId(null)}>
                        Ləğv et
                      </Button>
                    </div>
                  </div>
                )}

                <div className="mt-3 flex flex-wrap gap-2">
                  {review.status !== 'APPROVED' && (
                    <Button
                      size="sm"
                      variant="secondary"
                      icon={<Check size={14} aria-hidden="true" />}
                      onClick={() => moderate.mutate({ id: review.id, next: 'APPROVED' })}
                    >
                      Təsdiqlə
                    </Button>
                  )}
                  {review.status !== 'REJECTED' && (
                    <Button
                      size="sm"
                      variant="secondary"
                      icon={<X size={14} aria-hidden="true" />}
                      onClick={() => moderate.mutate({ id: review.id, next: 'REJECTED' })}
                    >
                      Rədd et
                    </Button>
                  )}
                  {replyingId !== review.id && (
                    <Button
                      size="sm"
                      variant="ghost"
                      icon={<MessageSquare size={14} aria-hidden="true" />}
                      onClick={() => {
                        setReplyingId(review.id);
                        setReplyDraft(review.adminReply ?? '');
                      }}
                    >
                      {review.adminReply ? 'Cavabı redaktə et' : 'Cavab yaz'}
                    </Button>
                  )}
                  <Button
                    size="sm"
                    variant="ghost"
                    className={clsx('text-[var(--color-danger)]')}
                    icon={<Trash2 size={14} aria-hidden="true" />}
                    onClick={() => {
                      if (window.confirm('Bu rəy tamamilə silinsin?')) {
                        remove.mutate(review.id);
                      }
                    }}
                  >
                    Sil
                  </Button>
                </div>
              </li>
            ))}
          </ul>
        )}

        {rows && rows.totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-[var(--color-border)] px-5 py-3">
            <span className="text-xs text-[var(--color-subtle)]">{rows.totalElements} rəy</span>
            <div className="flex gap-2">
              <Button variant="secondary" size="sm" disabled={rows.first} onClick={() => setPage((p) => p - 1)}>
                Əvvəlki
              </Button>
              <Button variant="secondary" size="sm" disabled={rows.last} onClick={() => setPage((p) => p + 1)}>
                Növbəti
              </Button>
            </div>
          </div>
        )}
      </Card>
    </>
  );
}
