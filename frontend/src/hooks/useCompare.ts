import { create } from 'zustand';

const STORAGE_KEY = 'ctl.compare';
export const MAX_COMPARE_ITEMS = 4;

function readSlugs(): string[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    const parsed = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? parsed.filter((slug): slug is string => typeof slug === 'string') : [];
  } catch {
    return [];
  }
}

function persist(slugs: string[]): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(slugs));
}

interface CompareState {
  slugs: string[];
  toggle: (slug: string) => void;
  remove: (slug: string) => void;
  clear: () => void;
}

/**
 * The "compare these products" shortlist — slugs, not ids, since the compare page needs
 * `/public/products/{slug}` (the only endpoint carrying full specifications) for each one
 * anyway. A zustand store rather than a plain hook because the toggle button on every
 * product card and the floating compare bar in the layout both need to see the same list
 * update instantly, not just the component that changed it.
 */
export const useCompareStore = create<CompareState>((set, get) => ({
  slugs: readSlugs(),

  toggle: (slug) => {
    const current = get().slugs;
    const next = current.includes(slug)
      ? current.filter((existing) => existing !== slug)
      : current.length >= MAX_COMPARE_ITEMS
        ? current
        : [...current, slug];
    if (next !== current) {
      persist(next);
      set({ slugs: next });
    }
  },

  remove: (slug) => {
    const next = get().slugs.filter((existing) => existing !== slug);
    persist(next);
    set({ slugs: next });
  },

  clear: () => {
    localStorage.removeItem(STORAGE_KEY);
    set({ slugs: [] });
  },
}));
