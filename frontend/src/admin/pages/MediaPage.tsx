import { useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import { Copy, Image as ImageIcon, Trash2, Upload } from 'lucide-react';
import {
  Button,
  Card,
  EmptyState,
  Field,
  Input,
  PageHeader,
  Select,
  InlineLoader,
} from '../components/ui';
import { useToast } from '@/components/ui/Toast';
import { ApiError, apiClient, deleteRequest, getData, getPage, putData } from '@/lib/api';
import { formatDate, formatFileSize, formatNumber } from '@/lib/format';

interface MediaFile {
  id: number;
  originalName: string;
  publicUrl: string;
  mimeType: string;
  fileSize: number;
  mediaType: 'IMAGE' | 'DOCUMENT' | 'VIDEO' | 'OTHER';
  width: number | null;
  height: number | null;
  altText: string | null;
  title: string | null;
  folder: string;
  createdAt: string;
}

export function MediaPage() {
  const queryClient = useQueryClient();
  const toast = useToast();
  const fileInput = useRef<HTMLInputElement>(null);

  const [type, setType] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<MediaFile | null>(null);
  const [altDraft, setAltDraft] = useState('');

  const media = useQuery({
    queryKey: ['admin', 'media', { type, search, page }],
    queryFn: () =>
      getPage<MediaFile>('/admin/media', {
        type: type || undefined,
        search: search || undefined,
        page,
        size: 40,
      }),
    placeholderData: (previous) => previous,
  });

  const stats = useQuery({
    queryKey: ['admin', 'media', 'stats'],
    queryFn: () => getData<{ fileCount: number; totalBytes: number }>('/admin/media/stats'),
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin', 'media'] });

  const upload = useMutation({
    mutationFn: async (files: FileList) => {
      // Uploaded one at a time so a single rejected file does not fail the batch.
      const results: MediaFile[] = [];
      for (const file of Array.from(files)) {
        const formData = new FormData();
        formData.append('file', file);
        const { data } = await apiClient.post('/admin/media/upload', formData, {
          headers: { 'Content-Type': 'multipart/form-data' },
        });
        results.push(data.data);
      }
      return results;
    },
    onSuccess: (files) => {
      invalidate();
      toast.success(`${files.length} fayl yükləndi`);
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const updateMeta = useMutation({
    mutationFn: ({ id, altText }: { id: number; altText: string }) =>
      putData(`/admin/media/${id}`, { altText }),
    onSuccess: () => {
      invalidate();
      toast.success('Məlumat yeniləndi');
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  const remove = useMutation({
    mutationFn: (id: number) => deleteRequest(`/admin/media/${id}`),
    onSuccess: () => {
      invalidate();
      setSelected(null);
      toast.success('Fayl silindi');
    },
    onError: (error: ApiError) => {
      // The server refuses to delete a file that is still referenced; there is
      // deliberately no force-delete, so the message explains rather than offers a bypass.
      toast.error(error.message);
    },
  });

  const files = media.data;

  return (
    <>
      <PageHeader
        title="Media kitabxanası"
        description={
          stats.data
            ? `${formatNumber(stats.data.fileCount)} fayl · ${formatFileSize(stats.data.totalBytes)}`
            : undefined
        }
        actions={
          <>
            <input
              ref={fileInput}
              type="file"
              multiple
              accept="image/*,application/pdf"
              className="hidden"
              onChange={(event) => {
                if (event.target.files?.length) {
                  upload.mutate(event.target.files);
                  event.target.value = '';
                }
              }}
            />
            <Button
              loading={upload.isPending}
              icon={<Upload size={15} aria-hidden="true" />}
              onClick={() => fileInput.current?.click()}
            >
              Fayl yüklə
            </Button>
          </>
        }
      />

      <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
        <Card bodyClassName="p-0">
          <div className="flex flex-wrap gap-3 border-b border-[var(--color-border)] p-4">
            <Input
              value={search}
              onChange={(event) => {
                setSearch(event.target.value);
                setPage(0);
              }}
              placeholder="Fayl adı və ya alt mətn"
              className="max-w-xs"
            />
            <Select
              value={type}
              onChange={(event) => {
                setType(event.target.value);
                setPage(0);
              }}
              className="max-w-[160px]"
            >
              <option value="">Bütün tiplər</option>
              <option value="IMAGE">Şəkillər</option>
              <option value="DOCUMENT">Sənədlər</option>
            </Select>
          </div>

          {media.isLoading ? (
            <InlineLoader />
          ) : !files || files.content.length === 0 ? (
            <EmptyState
              icon={<ImageIcon size={36} aria-hidden="true" />}
              title="Fayl yoxdur"
              description="Məhsul şəkilləri, loqo və sənədləri buraya yükləyin."
              action={
                <Button
                  icon={<Upload size={15} aria-hidden="true" />}
                  onClick={() => fileInput.current?.click()}
                >
                  Fayl yüklə
                </Button>
              }
            />
          ) : (
            <div className="grid grid-cols-2 gap-3 p-4 sm:grid-cols-3 xl:grid-cols-5">
              {files.content.map((file) => (
                <button
                  key={file.id}
                  type="button"
                  onClick={() => {
                    setSelected(file);
                    setAltDraft(file.altText ?? '');
                  }}
                  className={clsx(
                    'group relative overflow-hidden rounded-[var(--radius-button)] border-2 transition-colors',
                    selected?.id === file.id
                      ? 'border-[var(--color-primary)]'
                      : 'border-transparent hover:border-[var(--color-border)]',
                  )}
                >
                  <div className="aspect-square bg-[var(--color-muted)]">
                    {file.mediaType === 'IMAGE' ? (
                      <img
                        src={file.publicUrl}
                        alt={file.altText ?? ''}
                        loading="lazy"
                        className="h-full w-full object-cover"
                      />
                    ) : (
                      <div className="flex h-full items-center justify-center text-xs text-[var(--color-subtle)]">
                        {file.mimeType.split('/')[1]?.toUpperCase()}
                      </div>
                    )}
                  </div>
                  <p className="truncate px-2 py-1.5 text-start text-xs text-[var(--color-body)]">
                    {file.originalName}
                  </p>
                </button>
              ))}
            </div>
          )}

          {files && files.totalPages > 1 && (
            <div className="flex items-center justify-between border-t border-[var(--color-border)] px-5 py-3">
              <span className="text-xs text-[var(--color-subtle)]">
                {formatNumber(files.totalElements)} fayl
              </span>
              <div className="flex gap-2">
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={files.first}
                  onClick={() => setPage((current) => current - 1)}
                >
                  Əvvəlki
                </Button>
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={files.last}
                  onClick={() => setPage((current) => current + 1)}
                >
                  Növbəti
                </Button>
              </div>
            </div>
          )}
        </Card>

        {selected && (
          <Card
            title="Fayl detalı"
            className="lg:sticky lg:top-24 lg:self-start"
            actions={
              <Button variant="ghost" size="sm" onClick={() => setSelected(null)}>
                Bağla
              </Button>
            }
          >
            <div className="mb-4 overflow-hidden rounded-[var(--radius-button)] bg-[var(--color-muted)]">
              {selected.mediaType === 'IMAGE' ? (
                <img
                  src={selected.publicUrl}
                  alt={selected.altText ?? ''}
                  className="max-h-48 w-full object-contain"
                />
              ) : (
                <div className="flex h-32 items-center justify-center text-sm text-[var(--color-subtle)]">
                  {selected.mimeType}
                </div>
              )}
            </div>

            <dl className="mb-4 space-y-1.5 text-xs">
              <div className="flex justify-between gap-3">
                <dt className="text-[var(--color-subtle)]">Fayl</dt>
                <dd className="truncate text-end text-[var(--color-body)]">{selected.originalName}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-[var(--color-subtle)]">Həcm</dt>
                <dd className="text-[var(--color-body)]">{formatFileSize(selected.fileSize)}</dd>
              </div>
              {selected.width && (
                <div className="flex justify-between">
                  <dt className="text-[var(--color-subtle)]">Ölçü</dt>
                  <dd className="text-[var(--color-body)]">
                    {selected.width} × {selected.height} px
                  </dd>
                </div>
              )}
              <div className="flex justify-between">
                <dt className="text-[var(--color-subtle)]">Yükləndi</dt>
                <dd className="text-[var(--color-body)]">{formatDate(selected.createdAt)}</dd>
              </div>
            </dl>

            <Field
              label="Alt mətn"
              htmlFor="altText"
              hint="Ekran oxuyucular və SEO üçün. Şəklin nə göstərdiyini yazın."
            >
              <Input
                id="altText"
                value={altDraft}
                onChange={(event) => setAltDraft(event.target.value)}
              />
            </Field>

            <div className="mt-4 space-y-2">
              <Button
                className="w-full"
                size="sm"
                loading={updateMeta.isPending}
                onClick={() => updateMeta.mutate({ id: selected.id, altText: altDraft })}
              >
                Yadda saxla
              </Button>

              <Button
                variant="secondary"
                size="sm"
                className="w-full"
                icon={<Copy size={14} aria-hidden="true" />}
                onClick={() => {
                  void navigator.clipboard.writeText(selected.publicUrl);
                  toast.success('URL kopyalandı');
                }}
              >
                URL-i kopyala
              </Button>

              <Button
                variant="ghost"
                size="sm"
                className="w-full text-[var(--color-danger)]"
                icon={<Trash2 size={14} aria-hidden="true" />}
                loading={remove.isPending}
                onClick={() => {
                  if (window.confirm('Fayl silinsin?')) {
                    remove.mutate(selected.id);
                  }
                }}
              >
                Sil
              </Button>
            </div>
          </Card>
        )}
      </div>
    </>
  );
}
