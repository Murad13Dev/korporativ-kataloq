import { useQuery } from '@tanstack/react-query';
import { useEffect } from 'react';
import { getData } from '@/lib/api';
import { applyDocumentMeta, applyTheme } from '@/lib/theme';
import { configureFormatting } from '@/lib/format';
import type { FeatureFlags, SiteConfiguration } from '@/types/api';

const QUERY_KEY = ['site-configuration'] as const;

/** Sensible behaviour before the configuration lands, so nothing renders as "disabled". */
const DEFAULT_FEATURES: Required<FeatureFlags> = {
  showLanguageSelector: true,
  showNewsletter: true,
  showWhatsappButton: false,
  showSearch: true,
  commerceEnabled: true,
  cartEnabled: true,
  onlinePaymentEnabled: true,
  chatEnabled: true,
  wishlistEnabled: true,
  guestCheckoutEnabled: true,
  showPricesToGuests: true,
  showStockStatus: true,
  blogEnabled: true,
  reviewsEnabled: false,
};

/**
 * The published site configuration.
 *
 * Fetched once and treated as effectively static for the session: it only changes when
 * an admin publishes, and a stale theme for a few minutes is far cheaper than refetching
 * it on every navigation.
 */
export function useSiteConfig() {
  const query = useQuery({
    queryKey: QUERY_KEY,
    queryFn: () => getData<SiteConfiguration>('/public/site-configuration'),
    staleTime: 10 * 60 * 1000,
    gcTime: 30 * 60 * 1000,
    retry: 2,
  });

  const config = query.data;

  // Theme tokens must be on :root before the first paint of themed content.
  useEffect(() => {
    if (config?.theme) {
      applyTheme(config.theme);
    }
    if (config) {
      applyDocumentMeta(config);
      configureFormatting(config.commerce);
    }
  }, [config]);

  return {
    config,
    isLoading: query.isLoading,
    isError: query.isError,
    company: config?.company,
    branding: config?.branding,
    theme: config?.theme,
    commerce: config?.commerce,
    social: config?.socialNetworks ?? {},
    legal: config?.legal,
    features: { ...DEFAULT_FEATURES, ...(config?.features ?? {}) },
  };
}

export const siteConfigQueryKey = QUERY_KEY;
