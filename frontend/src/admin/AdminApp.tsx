import { useEffect } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { AdminLayout } from './AdminLayout';
import { useAdminAuth } from './auth/adminAuth';
import { AdminLoginPage } from './pages/AdminLoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { DiscountsPage } from './pages/DiscountsPage';
import { DiscountEditorPage } from './pages/DiscountEditorPage';
import { PromoCodesPage } from './pages/PromoCodesPage';
import { CustomersPage } from './pages/CustomersPage';
import { ProductsPage } from './pages/ProductsPage';
import { ProductEditorPage } from './pages/ProductEditorPage';
import { CategoriesPage } from './pages/CategoriesPage';
import { AttributesPage } from './pages/AttributesPage';
import { OrdersPage } from './pages/OrdersPage';
import { OrderDetailPage } from './pages/OrderDetailPage';
import { PostsPage } from './pages/PostsPage';
import { PostEditorPage } from './pages/PostEditorPage';
import { PagesPage } from './pages/PagesPage';
import { PageEditorPage } from './pages/PageEditorPage';
import { NavigationPage } from './pages/NavigationPage';
import { ChatPage } from './pages/ChatPage';
import { ContactRequestsPage } from './pages/ContactRequestsPage';
import { ReviewsPage } from './pages/ReviewsPage';
import { BundlesPage } from './pages/BundlesPage';
import { ContentBlocksPage } from './pages/ContentBlocksPage';
import { MediaPage } from './pages/MediaPage';
import { SettingsPage } from './pages/SettingsPage';
import { UsersPage } from './pages/UsersPage';
import { PageLoader } from '@/components/ui/PageLoader';

/**
 * Admin panel root.
 *
 * Gates everything behind a session check. `restore()` exchanges the HttpOnly refresh
 * cookie for an access token, so a reload keeps the operator signed in without the token
 * ever touching localStorage.
 */
export default function AdminApp() {
  const { status, restore } = useAdminAuth();

  useEffect(() => {
    if (status === 'idle') {
      void restore();
    }
  }, [status, restore]);

  // Rendering the login form during the check would flash it at an operator who is
  // in fact still signed in.
  if (status === 'idle' || status === 'checking') {
    return <PageLoader />;
  }

  if (status === 'anonymous') {
    return <AdminLoginPage />;
  }

  return (
    <Routes>
      <Route element={<AdminLayout />}>
        <Route index element={<DashboardPage />} />

        <Route path="products" element={<ProductsPage />} />
        <Route path="products/new" element={<ProductEditorPage />} />
        <Route path="products/:id" element={<ProductEditorPage />} />
        <Route path="categories" element={<CategoriesPage />} />
        <Route path="attributes" element={<AttributesPage />} />

        <Route path="discounts" element={<DiscountsPage />} />
        <Route path="discounts/new" element={<DiscountEditorPage />} />
        <Route path="discounts/:id" element={<DiscountEditorPage />} />
        <Route path="promo-codes" element={<PromoCodesPage />} />
        <Route path="reviews" element={<ReviewsPage />} />
        <Route path="bundles" element={<BundlesPage />} />

        <Route path="orders" element={<OrdersPage />} />
        <Route path="orders/:id" element={<OrderDetailPage />} />
        <Route path="customers" element={<CustomersPage />} />
        <Route path="chat" element={<ChatPage />} />
        <Route path="contact-requests" element={<ContactRequestsPage />} />

        <Route path="posts" element={<PostsPage />} />
        <Route path="posts/new" element={<PostEditorPage />} />
        <Route path="posts/:id" element={<PostEditorPage />} />
        <Route path="pages" element={<PagesPage />} />
        <Route path="pages/:id" element={<PageEditorPage />} />
        <Route path="navigation" element={<NavigationPage />} />
        <Route path="content" element={<ContentBlocksPage />} />
        <Route path="media" element={<MediaPage />} />
        <Route path="settings" element={<SettingsPage />} />
        <Route path="users" element={<UsersPage />} />

        <Route path="*" element={<Navigate to="/admin" replace />} />
      </Route>
    </Routes>
  );
}
