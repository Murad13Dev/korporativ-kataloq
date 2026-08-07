import DynamicPage from './DynamicPage';

/**
 * Home page.
 *
 * Renders whatever sections the admin composed on the HOME page — there is no hard-coded
 * home layout, which is the entire point of the section system. A client rearranges their
 * landing page from the admin panel and this file never changes.
 */
export function HomePage() {
  return <DynamicPage code="HOME" />;
}
