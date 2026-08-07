-- =============================================================================
-- V900 : Optional showcase catalog.
--
-- Only applied when DEMO_DATA_LOCATION=,classpath:db/demo is set. Gives a fresh
-- installation a browsable catalog, a live discount campaign and a promo code so
-- the storefront, the ribbons and the checkout can be exercised end to end.
-- Safe to skip entirely in production.
-- =============================================================================

-- ---- Categories -------------------------------------------------------------
INSERT INTO categories (id, parent_id, slug, status, featured, show_in_menu, path, depth, sort_order)
VALUES (1, NULL, 'mebel', 'ACTIVE', TRUE, TRUE, '1', 0, 1),
       (2, NULL, 'isiqlandirma', 'ACTIVE', TRUE, TRUE, '2', 0, 2),
       (3, NULL, 'elektronika', 'ACTIVE', TRUE, TRUE, '3', 0, 3),
       (4, NULL, 'ofis', 'ACTIVE', TRUE, TRUE, '4', 0, 4),
       (5, 1, 'oturacaqlar', 'ACTIVE', FALSE, TRUE, '1/5', 1, 1),
       (6, 1, 'masalar', 'ACTIVE', FALSE, TRUE, '1/6', 1, 2),
       (7, 3, 'audio', 'ACTIVE', FALSE, TRUE, '3/7', 1, 1);

INSERT INTO category_translations (category_id, language_code, name, description)
VALUES (1, 'az', 'Mebel', 'Ev və ofis üçün mebel həlləri.'),
       (2, 'az', 'İşıqlandırma', 'Dekorativ və funksional işıqlandırma.'),
       (3, 'az', 'Elektronika', 'Gündəlik texnika və aksesuarlar.'),
       (4, 'az', 'Ofis', 'Ofis avadanlığı və ləvazimatları.'),
       (5, 'az', 'Oturacaqlar', 'Kreslo, stul və divanlar.'),
       (6, 'az', 'Masalar', 'İş və yemək masaları.'),
       (7, 'az', 'Audio', 'Qulaqlıq və səs sistemləri.'),
       (1, 'en', 'Furniture', 'Furniture for home and office.'),
       (2, 'en', 'Lighting', 'Decorative and functional lighting.'),
       (3, 'en', 'Electronics', 'Everyday technology and accessories.'),
       (4, 'en', 'Office', 'Office equipment and supplies.'),
       (5, 'en', 'Seating', 'Chairs, armchairs and sofas.'),
       (6, 'en', 'Tables', 'Work and dining tables.'),
       (7, 'en', 'Audio', 'Headphones and sound systems.');

SELECT setval(pg_get_serial_sequence('categories', 'id'), (SELECT max(id) FROM categories));


-- ---- Products ---------------------------------------------------------------
INSERT INTO products (id, category_id, sku, slug, price, compare_at_price, currency, show_price,
                      purchasable, track_stock, stock_quantity, featured, is_new, status, sort_order, published_at)
VALUES (1, 5, 'CHR-001', 'modern-istirahet-kreslosu', 249.00, NULL, 'AZN', TRUE, TRUE, TRUE, 24, TRUE, FALSE, 'ACTIVE', 1, now()),
       (2, 2, 'LMP-002', 'keramik-masa-lampasi', 89.00, NULL, 'AZN', TRUE, TRUE, TRUE, 40, TRUE, FALSE, 'ACTIVE', 2, now()),
       (3, 7, 'HDP-003', 'simsiz-noise-cancelling-qulaqliq', 399.00, 449.00, 'AZN', TRUE, TRUE, TRUE, 15, TRUE, TRUE, 'ACTIVE', 3, now()),
       (4, 6, 'DSK-004', 'hundurluyu-tenzimlenen-masa', 899.00, NULL, 'AZN', TRUE, TRUE, TRUE, 8, FALSE, FALSE, 'ACTIVE', 4, now()),
       (5, 5, 'SFA-005', 'uc-nefrlik-parca-divan', 1290.00, NULL, 'AZN', TRUE, TRUE, TRUE, 4, TRUE, FALSE, 'ACTIVE', 5, now()),
       (6, 4, 'DRL-006', 'akkumulyatorlu-drel-desti', 179.00, NULL, 'AZN', TRUE, TRUE, TRUE, 30, FALSE, FALSE, 'ACTIVE', 6, now()),
       (7, 5, 'CHR-007', 'erqonomik-ofis-kreslosu', 549.00, NULL, 'AZN', TRUE, TRUE, TRUE, 12, TRUE, TRUE, 'ACTIVE', 7, now()),
       (8, 2, 'LMP-008', 'minimalist-doseme-lampasi', 159.00, NULL, 'AZN', TRUE, TRUE, TRUE, 0, FALSE, FALSE, 'ACTIVE', 8, now()),
       (9, 6, 'TBL-009', 'palid-yemek-masasi', 1450.00, NULL, 'AZN', TRUE, TRUE, FALSE, 0, FALSE, FALSE, 'ACTIVE', 9, now()),
       (10, 3, 'SPK-010', 'portativ-bluetooth-dinamik', 129.00, NULL, 'AZN', TRUE, TRUE, TRUE, 55, FALSE, TRUE, 'ACTIVE', 10, now()),
       (11, 4, 'SHF-011', 'agac-kitab-refi', 329.00, NULL, 'AZN', TRUE, TRUE, TRUE, 18, FALSE, FALSE, 'ACTIVE', 11, now()),
       (12, 4, 'LMP-012', 'led-is-lampasi', 79.00, NULL, 'AZN', TRUE, TRUE, TRUE, 60, FALSE, FALSE, 'ACTIVE', 12, now());

INSERT INTO product_translations (product_id, language_code, name, short_description, description)
VALUES (1, 'az', 'Modern istirahət kreslosu', 'Premium parça və möhkəm ağac ayaqlar ilə erqonomik dizayn.',
        '<p>Uzun müddət rahat oturmaq üçün nəzərdə tutulmuş modern kreslo. Yüksək sıxlıqlı sünger, möhkəm palıd ayaqlar və ləkəyə davamlı parça üzlük.</p>'),
       (2, 'az', 'Keramik masa lampası', 'Kətan abajur və yumşaq işıq ilə minimalist keramika.',
        '<p>Yataq otağı və iş masası üçün yumşaq ambient işıq verən keramik lampa.</p>'),
       (3, 'az', 'Simsiz noise-cancelling qulaqlıq', 'Aktiv səs izolyasiyası, 30 saat batareya.',
        '<p>Aktiv küy azaldıcı texnologiya, sürətli şarj və çoxcihazlı qoşulma dəstəyi.</p>'),
       (4, 'az', 'Hündürlüyü tənzimlənən masa', 'Yaddaş rejimləri və kabel idarəetməsi ilə.',
        '<p>Oturaraq və ayaq üstə işləmək üçün elektrik motorlu masa. 4 yaddaş mövqeyi.</p>'),
       (5, 'az', 'Üç nəfərlik parça divan', 'Geniş və rahat, davamlı parça üzlük.',
        '<p>Ailəvi istirahət üçün geniş divan. Çıxarıla bilən yastıq üzlükləri.</p>'),
       (6, 'az', 'Akkumulyatorlu drel dəsti', 'İki batareya və 24 ədəd ucluq daxildir.',
        '<p>Ev təmiri üçün tam dəst: fırçasız motor, iki batareya, daşıma çantası.</p>'),
       (7, 'az', 'Erqonomik ofis kreslosu', 'Bel dəstəyi, tənzimlənən qol dayaqları.',
        '<p>Gün boyu iş üçün nəzərdə tutulmuş, torlu arxalıq və 4D qol dayaqları olan kreslo.</p>'),
       (8, 'az', 'Minimalist döşəmə lampası', 'İncə metal gövdə, isti işıq.',
        '<p>Oxu küncü üçün ideal, yönləndirilə bilən başlıqlı döşəmə lampası.</p>'),
       (9, 'az', 'Palıd yemək masası', 'Altı nəfərlik, təbii palıd.',
        '<p>Təbii palıd taxtadan hazırlanmış, yağ ilə örtülmüş yemək masası.</p>'),
       (10, 'az', 'Portativ Bluetooth dinamik', 'IPX7 sukeçirməz, 20 saat işləmə.',
        '<p>Çöl istifadəsi üçün suya davamlı, güclü bass verən portativ dinamik.</p>'),
       (11, 'az', 'Ağac kitab rəfi', 'Beş səviyyəli, divara bərkidilə bilən.',
        '<p>Kitab və dekorativ əşyalar üçün geniş rəf sistemi.</p>'),
       (12, 'az', 'LED iş lampası', 'İşıq temperaturu tənzimlənən, USB portlu.',
        '<p>Göz yorğunluğunu azaldan, üç işıq rejimli masa lampası.</p>');

INSERT INTO product_translations (product_id, language_code, name, short_description)
VALUES (1, 'en', 'Modern Lounge Chair', 'Ergonomic design with premium fabric and solid wood legs.'),
       (2, 'en', 'Ceramic Table Lamp', 'Minimalist ceramic lamp with linen shade.'),
       (3, 'en', 'Wireless Noise-Cancelling Headphones', 'Active noise cancelling, 30-hour battery.'),
       (4, 'en', 'Adjustable Standing Desk', 'Height adjustable desk with memory presets.'),
       (5, 'en', '3-Seater Fabric Sofa', 'Spacious and comfortable with durable upholstery.'),
       (6, 'en', 'Cordless Drill Kit', 'Brushless motor with dual battery and 24 bits.'),
       (7, 'en', 'Ergonomic Office Chair', 'Lumbar support and adjustable armrests.'),
       (8, 'en', 'Minimalist Floor Lamp', 'Slim metal body with warm light.'),
       (9, 'en', 'Oak Dining Table', 'Seats six, solid natural oak.'),
       (10, 'en', 'Portable Bluetooth Speaker', 'IPX7 waterproof, 20 hours playback.'),
       (11, 'en', 'Wooden Bookshelf', 'Five tiers, wall-mountable.'),
       (12, 'en', 'LED Desk Lamp', 'Adjustable colour temperature with USB port.');

SELECT setval(pg_get_serial_sequence('products', 'id'), (SELECT max(id) FROM products));


-- ---- Attributes -------------------------------------------------------------
INSERT INTO attribute_groups (id, code, sort_order) VALUES (1, 'general', 1), (2, 'dimensions', 2);
INSERT INTO attribute_group_translations (attribute_group_id, language_code, name)
VALUES (1, 'az', 'Ümumi'), (2, 'az', 'Ölçülər'), (1, 'en', 'General'), (2, 'en', 'Dimensions');
SELECT setval(pg_get_serial_sequence('attribute_groups', 'id'), (SELECT max(id) FROM attribute_groups));

INSERT INTO attributes (id, attribute_group_id, code, data_type, unit, filterable, show_in_listing, status, sort_order)
VALUES (1, 1, 'material', 'SELECT', NULL, TRUE, FALSE, 'ACTIVE', 1),
       (2, 1, 'color', 'SELECT', NULL, TRUE, TRUE, 'ACTIVE', 2),
       (3, 1, 'warranty', 'NUMBER', 'ay', TRUE, FALSE, 'ACTIVE', 3),
       (4, 1, 'assembly_required', 'BOOLEAN', NULL, FALSE, FALSE, 'ACTIVE', 4),
       (5, 2, 'width', 'NUMBER', 'sm', FALSE, FALSE, 'ACTIVE', 5);

INSERT INTO attribute_translations (attribute_id, language_code, name)
VALUES (1, 'az', 'Material'), (2, 'az', 'Rəng'), (3, 'az', 'Zəmanət'),
       (4, 'az', 'Yığılma tələb olunur'), (5, 'az', 'En'),
       (1, 'en', 'Material'), (2, 'en', 'Colour'), (3, 'en', 'Warranty'),
       (4, 'en', 'Assembly required'), (5, 'en', 'Width');
SELECT setval(pg_get_serial_sequence('attributes', 'id'), (SELECT max(id) FROM attributes));

INSERT INTO attribute_options (id, attribute_id, value, color_hex, sort_order)
VALUES (1, 1, 'wood', NULL, 1), (2, 1, 'metal', NULL, 2), (3, 1, 'fabric', NULL, 3), (4, 1, 'plastic', NULL, 4),
       (5, 2, 'black', '#111827', 1), (6, 2, 'white', '#F8FAFC', 2), (7, 2, 'beige', '#D6CCC2', 3),
       (8, 2, 'green', '#4B6455', 4);

INSERT INTO attribute_option_translations (option_id, language_code, label)
VALUES (1, 'az', 'Ağac'), (2, 'az', 'Metal'), (3, 'az', 'Parça'), (4, 'az', 'Plastik'),
       (5, 'az', 'Qara'), (6, 'az', 'Ağ'), (7, 'az', 'Bej'), (8, 'az', 'Yaşıl'),
       (1, 'en', 'Wood'), (2, 'en', 'Metal'), (3, 'en', 'Fabric'), (4, 'en', 'Plastic'),
       (5, 'en', 'Black'), (6, 'en', 'White'), (7, 'en', 'Beige'), (8, 'en', 'Green');
SELECT setval(pg_get_serial_sequence('attribute_options', 'id'), (SELECT max(id) FROM attribute_options));

INSERT INTO product_attribute_values (product_id, attribute_id, option_id, number_value, boolean_value)
VALUES (1, 1, 3, NULL, NULL), (1, 2, 7, NULL, NULL), (1, 3, NULL, 24, NULL), (1, 4, NULL, NULL, TRUE),
       (3, 1, 4, NULL, NULL), (3, 2, 5, NULL, NULL), (3, 3, NULL, 12, NULL), (3, 4, NULL, NULL, FALSE),
       (5, 1, 3, NULL, NULL), (5, 2, 7, NULL, NULL), (5, 3, NULL, 36, NULL),
       (7, 1, 2, NULL, NULL), (7, 2, 5, NULL, NULL), (7, 3, NULL, 24, NULL), (7, 4, NULL, NULL, TRUE),
       (9, 1, 1, NULL, NULL), (9, 3, NULL, 60, NULL);


-- ---- Live discount campaigns -----------------------------------------------
-- 1) A running "Yaz kampaniyası": 20% off everything in Furniture (incl. subcategories).
INSERT INTO discounts (id, name, description, discount_type, value, max_discount_amount, scope,
                       starts_at, ends_at, priority, show_badge, badge_label, badge_style, show_countdown, status)
VALUES (1, 'Yaz kampaniyası', 'Bütün mebel məhsullarına 20% endirim.',
        'PERCENTAGE', 20, 300, 'CATEGORIES',
        now() - INTERVAL '2 days', now() + INTERVAL '12 days', 10, TRUE, NULL, 'SEASONAL', TRUE, 'ACTIVE');

INSERT INTO discount_categories (discount_id, category_id, include_descendants) VALUES (1, 1, TRUE);

-- 2) A deeper, higher-priority deal on a single hero product — proves priority wins.
INSERT INTO discounts (id, name, description, discount_type, value, scope,
                       starts_at, ends_at, priority, show_badge, badge_label, badge_style, show_countdown, status)
VALUES (2, 'Həftənin təklifi', 'Simsiz qulaqlığa xüsusi endirim.',
        'PERCENTAGE', 35, 'PRODUCTS',
        now() - INTERVAL '1 day', now() + INTERVAL '5 days', 50, TRUE, 'HƏFTƏNİN TƏKLİFİ', 'HOT', TRUE, 'ACTIVE');

INSERT INTO discount_products (discount_id, product_id) VALUES (2, 3);

-- 3) A flat-amount clearance on two slow movers.
INSERT INTO discounts (id, name, discount_type, value, scope, starts_at, priority,
                       show_badge, badge_style, status)
VALUES (3, 'Anbar təmizlənməsi', 'FIXED_AMOUNT', 30, 'PRODUCTS', now() - INTERVAL '3 days', 5,
        TRUE, 'CLEARANCE', 'ACTIVE');

INSERT INTO discount_products (discount_id, product_id) VALUES (3, 11), (3, 12);

SELECT setval(pg_get_serial_sequence('discounts', 'id'), (SELECT max(id) FROM discounts));


-- ---- Promo codes ------------------------------------------------------------
INSERT INTO promo_codes (code, description, discount_type, value, max_discount_amount, min_subtotal,
                         free_delivery, starts_at, ends_at, usage_limit, usage_limit_per_customer, status)
VALUES ('XOSGELDIN10', 'İlk sifariş üçün 10% endirim', 'PERCENTAGE', 10, 50, 50, FALSE,
        now() - INTERVAL '1 day', now() + INTERVAL '90 days', 500, 1, 'ACTIVE'),
       ('PULSUZCATDIRILMA', 'Pulsuz çatdırılma', 'FIXED_AMOUNT', 0, NULL, 30, TRUE,
        now() - INTERVAL '1 day', now() + INTERVAL '60 days', NULL, 3, 'ACTIVE');


-- ---- Blog -------------------------------------------------------------------
INSERT INTO post_categories (id, slug, color, status, sort_order)
VALUES (1, 'xeberler', '#4F46E5', 'ACTIVE', 1),
       (2, 'meslehetler', '#06B6D4', 'ACTIVE', 2);

INSERT INTO post_category_translations (category_id, language_code, name)
VALUES (1, 'az', 'Xəbərlər'), (2, 'az', 'Məsləhətlər'),
       (1, 'en', 'News'), (2, 'en', 'Guides');
SELECT setval(pg_get_serial_sequence('post_categories', 'id'), (SELECT max(id) FROM post_categories));

INSERT INTO posts (id, category_id, slug, status, featured, reading_minutes, published_at)
VALUES (1, 1, 'yeni-kolleksiya-2026', 'ACTIVE', TRUE, 4, now() - INTERVAL '3 days'),
       (2, 2, 'ev-ofisi-necə-qurmali', 'ACTIVE', FALSE, 6, now() - INTERVAL '10 days'),
       (3, 2, 'mebel-baximi-ucun-5-meslehet', 'ACTIVE', FALSE, 5, now() - INTERVAL '20 days');

INSERT INTO post_translations (post_id, language_code, title, short_description, content)
VALUES (1, 'az', 'Yeni kolleksiya 2026', 'Bu mövsümün ən çox gözlənilən məhsulları artıq kataloqda.',
        '<p>Yeni kolleksiyamızda müasir formalar, təbii materiallar və funksional dizayn birləşir. Bu il diqqətimizi rahatlıq və davamlılığa yönəltdik.</p>
<p>Kolleksiyaya mebel, işıqlandırma və ofis həlləri daxildir — hər biri həm ev, həm də iş mühiti üçün düşünülüb.</p>
<h2>Nə dəyişdi?</h2>
<ul>
<li>Təbii ağac və parça materialları daha geniş istifadə olunur</li>
<li>Rəng palitrası isti və neytral tonlara yönəlib</li>
<li>Modul mebel həlləri kiçik mənzillər üçün optimallaşdırılıb</li>
</ul>
<p>Kolleksiyanı kataloqda indi kəşf edə bilərsiniz.</p>'),
       (2, 'az', 'Ev ofisini necə qurmalı', 'Məhsuldar iş üçün 7 praktik addım.',
        '<p>Evdə məhsuldar iş mühiti qurmaq bir neçə düzgün qərardan asılıdır. Aşağıdakı 7 addım sizə kömək edəcək.</p>
<h2>7 praktik addım</h2>
<ol>
<li><strong>Sabit iş yeri seçin.</strong> Mümkünsə, yalnız işə həsr olunmuş ayrıca bir künc ayırın.</li>
<li><strong>Düzgün masa hündürlüyünü tapın.</strong> Dirsəkləriniz 90 dərəcə bucaq altında olmalıdır.</li>
<li><strong>Erqonomik oturacağa investisiya edin.</strong> Bel dayağı olan kreslo uzunmüddətli ağrıların qarşısını alır.</li>
<li><strong>İşıqlandırmanı tənzimləyin.</strong> Təbii işıqla süni işığı kombinə edin, ekran parıltısından çəkinin.</li>
<li><strong>Kabelləri səliqəyə salın.</strong> Nizamsız kabellər həm təhlükə, həm də diqqət yayındırıcıdır.</li>
<li><strong>Fasilə cədvəli yaradın.</strong> Hər 90 dəqiqədən bir 5-10 dəqiqəlik fasilə məhsuldarlığı artırır.</li>
<li><strong>Saxlama sistemini optimallaşdırın.</strong> Sənədləri və avadanlığı əl çatan məsafədə, lakin masanı yükləmədən saxlayın.</li>
</ol>
<p>Düzgün masa hündürlüyü, işıqlandırma və oturacaq seçimi gün ərzində yorğunluğu ciddi azaldır.</p>'),
       (3, 'az', 'Mebel baxımı üçün 5 məsləhət', 'Mebelinizin ömrünü uzatmağın yolları.',
        '<p>Mebeliniz düzgün baxım olmadan tez köhnəlir. Aşağıdakı 5 məsləhət mebelinizin ömrünü illərlə uzadacaq.</p>
<ol>
<li><strong>Düzgün təmizlik məhsulları seçin.</strong> Ağac səthlər üçün aşındırıcı kimyəvi maddələrdən çəkinin.</li>
<li><strong>Birbaşa günəş işığından qoruyun.</strong> Uzunmüddətli günəş işığı rəngi soldura və materialı qurutmağa bilər.</li>
<li><strong>Nəmlik səviyyəsinə diqqət edin.</strong> Çox quru və ya çox nəmli mühit taxtanın deformasiyasına səbəb ola bilər.</li>
<li><strong>Vint və birləşmələri mütəmadi yoxlayın.</strong> Boşalmış hissələri vaxtında bərkitmək struktur zədələnməsinin qarşısını alır.</li>
<li><strong>Parçanı mütəmadi tozsuzlaşdırın.</strong> Divan və kreslo üzləri üçün yumşaq fırça ilə həftəlik təmizlik kifayətdir.</li>
</ol>
<p>Düzgün təmizlik məhsulları və mütəmadi baxım mebelinizin görünüşünü illərlə qoruyur.</p>');
SELECT setval(pg_get_serial_sequence('posts', 'id'), (SELECT max(id) FROM posts));


-- ---- Testimonials & partners ------------------------------------------------
INSERT INTO testimonials (id, person_name, position, company_name, rating, status, featured, sort_order)
VALUES (1, 'Səbinə Məmmədova', 'Marketinq direktoru', 'Pinnacle Retail', 5, 'ACTIVE', TRUE, 1),
       (2, 'Rəşad Əliyev', 'Əməliyyat rəhbəri', 'Nexora', 5, 'ACTIVE', TRUE, 2),
       (3, 'Leyla Hüseynova', 'Baş dizayner', 'Elevate Studio', 4, 'ACTIVE', FALSE, 3);

INSERT INTO testimonial_translations (testimonial_id, language_code, content)
VALUES (1, 'az', 'Kataloqumuzu tam yenidən qurduq. İdarəetmə panelindən hər şeyi özümüz dəyişə bilirik.'),
       (2, 'az', 'Sifariş və ödəniş axını çox rahat işləyir. Müştəri şikayətləri kəskin azaldı.'),
       (3, 'az', 'Dizayn tənzimləmələri kod dəyişmədən mümkündür — bu bizim üçün əsas idi.');
SELECT setval(pg_get_serial_sequence('testimonials', 'id'), (SELECT max(id) FROM testimonials));

INSERT INTO partners (name, website_url, status, sort_order)
VALUES ('Nexora', 'https://example.com', 'ACTIVE', 1),
       ('Brightline', 'https://example.com', 'ACTIVE', 2),
       ('Vertex', 'https://example.com', 'ACTIVE', 3),
       ('Solvix', 'https://example.com', 'ACTIVE', 4),
       ('Pinnacle', 'https://example.com', 'ACTIVE', 5),
       ('Quantis', 'https://example.com', 'ACTIVE', 6);


-- ---- Related products -------------------------------------------------------
INSERT INTO product_relations (product_id, related_product_id, relation_type, sort_order)
VALUES (1, 5, 'RELATED', 1), (1, 7, 'RELATED', 2), (1, 2, 'CROSS_SELL', 1),
       (3, 10, 'RELATED', 1), (7, 4, 'CROSS_SELL', 1), (4, 12, 'CROSS_SELL', 1);
