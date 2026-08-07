import { useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import { Check, Image as ImageIcon, Upload, X } from 'lucide-react';
import { Button, EmptyState, InlineLoader, Input, Select } from './ui';
import { useToast } from '@/components/ui/Toast';
import { ApiError, apiClient, getPage } from '@/lib/api';

export interface PickedMedia {
  id: number;
  publicUrl: string;
  originalName: string;
  altText: string | null;
  mimeType: string;
  fileSize: number;
  mediaType: 'IMAGE' | 'DOCUMENT' | 'VIDEO' | 'OTHER';
}

/**
 * Modal media picker.
 *
 * Uploading happens inside the dialog rather than sending the operator to the media
 * library and back — the common case is "I have the photo right here", and a round trip
 * through another screen loses the product they were editing.
 */
export function MediaPicker({
  open,
  onClose,
  onSelect,
  multiple = false,
  type = 'IMAGE',
  excludeIds = [],
}: {
  open: boolean;
  onClose: () => void;
  onSelect: (media: PickedMedia[]) => void;
  multiple?: boolean;
  type?: 'IMAGE' | 'DOCUMENT' | 'VIDEO';
  excludeIds?: number[];
}) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const fileInput = useRef<HTMLInputElement>(null);
  const dialogRef = useRef<HTMLDivElement>(null);

  const [search, setSearch] = useState('');
  const [folder, setFolder] = useState('');
  const [selected, setSelected] = useState<Map<number, PickedMedia>>(new Map());

  const media = useQuery({
    queryKey: ['admin', 'media', 'picker', { search, folder, type }],
    queryFn: () =>
      getPage<PickedMedia>('/admin/media', {
        search: search || undefined,
        folder: folder || undefined,
        type,
        page: 0,
        size: 60,
      }),
    enabled: open,
  });

  const upload = useMutation({
    mutationFn: async (files: FileList) => {
      const uploaded: PickedMedia[] = [];
      for (const file of Array.from(files)) {
        const formData = new FormData();
        formData.append('file', file);
        formData.append('folder', type === 'DOCUMENT' ? 'documents' : type === 'VIDEO' ? 'videos' : 'products');
        const { data } = await apiClient.post('/admin/media/upload', formData, {
          headers: { 'Content-Type': 'multipart/form-data' },
        });
        uploaded.push(data.data);
      }
      return uploaded;
    },
    onSuccess: (files) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'media'] });
      // A freshly uploaded file is almost always the one being looked for, so it is
      // pre-selected instead of leaving the operator to hunt for it in the grid.
      setSelected((current) => {
        const next = multiple ? new Map(current) : new Map<number, PickedMedia>();
        files.forEach((file) => next.set(file.id, file));
        return next;
      });
      toast.success(`${files.length} fayl yükləndi`);
    },
    onError: (error: ApiError) => toast.error(error.message),
  });

  // Reset between openings so a previous selection does not leak into the next use.
  useEffect(() => {
    if (open) {
      setSelected(new Map());
      setSearch('');
    }
  }, [open]);

  useEffect(() => {
    if (!open) return;

    const handleKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose();
      }
    };
    document.addEventListener('keydown', handleKey);
    document.body.style.overflow = 'hidden';

    return () => {
      document.removeEventListener('keydown', handleKey);
      document.body.style.overflow = '';
    };
  }, [open, onClose]);

  if (!open) {
    return null;
  }

  const toggle = (file: PickedMedia) => {
    setSelected((current) => {
      const next = multiple ? new Map(current) : new Map<number, PickedMedia>();
      if (current.has(file.id) && multiple) {
        next.delete(file.id);
      } else if (current.has(file.id) && !multiple) {
        return new Map();
      } else {
        next.set(file.id, file);
      }
      return next;
    });
  };

  const confirm = () => {
    onSelect([...selected.values()]);
    onClose();
  };

  const files = (media.data?.content ?? []).filter((file) => !excludeIds.includes(file.id));

  return (
    <div
      className="fixed inset-0 z-[100] flex items-center justify-center bg-black/50 p-4"
      role="dialog"
      aria-modal="true"
      aria-label="Media seçimi"
      onClick={(event) => {
        if (event.target === event.currentTarget) {
          onClose();
        }
      }}
    >
      <div
        ref={dialogRef}
        className="flex max-h-[85vh] w-full max-w-4xl flex-col overflow-hidden rounded-[var(--radius-card)] bg-[var(--color-surface)] shadow-[var(--shadow-popover)]"
      >
        {/* ---------------- header ---------------- */}
        <div className="flex items-center justify-between border-b border-[var(--color-border)] px-5 py-4">
          <h2 className="font-semibold text-[var(--color-heading)]">
            {type === 'DOCUMENT' ? 'Sənəd seç' : type === 'VIDEO' ? 'Video seç' : 'Şəkil seç'}
          </h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="Bağla"
            className="rounded p-1 text-[var(--color-subtle)] hover:text-[var(--color-heading)]"
          >
            <X size={18} aria-hidden="true" />
          </button>
        </div>

        {/* ---------------- toolbar ---------------- */}
        <div className="flex flex-wrap items-center gap-3 border-b border-[var(--color-border)] px-5 py-3">
          <Input
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Fayl adı"
            className="max-w-xs"
          />
          <Select value={folder} onChange={(event) => setFolder(event.target.value)} className="max-w-[160px]">
            <option value="">Bütün qovluqlar</option>
            <option value="products">Məhsullar</option>
            <option value="general">Ümumi</option>
            <option value="documents">Sənədlər</option>
            <option value="videos">Videolar</option>
          </Select>

          <input
            ref={fileInput}
            type="file"
            multiple={multiple}
            accept={type === 'DOCUMENT' ? 'application/pdf' : type === 'VIDEO' ? 'video/*' : 'image/*'}
            className="hidden"
            onChange={(event) => {
              if (event.target.files?.length) {
                upload.mutate(event.target.files);
                event.target.value = '';
              }
            }}
          />
          <Button
            variant="secondary"
            size="sm"
            className="ms-auto"
            loading={upload.isPending}
            icon={<Upload size={14} aria-hidden="true" />}
            onClick={() => fileInput.current?.click()}
          >
            Yüklə
          </Button>
        </div>

        {/* ---------------- grid ---------------- */}
        <div className="min-h-0 flex-1 overflow-y-auto p-5">
          {media.isLoading ? (
            <InlineLoader />
          ) : files.length === 0 ? (
            <EmptyState
              icon={<ImageIcon size={32} aria-hidden="true" />}
              title="Fayl tapılmadı"
              description="Yeni fayl yükləyin və ya axtarışı dəyişin."
            />
          ) : (
            <div className="grid grid-cols-3 gap-3 sm:grid-cols-4 lg:grid-cols-6">
              {files.map((file) => {
                const isSelected = selected.has(file.id);
                return (
                  <button
                    key={file.id}
                    type="button"
                    onClick={() => toggle(file)}
                    aria-pressed={isSelected}
                    className={clsx(
                      'group relative overflow-hidden rounded-[var(--radius-button)] border-2 text-start transition-colors',
                      isSelected
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
                      ) : file.mediaType === 'VIDEO' ? (
                        <video src={file.publicUrl} muted className="h-full w-full object-cover" />
                      ) : (
                        <div className="flex h-full items-center justify-center text-xs font-medium text-[var(--color-subtle)]">
                          {file.mimeType.split('/')[1]?.toUpperCase()}
                        </div>
                      )}
                    </div>

                    {isSelected && (
                      <span className="absolute end-1.5 top-1.5 flex h-5 w-5 items-center justify-center rounded-full bg-[var(--color-primary)] text-[var(--color-primary-contrast)]">
                        <Check size={12} strokeWidth={3} aria-hidden="true" />
                      </span>
                    )}

                    <p className="truncate px-2 py-1.5 text-xs text-[var(--color-body)]">
                      {file.originalName}
                    </p>
                  </button>
                );
              })}
            </div>
          )}
        </div>

        {/* ---------------- footer ---------------- */}
        <div className="flex items-center justify-between border-t border-[var(--color-border)] px-5 py-3">
          <span className="text-xs text-[var(--color-subtle)]">
            {selected.size > 0
              ? `${selected.size} fayl seçildi`
              : multiple
                ? 'Bir və ya bir neçə fayl seçin'
                : 'Bir fayl seçin'}
          </span>
          <div className="flex gap-2">
            <Button variant="secondary" size="sm" onClick={onClose}>
              Ləğv et
            </Button>
            <Button size="sm" disabled={selected.size === 0} onClick={confirm}>
              Seç
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
