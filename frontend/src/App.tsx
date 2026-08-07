import { Suspense, lazy } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { StoreLayout } from '@/components/layout/StoreLayout';
import { PageLoader } from '@/components/ui/PageLoader';
import { ErrorBoundary } from '@/components/ui/ErrorBoundary';
import { AccountLayout } from '@/pages/AccountLayout';

// The pages a shopper actually lands on stay in the main bundle.
import { HomePage } from '@/pages/HomePage';
import { CatalogPage } from '@/pages/CatalogPage';
import { ProductDetailPage } from '@/pages/ProductDetailPage';

// Checkout is only reached by someone who has already committed, so it can be split.
const CartPage = lazy(() => import('@/pages/CartPage'));
const CheckoutPage = lazy(() => import('@/pages/CheckoutPage'));
const CheckoutResultPage = lazy(() => import('@/pages/CheckoutResultPage'));
const MockPaymentPage = lazy(() => import('@/pages/MockPaymentPage'));
const BlogListPage = lazy(() => import('@/pages/BlogListPage'));
const BlogDetailPage = lazy(() => import('@/pages/BlogDetailPage'));
const DynamicPage = lazy(() => import('@/pages/DynamicPage'));
const NotFoundPage = lazy(() => import('@/pages/NotFoundPage'));

// Account area — reached only by someone who clicks through to it.
const AccountLoginPage = lazy(() => import('@/pages/AccountLoginPage'));
const AccountRegisterPage = lazy(() => import('@/pages/AccountRegisterPage'));
const AccountForgotPasswordPage = lazy(() => import('@/pages/AccountForgotPasswordPage'));
const AccountResetPasswordPage = lazy(() => import('@/pages/AccountResetPasswordPage'));
const AccountProfilePage = lazy(() => import('@/pages/AccountProfilePage'));
const AccountAddressesPage = lazy(() => import('@/pages/AccountAddressesPage'));
const AccountOrdersPage = lazy(() => import('@/pages/AccountOrdersPage'));
const AccountOrderDetailPage = lazy(() => import('@/pages/AccountOrderDetailPage'));
const AccountWishlistPage = lazy(() => import('@/pages/AccountWishlistPage'));
const AccountLoyaltyPage = lazy(() => import('@/pages/AccountLoyaltyPage'));
const OrderTrackingPage = lazy(() => import('@/pages/OrderTrackingPage'));
const ComparePage = lazy(() => import('@/pages/ComparePage'));

// The admin panel is its own chunk entirely — a shopper never downloads it.
const AdminApp = lazy(() => import('@/admin/AdminApp'));

export function App() {
  return (
    <ErrorBoundary>
      <Suspense fallback={<PageLoader />}>
        <Routes>
          {/* Admin sits outside StoreLayout: it has its own shell and its own session. */}
          <Route path="/admin/*" element={<AdminApp />} />

          <Route element={<StoreLayout />}>
            <Route index element={<HomePage />} />

            <Route path="kataloq" element={<CatalogPage />} />
            <Route path="kataloq/:categorySlug" element={<CatalogPage />} />
            <Route path="mehsul/:slug" element={<ProductDetailPage />} />

            <Route path="sebet" element={<CartPage />} />
            <Route path="checkout" element={<CheckoutPage />} />
            <Route path="checkout/result" element={<CheckoutResultPage />} />
            {/*
              Hosted page for the built-in mock payment provider. It stands in for a
              bank's 3-D Secure screen so the whole payment flow can be exercised
              end to end without a merchant agreement.
            */}
            <Route path="checkout/mock-payment" element={<MockPaymentPage />} />

            <Route path="bloq" element={<BlogListPage />} />
            <Route path="bloq/:slug" element={<BlogDetailPage />} />

            <Route path="sifarisi-izle" element={<OrderTrackingPage />} />
            <Route path="muqayise" element={<ComparePage />} />

            <Route path="hesab/giris" element={<AccountLoginPage />} />
            <Route path="hesab/qeydiyyat" element={<AccountRegisterPage />} />
            <Route path="hesab/sifreni-unutdum" element={<AccountForgotPasswordPage />} />
            <Route path="hesab/sifre-yenile" element={<AccountResetPasswordPage />} />
            <Route path="hesab" element={<AccountLayout />}>
              <Route index element={<Navigate to="/hesab/profil" replace />} />
              <Route path="profil" element={<AccountProfilePage />} />
              <Route path="unvanlar" element={<AccountAddressesPage />} />
              <Route path="sifarisler" element={<AccountOrdersPage />} />
              <Route path="sifarisler/:id" element={<AccountOrderDetailPage />} />
              <Route path="sevimliler" element={<AccountWishlistPage />} />
              <Route path="xallar" element={<AccountLoyaltyPage />} />
            </Route>

            {/*
              CMS catch-all. Must stay last among the storefront routes: any slug that
              matched none of the fixed paths above is looked up as a published page,
              and DynamicPage falls through to 404 when the lookup misses.
            */}
            <Route path=":slug" element={<DynamicPage />} />

            <Route path="*" element={<NotFoundPage />} />
          </Route>
        </Routes>
      </Suspense>
    </ErrorBoundary>
  );
}
