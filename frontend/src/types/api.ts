/**
 * Wire types shared with the Spring Boot API.
 *
 * These mirror the DTO records on the server one-for-one. Keeping them hand-written
 * (rather than generated) is deliberate for a project this size: the surface is stable,
 * and a hand-written type can carry the comments that explain what a field is *for*.
 */

// ---------------------------------------------------------------------------
// Envelopes
// ---------------------------------------------------------------------------

export interface DataResponse<T> {
  data: T;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface ApiErrorBody {
  timestamp: string;
  status: number;
  code: ErrorCode;
  message: string;
  path?: string;
  errors?: Record<string, string>;
}

export type ErrorCode =
  | 'VALIDATION_ERROR'
  | 'RESOURCE_NOT_FOUND'
  | 'RESOURCE_CONFLICT'
  | 'INTERNAL_ERROR'
  | 'RATE_LIMIT_EXCEEDED'
  | 'INVALID_CREDENTIALS'
  | 'UNAUTHENTICATED'
  | 'ACCESS_DENIED'
  | 'TOKEN_EXPIRED'
  | 'TOKEN_INVALID'
  | 'EMAIL_ALREADY_EXISTS'
  | 'CART_EMPTY'
  | 'CART_NOT_FOUND'
  | 'PRODUCT_NOT_PURCHASABLE'
  | 'INSUFFICIENT_STOCK'
  | 'PRICE_CHANGED'
  | 'PROMO_CODE_INVALID'
  | 'PROMO_CODE_EXPIRED'
  | 'PROMO_CODE_USAGE_LIMIT'
  | 'PROMO_CODE_MIN_SUBTOTAL'
  | 'COMMERCE_DISABLED'
  | 'PAYMENT_PROVIDER_ERROR'
  | 'PAYMENT_WINDOW_EXPIRED'
  | 'ORDER_NOT_CANCELLABLE'
  | 'ORDER_STATUS_TRANSITION_INVALID'
  | (string & {});

// ---------------------------------------------------------------------------
// Site configuration
// ---------------------------------------------------------------------------

export interface SiteConfiguration {
  company: {
    name: string;
    shortName?: string;
    slogan?: string;
    shortDescription?: string;
    phones?: string[];
    email?: string;
    salesEmail?: string;
    supportEmail?: string;
    whatsapp?: string;
    address?: string;
    workingHours?: string;
    mapUrl?: string;
  };
  branding: {
    logoLightMediaId?: number | null;
    logoDarkMediaId?: number | null;
    mobileLogoMediaId?: number | null;
    faviconMediaId?: number | null;
    defaultOgMediaId?: number | null;
    /** Resolved URLs, added by the public endpoint so the client needs no extra call. */
    logoLightUrl?: string | null;
    logoDarkUrl?: string | null;
    faviconUrl?: string | null;
  };
  theme: ThemeConfig;
  socialNetworks: Record<string, string>;
  features: FeatureFlags;
  commerce: CommerceConfig;
  seo: {
    defaultTitle?: string;
    titleTemplate?: string;
    defaultDescription?: string;
    googleAnalyticsId?: string;
    robotsPolicy?: string;
  };
  legal: {
    copyright?: string;
    showCookieBanner?: boolean;
  };
}

export interface ThemeConfig {
  preset?: string;
  primaryColor: string;
  primaryHoverColor?: string;
  primaryContrastColor?: string;
  secondaryColor?: string;
  accentColor?: string;
  /** Colour of discount ribbons and sale prices. */
  saleColor?: string;
  successColor?: string;
  warningColor?: string;
  dangerColor?: string;
  backgroundColor?: string;
  sectionBackgroundColor?: string;
  headingColor?: string;
  textColor?: string;
  mutedTextColor?: string;
  borderColor?: string;
  footerBackgroundColor?: string;
  footerTextColor?: string;
  fontFamily?: string;
  headingFontFamily?: string;
  baseFontSize?: number;
  buttonRadius?: number;
  cardRadius?: number;
  inputRadius?: number;
  containerWidth?: number;
  headerStyle?: 'DEFAULT' | 'CENTERED' | 'MINIMAL' | 'TRANSPARENT';
  footerStyle?: 'DARK' | 'LIGHT' | 'MINIMAL';
  productCardStyle?: 'ELEVATED' | 'FLAT' | 'BORDERED';
}

export interface FeatureFlags {
  showLanguageSelector?: boolean;
  showNewsletter?: boolean;
  showWhatsappButton?: boolean;
  showSearch?: boolean;
  commerceEnabled?: boolean;
  cartEnabled?: boolean;
  onlinePaymentEnabled?: boolean;
  chatEnabled?: boolean;
  wishlistEnabled?: boolean;
  guestCheckoutEnabled?: boolean;
  showPricesToGuests?: boolean;
  showStockStatus?: boolean;
  blogEnabled?: boolean;
  reviewsEnabled?: boolean;
}

export interface CommerceConfig {
  currency: string;
  currencySymbol: string;
  currencyPosition?: 'PREFIX' | 'SUFFIX';
  freeDeliveryThreshold?: number;
  defaultDeliveryPrice?: number;
  paymentMethods?: PaymentMethod[];
  minOrderTotal?: number;
}

// ---------------------------------------------------------------------------
// Catalog
// ---------------------------------------------------------------------------

export type BadgeStyle = 'SALE' | 'HOT' | 'SEASONAL' | 'CLEARANCE' | 'BUNDLE' | 'CUSTOM';

/**
 * Everything needed to render a price and its ribbon.
 * Produced server-side by DiscountResolver so the client never computes a price.
 */
export interface PriceInfo {
  listPrice: number | null;
  finalPrice: number | null;
  savingAmount: number;
  savingPercentage: number;
  discounted: boolean;
  discountId: number | null;
  discountName: string | null;
  /** Ready to render: "-25%" or campaign wording like "BLACK FRIDAY". */
  badgeLabel: string | null;
  badgeStyle: BadgeStyle | null;
  badgeColor: string | null;
  showBadge: boolean;
  showCountdown: boolean;
  /** Campaign end — drives the countdown when showCountdown is true. */
  endsAt: string | null;
  currency: string;
}

export type StockStatus = 'IN_STOCK' | 'LOW_STOCK' | 'OUT_OF_STOCK' | 'BACKORDER';

export interface ProductCard {
  id: number;
  slug: string;
  sku: string;
  name: string;
  shortDescription: string | null;
  imageUrl: string | null;
  imageAlt: string | null;
  categoryId: number | null;
  categorySlug: string | null;
  categoryName: string | null;
  price: PriceInfo;
  showPrice: boolean;
  featured: boolean;
  isNew: boolean;
  purchasable: boolean;
  stockStatus: StockStatus;
  availableQuantity: number | null;
}

export interface ProductDetail extends Omit<ProductCard, 'imageUrl' | 'imageAlt'> {
  barcode: string | null;
  description: string | null;
  seoTitle: string | null;
  seoDescription: string | null;
  currency: string;
  compareAtPrice: number | null;
  minOrderQuantity: number;
  maxOrderQuantity: number | null;
  weightGrams: number | null;
  images: ProductImage[];
  documents: ProductDocument[];
  specifications: SpecificationGroup[];
  publishedAt: string | null;
  variants: ProductVariant[];
  variantAttributes: VariantAttribute[];
  averageRating: number;
  reviewCount: number;
}

export interface ProductBundle {
  id: number;
  name: string;
  items: ProductCard[];
  totalPrice: number;
  currency: string;
}

export interface Review {
  id: number;
  authorName: string;
  rating: number;
  title: string | null;
  body: string | null;
  verifiedPurchase: boolean;
  adminReply: string | null;
  adminReplyAt: string | null;
  createdAt: string;
}

export interface RatingSummary {
  average: number;
  count: number;
  breakdown: Record<number, number>;
}

/** One purchasable colour/size (etc.) combination of a product. */
export interface ProductVariant {
  id: number;
  sku: string;
  price: number | null;
  priceOverridden: boolean;
  stockStatus: StockStatus;
  availableQuantity: number | null;
  imageUrl: string | null;
  isDefault: boolean;
  optionIds: number[];
}

/** One variation axis ("Rəng") and the option chips a shopper can pick for it. */
export interface VariantAttribute {
  attributeId: number;
  code: string;
  name: string;
  options: VariantOption[];
}

export interface VariantOption {
  id: number;
  value: string;
  label: string;
  colorHex: string | null;
}

export interface ProductImage {
  id: number;
  mediaId: number;
  url: string;
  alt: string | null;
  main: boolean;
  sortOrder: number;
}

export interface ProductDocument {
  id: number;
  mediaId: number;
  url: string;
  title: string;
  documentType: string;
  fileSize: number;
}

export interface SpecificationGroup {
  groupCode: string;
  groupName: string;
  lines: SpecificationLine[];
}

export interface SpecificationLine {
  attributeCode: string;
  label: string;
  value: string | null;
  unit: string | null;
}

export interface CategoryCard {
  id: number;
  slug: string;
  name: string;
  description: string | null;
  icon: string | null;
  imageUrl: string | null;
  productCount: number;
  featured: boolean;
}

export interface CategoryNode extends Omit<CategoryCard, 'description'> {
  parentId: number | null;
  status: string;
  showInMenu: boolean;
  depth: number;
  sortOrder: number;
  children: CategoryNode[];
}

// ---------------------------------------------------------------------------
// Cart
// ---------------------------------------------------------------------------

export interface Cart {
  id: number | null;
  sessionId: string | null;
  currency: string;
  items: CartLine[];
  totals: CartTotals;
  promo: AppliedPromo | null;
  /** Non-fatal problems found while re-pricing; rendered as banners. */
  notices: CartNotice[];
  checkoutAllowed: boolean;
}

export interface CartLine {
  id: number;
  productId: number;
  slug: string;
  sku: string;
  name: string;
  imageUrl: string | null;
  quantity: number;
  unitPrice: number;
  originalUnitPrice: number;
  lineTotal: number;
  lineSaving: number;
  discounted: boolean;
  discountName: string | null;
  badgeLabel: string | null;
  savingPercentage: number;
  stockStatus: StockStatus;
  availableQuantity: number | null;
  minOrderQuantity: number;
  maxOrderQuantity: number | null;
  available: boolean;
  variantId: number | null;
  variantSku: string | null;
  variantLabel: string | null;
}

export interface CartTotals {
  originalSubtotal: number;
  subtotal: number;
  /** Saving from product campaigns. */
  itemDiscountTotal: number;
  /** Saving from the cart-level voucher. */
  promoDiscount: number;
  totalSaving: number;
  deliveryPrice: number;
  total: number;
  currency: string;
  itemCount: number;
  totalQuantity: number;
  freeDelivery: boolean;
  /** How much more is needed for free delivery; null when already free. */
  amountToFreeDelivery: number | null;
}

export interface AppliedPromo {
  code: string;
  description: string | null;
  amount: number;
  freeDelivery: boolean;
}

export type CartNoticeType =
  | 'PRODUCT_UNAVAILABLE'
  | 'PRICE_DROPPED'
  | 'PRICE_INCREASED'
  | 'OUT_OF_STOCK'
  | 'QUANTITY_REDUCED'
  | 'PROMO_REMOVED'
  | 'MIN_ORDER_NOT_MET';

export interface CartNotice {
  type: CartNoticeType;
  productName: string | null;
  message: string;
}

// ---------------------------------------------------------------------------
// Orders & payment
// ---------------------------------------------------------------------------

export type OrderStatus =
  | 'PENDING'
  | 'AWAITING_PAYMENT'
  | 'CONFIRMED'
  | 'PROCESSING'
  | 'SHIPPED'
  | 'DELIVERED'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'REFUNDED'
  | 'EXPIRED';

export type PaymentStatus =
  | 'PENDING'
  | 'AUTHORIZED'
  | 'PAID'
  | 'PARTIALLY_REFUNDED'
  | 'REFUNDED'
  | 'FAILED'
  | 'CANCELLED';

export type PaymentMethod = 'ONLINE' | 'CASH_ON_DELIVERY' | 'BANK_TRANSFER';
export type DeliveryMethod = 'COURIER' | 'PICKUP' | 'CARGO';

export interface CheckoutRequest {
  fullName: string;
  email: string;
  phone: string;
  companyName?: string;
  deliveryMethod: DeliveryMethod;
  addressId?: number | null;
  country?: string;
  city?: string;
  district?: string;
  addressLine?: string;
  postalCode?: string;
  paymentMethod: PaymentMethod;
  customerNote?: string;
  saveAddress?: boolean;
  marketingOptIn?: boolean;
  termsAccepted: boolean;
}

export interface CheckoutResponse {
  orderId: number;
  orderNumber: string;
  status: OrderStatus;
  paymentStatus: PaymentStatus;
  total: number;
  currency: string;
  /** Present only for online payment — the browser is redirected here. */
  paymentUrl: string | null;
  paymentReference: string | null;
  paymentExpiresAt: string | null;
}

export interface OrderSummary {
  id: number;
  orderNumber: string;
  status: OrderStatus;
  statusLabel: string;
  paymentStatus: PaymentStatus;
  paymentMethod: PaymentMethod;
  customerName: string;
  customerEmail: string;
  total: number;
  currency: string;
  itemCount: number;
  createdAt: string;
  placedAt: string | null;
}

export interface OrderDetail extends Omit<OrderSummary, 'itemCount'> {
  deliveryMethod: DeliveryMethod;
  customerId: number | null;
  customerPhone: string | null;
  companyName: string | null;
  shippingCountry: string | null;
  shippingCity: string | null;
  shippingDistrict: string | null;
  shippingAddressLine: string | null;
  shippingPostalCode: string | null;
  subtotal: number;
  discountTotal: number;
  promoDiscount: number;
  promoCode: string | null;
  deliveryPrice: number;
  totalSaving: number;
  customerNote: string | null;
  adminNote: string | null;
  items: OrderItem[];
  history: OrderStatusHistoryEntry[];
  payments: PaymentRecord[];
  paidAt: string | null;
  shippedAt: string | null;
  deliveredAt: string | null;
  cancelledAt: string | null;
  cancelReason: string | null;
  paymentExpiresAt: string | null;
  cancellable: boolean;
}

export interface OrderItem {
  id: number;
  productId: number | null;
  productName: string;
  productSku: string | null;
  productSlug: string | null;
  imageUrl: string | null;
  quantity: number;
  originalUnitPrice: number;
  unitPrice: number;
  discountAmount: number;
  discountName: string | null;
  totalPrice: number;
  discounted: boolean;
  variantSku: string | null;
  variantLabel: string | null;
}

export interface OrderStatusHistoryEntry {
  fromStatus: OrderStatus | null;
  toStatus: OrderStatus;
  toStatusLabel: string;
  note: string | null;
  actorType: 'ADMIN' | 'CUSTOMER' | 'SYSTEM' | 'PAYMENT_PROVIDER';
  createdAt: string;
}

export interface PaymentRecord {
  id: number;
  provider: string;
  reference: string;
  externalTransactionId: string | null;
  amount: number;
  refundedAmount: number;
  status: string;
  cardMask: string | null;
  failureMessage: string | null;
  createdAt: string;
  completedAt: string | null;
}

export interface PaymentStatusView {
  reference: string;
  paymentStatus: string;
  orderId: number;
  orderNumber: string;
  orderStatus: OrderStatus;
  orderStatusLabel: string;
  orderPaymentStatus: PaymentStatus;
  total: number;
  currency: string;
  failureMessage: string | null;
}

// ---------------------------------------------------------------------------
// Customer account
// ---------------------------------------------------------------------------

export interface CustomerProfile {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  companyName: string | null;
  customerType: 'INDIVIDUAL' | 'COMPANY';
  locale: string;
  emailVerified: boolean;
  marketingOptIn: boolean;
  createdAt: string;
}

export interface CustomerAuthResponse {
  accessToken: string;
  expiresIn: number;
  customer: CustomerProfile;
}

export interface Address {
  id: number;
  title: string;
  fullName: string | null;
  phone: string | null;
  country: string;
  city: string;
  district: string | null;
  addressLine: string;
  postalCode: string | null;
  notes: string | null;
  isDefault: boolean;
}

// ---------------------------------------------------------------------------
// Navigation & pages
// ---------------------------------------------------------------------------

export interface MenuItem {
  id: number;
  title: string;
  url: string;
  linkType: string;
  icon: string | null;
  description: string | null;
  target: '_self' | '_blank';
  highlight: boolean;
  children: MenuItem[];
}

export interface Language {
  code: string;
  name: string;
  nativeName: string | null;
  flagCode: string | null;
  isDefault: boolean;
  isActive: boolean;
  sortOrder: number;
}
