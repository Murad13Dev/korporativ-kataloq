-- =============================================================================
-- V13 : Baseline data every installation needs
--
-- The bootstrap SUPER_ADMIN is deliberately NOT created here: a password hash in a
-- migration would be identical across every client deployment. It is created once
-- on first boot by BootstrapAdminInitializer from ADMIN_EMAIL / ADMIN_PASSWORD.
-- =============================================================================

INSERT INTO languages (code, name, native_name, flag_code, is_default, is_active, sort_order)
VALUES ('az', 'Azerbaijani', 'Azərbaycan', 'az', TRUE, TRUE, 1),
       ('en', 'English', 'English', 'gb', FALSE, TRUE, 2),
       ('ru', 'Russian', 'Русский', 'ru', FALSE, TRUE, 3);


-- ---------------------------------------------------------------------------
-- Default site configuration (version 1, published).
-- "Ink & Electric" default theme: deep slate structure, electric indigo accent,
-- coral for sale ribbons so discounts read instantly against the primary colour.
-- ---------------------------------------------------------------------------
INSERT INTO site_configuration_versions (version_number, status, label, configuration, published_at)
VALUES (1, 'PUBLISHED', 'Başlanğıc konfiqurasiya', '{
  "company": {
    "name": "Şirkət adı",
    "shortName": "Şirkət",
    "slogan": "Biznesiniz üçün universal kataloq",
    "shortDescription": "Məhsullarınızı bir platformada toplayın, idarə edin və satın.",
    "phones": ["+994 12 000 00 00"],
    "email": "info@company.az",
    "salesEmail": "sales@company.az",
    "supportEmail": "support@company.az",
    "whatsapp": "+994500000000",
    "address": "Bakı, Azərbaycan",
    "workingHours": "Bazar ertəsi – Cümə, 09:00–18:00",
    "mapUrl": "",
    "taxNumber": "",
    "registrationNumber": ""
  },
  "branding": {
    "logoLightMediaId": null,
    "logoDarkMediaId": null,
    "mobileLogoMediaId": null,
    "faviconMediaId": null,
    "defaultOgMediaId": null
  },
  "theme": {
    "preset": "INK_ELECTRIC",
    "primaryColor": "#4F46E5",
    "primaryHoverColor": "#4338CA",
    "primaryContrastColor": "#FFFFFF",
    "secondaryColor": "#0F172A",
    "accentColor": "#06B6D4",
    "saleColor": "#F43F5E",
    "successColor": "#10B981",
    "warningColor": "#F59E0B",
    "dangerColor": "#EF4444",
    "backgroundColor": "#FFFFFF",
    "sectionBackgroundColor": "#F8FAFC",
    "headingColor": "#0F172A",
    "textColor": "#475569",
    "mutedTextColor": "#94A3B8",
    "borderColor": "#E2E8F0",
    "footerBackgroundColor": "#0B1120",
    "footerTextColor": "#94A3B8",
    "fontFamily": "Inter",
    "headingFontFamily": "Sora",
    "baseFontSize": 16,
    "buttonRadius": 10,
    "cardRadius": 16,
    "inputRadius": 10,
    "containerWidth": 1280,
    "headerStyle": "DEFAULT",
    "footerStyle": "DARK",
    "productCardStyle": "ELEVATED",
    "darkModeEnabled": true
  },
  "socialNetworks": {
    "facebook": "",
    "instagram": "",
    "linkedin": "",
    "youtube": "",
    "x": "",
    "tiktok": "",
    "telegram": ""
  },
  "features": {
    "showLanguageSelector": true,
    "showNewsletter": true,
    "showWhatsappButton": true,
    "showSearch": true,
    "commerceEnabled": true,
    "cartEnabled": true,
    "onlinePaymentEnabled": true,
    "chatEnabled": true,
    "wishlistEnabled": true,
    "guestCheckoutEnabled": true,
    "showPricesToGuests": true,
    "showStockStatus": true,
    "blogEnabled": true,
    "reviewsEnabled": false
  },
  "commerce": {
    "currency": "AZN",
    "currencySymbol": "₼",
    "currencyPosition": "SUFFIX",
    "freeDeliveryThreshold": 150,
    "defaultDeliveryPrice": 5,
    "paymentMethods": ["ONLINE", "CASH_ON_DELIVERY"],
    "minOrderTotal": 0
  },
  "seo": {
    "defaultTitle": "Şirkət adı — Universal kataloq",
    "titleTemplate": "%s | Şirkət adı",
    "defaultDescription": "Məhsul kataloqumuzu kəşf edin.",
    "googleAnalyticsId": "",
    "googleTagManagerId": "",
    "facebookPixelId": "",
    "robotsPolicy": "INDEX_FOLLOW"
  },
  "legal": {
    "copyright": "© 2026 Şirkət adı. Bütün hüquqlar qorunur.",
    "showCookieBanner": true
  }
}'::jsonb, now());


-- ---------------------------------------------------------------------------
-- System pages. These may be edited but never deleted — the router depends on them.
-- ---------------------------------------------------------------------------
INSERT INTO pages (code, page_type, is_system, layout, sort_order)
VALUES ('HOME', 'HOME', TRUE, 'DEFAULT', 1),
       ('CATALOG', 'CATALOG', TRUE, 'DEFAULT', 2),
       ('ABOUT', 'ABOUT', TRUE, 'DEFAULT', 3),
       ('BLOG', 'BLOG', TRUE, 'DEFAULT', 4),
       ('CONTACT', 'CONTACT', TRUE, 'DEFAULT', 5),
       ('PRIVACY', 'LEGAL', TRUE, 'NARROW', 6),
       ('TERMS', 'LEGAL', TRUE, 'NARROW', 7),
       ('DELIVERY', 'LEGAL', TRUE, 'NARROW', 8);


-- Published Azerbaijani version for every system page.
INSERT INTO page_versions (page_id, version_number, language_code, slug, title, short_description,
                           seo_title, seo_description, status, published_at)
SELECT p.id,
       1,
       'az',
       v.slug,
       v.title,
       v.description,
       v.title,
       v.description,
       'PUBLISHED',
       now()
FROM pages p
         JOIN (VALUES ('HOME', 'ana-sehife', 'Ana səhifə', 'Məhsul kataloqumuza xoş gəlmisiniz.'),
                      ('CATALOG', 'kataloq', 'Kataloq', 'Bütün məhsullarımızı kəşf edin.'),
                      ('ABOUT', 'haqqimizda', 'Haqqımızda', 'Şirkətimiz haqqında.'),
                      ('BLOG', 'bloq', 'Bloq', 'Xəbərlər və məqalələr.'),
                      ('CONTACT', 'elaqe', 'Əlaqə', 'Bizimlə əlaqə saxlayın.'),
                      ('PRIVACY', 'mexfilik-siyaseti', 'Məxfilik siyasəti', 'Şəxsi məlumatların qorunması.'),
                      ('TERMS', 'istifade-sertleri', 'İstifadə şərtləri', 'Saytdan istifadə qaydaları.'),
                      ('DELIVERY', 'chatdirilma-ve-odenis', 'Çatdırılma və ödəniş',
                       'Çatdırılma şərtləri və ödəniş üsulları.')) AS v(code, slug, title, description)
              ON v.code = p.code;


-- Home page starter layout, built only from registered section components.
INSERT INTO page_sections (page_version_id, section_type, variant, eyebrow, title, subtitle,
                           button_text, button_url, background_style, configuration, sort_order)
SELECT pv.id, s.section_type, s.variant, s.eyebrow, s.title, s.subtitle,
       s.button_text, s.button_url, s.background_style, s.configuration::jsonb, s.sort_order
FROM page_versions pv
         JOIN pages p ON p.id = pv.page_id AND p.code = 'HOME'
         JOIN (VALUES
                   ('HERO', 'SPLIT', 'Universal kataloq', 'Məhsullarınız. Bir platformada.',
                    'Kataloqunuzu yaradın, idarə edin və hər kanalda dərc edin.',
                    'Kataloqa bax', '/kataloq', 'DEFAULT',
                    '{"showTrustBadges":true,"badges":["Rahat idarəetmə","Çoxdilli","Təhlükəsiz"]}', 1),
                   ('FEATURED_CATEGORIES', 'GRID', 'Kateqoriyalar', 'Populyar kateqoriyalar', NULL,
                    'Hamısına bax', '/kataloq', 'MUTED', '{"limit":6,"showProductCount":true}', 2),
                   ('DISCOUNTED_PRODUCTS', 'CAROUSEL', 'Kampaniya', 'Endirimdə olan məhsullar',
                    'Məhdud müddətli təkliflər', 'Bütün endirimlər', '/kataloq?discounted=true', 'DEFAULT',
                    '{"limit":8,"showCountdown":true}', 3),
                   ('FEATURED_PRODUCTS', 'GRID', 'Seçilmişlər', 'Seçilmiş məhsullar', NULL,
                    'Kataloqa bax', '/kataloq', 'DEFAULT', '{"limit":8}', 4),
                   ('BENEFITS', 'ICON_GRID', 'Üstünlüklər', 'Niyə biz?', NULL, NULL, NULL, 'MUTED',
                    '{"columns":4}', 5),
                   ('STATISTICS', 'COUNTER', NULL, 'Rəqəmlərlə biz', NULL, NULL, NULL, 'DARK', '{}', 6),
                   ('TESTIMONIALS', 'SLIDER', 'Rəylər', 'Müştərilərimiz nə deyir', NULL, NULL, NULL, 'DEFAULT',
                    '{"limit":6}', 7),
                   ('PARTNERS', 'LOGO_STRIP', NULL, 'Bizə etibar edənlər', NULL, NULL, NULL, 'MUTED', '{}', 8),
                   ('LATEST_POSTS', 'GRID', 'Bloq', 'Son yazılar', NULL, 'Bütün yazılar', '/bloq', 'DEFAULT',
                    '{"limit":3}', 9),
                   ('CTA_BANNER', 'CENTERED', NULL, 'Layihənizi birlikdə quraq',
                    'Komandamız sizinlə əlaqə saxlasın.', 'Əlaqə saxla', '/elaqe', 'ACCENT', '{}', 10)
    ) AS s(section_type, variant, eyebrow, title, subtitle, button_text, button_url,
           background_style, configuration, sort_order) ON TRUE
WHERE pv.language_code = 'az' AND pv.status = 'PUBLISHED';


-- Contact page starter layout.
INSERT INTO page_sections (page_version_id, section_type, variant, eyebrow, title, subtitle,
                           background_style, configuration, sort_order)
SELECT pv.id, s.section_type, s.variant, s.eyebrow, s.title, s.subtitle,
       s.background_style, s.configuration::jsonb, s.sort_order
FROM page_versions pv
         JOIN pages p ON p.id = pv.page_id AND p.code = 'CONTACT'
         JOIN (VALUES
                   ('PAGE_HEADER', 'DEFAULT', 'Əlaqə', 'Bizimlə əlaqə saxlayın',
                    'Suallarınızı göndərin, bir iş günü ərzində cavablandıraq.', 'DEFAULT', '{}', 1),
                   ('CONTACT_FORM', 'WITH_INFO', NULL, NULL, NULL, 'DEFAULT',
                    '{"showMap":true,"showOffice":true,"showSocial":true}', 2),
                   ('FAQ', 'ACCORDION', NULL, 'Tez-tez verilən suallar', NULL, 'MUTED', '{"limit":8}', 3)
    ) AS s(section_type, variant, eyebrow, title, subtitle, background_style, configuration, sort_order) ON TRUE
WHERE pv.language_code = 'az' AND pv.status = 'PUBLISHED';


-- ---------------------------------------------------------------------------
-- Menus
-- ---------------------------------------------------------------------------
INSERT INTO menus (code, name, location, is_active)
VALUES ('main-header', 'Əsas menyu', 'HEADER', TRUE),
       ('footer-company', 'Şirkət', 'FOOTER_PRIMARY', TRUE),
       ('footer-support', 'Dəstək', 'FOOTER_SECONDARY', TRUE),
       ('legal', 'Hüquqi', 'LEGAL', TRUE);

INSERT INTO menu_versions (menu_id, version_number, status, published_at)
SELECT id, 1, 'PUBLISHED', now() FROM menus;

INSERT INTO menu_items (menu_version_id, title, url, link_type, sort_order, highlight)
SELECT mv.id, i.title, i.url, i.link_type, i.sort_order, i.highlight
FROM menu_versions mv
         JOIN menus m ON m.id = mv.menu_id
         JOIN (VALUES
                   ('main-header', 'Ana səhifə', '/', 'PAGE', 1, FALSE),
                   ('main-header', 'Kataloq', '/kataloq', 'PAGE', 2, FALSE),
                   ('main-header', 'Haqqımızda', '/haqqimizda', 'PAGE', 3, FALSE),
                   ('main-header', 'Bloq', '/bloq', 'PAGE', 4, FALSE),
                   ('main-header', 'Əlaqə', '/elaqe', 'PAGE', 5, TRUE),
                   ('footer-company', 'Haqqımızda', '/haqqimizda', 'PAGE', 1, FALSE),
                   ('footer-company', 'Bloq', '/bloq', 'PAGE', 2, FALSE),
                   ('footer-company', 'Əlaqə', '/elaqe', 'PAGE', 3, FALSE),
                   ('footer-support', 'Çatdırılma və ödəniş', '/chatdirilma-ve-odenis', 'PAGE', 1, FALSE),
                   ('footer-support', 'Tez-tez verilən suallar', '/elaqe#faq', 'CUSTOM', 2, FALSE),
                   ('legal', 'Məxfilik siyasəti', '/mexfilik-siyaseti', 'PAGE', 1, FALSE),
                   ('legal', 'İstifadə şərtləri', '/istifade-sertleri', 'PAGE', 2, FALSE)
    ) AS i(menu_code, title, url, link_type, sort_order, highlight) ON i.menu_code = m.code;


-- ---------------------------------------------------------------------------
-- Starter corporate content
-- ---------------------------------------------------------------------------
INSERT INTO benefits (icon, sort_order)
VALUES ('package', 1), ('shield-check', 2), ('truck', 3), ('headset', 4);

INSERT INTO benefit_translations (benefit_id, language_code, title, description)
SELECT b.id, 'az', t.title, t.description
FROM benefits b
         JOIN (VALUES (1, 'Geniş çeşid', 'Minlərlə məhsul bir kataloqda.'),
                      (2, 'Zəmanətli keyfiyyət', 'Bütün məhsullar rəsmi zəmanət ilə.'),
                      (3, 'Sürətli çatdırılma', 'Bakı daxili 1-2 iş günü.'),
                      (4, 'Canlı dəstək', 'Suallarınıza dərhal cavab.')) AS t(sort_order, title, description)
              ON t.sort_order = b.sort_order;

INSERT INTO statistics (icon, value, suffix, sort_order)
VALUES ('box', '2 500', '+', 1),
       ('users', '10 000', '+', 2),
       ('map-pin', '25', '+', 3),
       ('star', '4.8', '/5', 4);

INSERT INTO statistic_translations (statistic_id, language_code, label)
SELECT s.id, 'az', t.label
FROM statistics s
         JOIN (VALUES (1, 'Məhsul'), (2, 'Müştəri'), (3, 'Şəhər'), (4, 'Reytinq')) AS t(sort_order, label)
              ON t.sort_order = s.sort_order;

INSERT INTO faqs (category, sort_order)
VALUES ('general', 1), ('delivery', 2), ('payment', 3), ('return', 4);

INSERT INTO faq_translations (faq_id, language_code, question, answer)
SELECT f.id, 'az', t.question, t.answer
FROM faqs f
         JOIN (VALUES
                   (1, 'Sifarişi necə verə bilərəm?',
                    'Bəyəndiyiniz məhsulu səbətə əlavə edin, səbət səhifəsindən "Sifarişi tamamla" düyməsinə klikləyin və çatdırılma məlumatlarını doldurun.'),
                   (2, 'Çatdırılma nə qədər çəkir?',
                    'Bakı daxilində sifarişlər 1-2 iş günü, regionlara 2-4 iş günü ərzində çatdırılır.'),
                   (3, 'Hansı ödəniş üsulları var?',
                    'Onlayn kart ödənişi və çatdırılma zamanı nağd ödəniş mümkündür.'),
                   (4, 'Məhsulu geri qaytara bilərəmmi?',
                    'İstifadə edilməmiş məhsulları 14 gün ərzində geri qaytara bilərsiniz.')
    ) AS t(sort_order, question, answer) ON t.sort_order = f.sort_order;


INSERT INTO chat_quick_replies (code, title, body, language_code, sort_order)
VALUES ('greeting', 'Salamlama', 'Salam! Sizə necə kömək edə bilərəm?', 'az', 1),
       ('delivery', 'Çatdırılma', 'Bakı daxilində çatdırılma 1-2 iş günü çəkir və 150 ₼-dən yuxarı sifarişlər üçün pulsuzdur.', 'az', 2),
       ('stock', 'Anbar', 'Məhsulun mövcudluğunu yoxlayıram, bir neçə dəqiqə gözləyin.', 'az', 3),
       ('closing', 'Bağlanış', 'Başqa sualınız varsa yazın. Bizi seçdiyiniz üçün təşəkkür edirik!', 'az', 4);
