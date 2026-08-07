-- =============================================================================
-- V901 : Richer demo content — more products, reviews, variants, bundles and the
-- homepage content blocks (benefits/statistics/FAQ) that V900 left empty.
--
-- Media (product photos, category images, testimonial avatars, the hero video)
-- is deliberately NOT seeded here — SQL cannot create real files on disk, so
-- those are attached by a one-off script that uploads real images through the
-- admin API after the application boots. This file only adds the data that has
-- no such dependency.
-- =============================================================================

-- ---- More products ------------------------------------------------------------
INSERT INTO products (id, category_id, sku, slug, price, compare_at_price, currency, show_price,
                      purchasable, track_stock, stock_quantity, featured, is_new, status, sort_order, published_at)
VALUES (13, 5, 'BAR-013', 'bar-oturacagi', 189.00, NULL, 'AZN', TRUE, TRUE, TRUE, 22, FALSE, FALSE, 'ACTIVE', 13, now()),
       (14, 6, 'CFT-014', 'qehve-masasi', 349.00, NULL, 'AZN', TRUE, TRUE, TRUE, 14, TRUE, FALSE, 'ACTIVE', 14, now()),
       (15, 2, 'PND-015', 'asma-lampa', 219.00, 259.00, 'AZN', TRUE, TRUE, TRUE, 20, FALSE, TRUE, 'ACTIVE', 15, now()),
       (16, 3, 'SWT-016', 'agilli-saat', 459.00, 519.00, 'AZN', TRUE, TRUE, TRUE, 35, TRUE, TRUE, 'ACTIVE', 16, now()),
       (17, 7, 'MIC-017', 'studiya-mikrofonu', 289.00, 329.00, 'AZN', TRUE, TRUE, TRUE, 17, FALSE, FALSE, 'ACTIVE', 17, now()),
       (18, 4, 'CAB-018', 'sened-kabineti', 399.00, NULL, 'AZN', TRUE, TRUE, TRUE, 9, FALSE, FALSE, 'ACTIVE', 18, now()),
       (19, 1, 'WRD-019', 'geyim-shkafi', 1090.00, NULL, 'AZN', TRUE, TRUE, TRUE, 6, TRUE, FALSE, 'ACTIVE', 19, now()),
       (20, 5, 'HMK-020', 'asma-hamak-kreslo', 259.00, NULL, 'AZN', TRUE, TRUE, TRUE, 11, FALSE, TRUE, 'ACTIVE', 20, now());

INSERT INTO product_translations (product_id, language_code, name, short_description, description)
VALUES (13, 'az', 'Bar oturacağı', 'Hündür masalar üçün fırlanan oturacaqlı bar stulu.',
        '<p>Mətbəx adası və bar masaları üçün nəzərdə tutulmuş, ayaq dayağı olan fırlanan bar oturacağı.</p>'),
       (14, 'az', 'Qəhvə masası', 'Nazik metal ayaqlar üzərində şüşə üst masa.',
        '<p>Salon üçün yüngül görünüşlü, temperli şüşə üzlüklü qəhvə masası.</p>'),
       (15, 'az', 'Asma lampa', 'Mətbəx adası və yemək masası üzərinə uyğun asma işıqlandırma.',
        '<p>Tünd metal gövdəli, isti ton verən asma lampa. Hündürlüyü tənzimlənə bilər.</p>'),
       (16, 'az', 'Ağıllı saat', 'Ürək dərəcəsi, yuxu izləmə və 7 gün batareya resursu.',
        '<p>Gündəlik fəallıq izləmə, bildirişlər və su keçirməzlik ilə ağıllı saat.</p>'),
       (17, 'az', 'Studiya mikrofonu', 'USB qoşulan kondansator mikrofon, podkast və səsləndirmə üçün.',
        '<p>Aydın səs keyfiyyəti verən kondansator kapsullu, tərpənmə əleyhinə dayaqlı studiya mikrofonu.</p>'),
       (18, 'az', 'Sənəd kabineti', 'İki siyirmə, açarlı bağlanan metal arxiv şkafı.',
        '<p>Ofis sənədlərinin təhlükəsiz saxlanması üçün möhkəm metal kabinet.</p>'),
       (19, 'az', 'Geyim şkafı', 'Üç qapılı, aynalı və daxili rəf sistemli geniş şkaf.',
        '<p>Yataq otağı üçün genış saxlama həcmi olan, aynalı qapılı geyim şkafı.</p>'),
       (20, 'az', 'Asma hamak kreslo', 'Bağça və balkon üçün asma oturacaq, möhkəm kətan parça.',
        '<p>Açıq hava istirahəti üçün metal həlqəli, rahat asma hamak kreslo.</p>');

SELECT setval(pg_get_serial_sequence('products', 'id'), (SELECT max(id) FROM products));


-- ---- More variants (colour) ---------------------------------------------------
-- Option ids reused from V900: 5=black, 6=white, 7=beige, 8=green.
INSERT INTO product_variants (id, product_id, sku, track_stock, stock_quantity, allow_backorder,
                              sort_order, is_default, active)
VALUES (1, 1, 'CHR-001-BLK', TRUE, 10, FALSE, 1, TRUE, TRUE),
       (2, 1, 'CHR-001-BEZ', TRUE, 6, FALSE, 2, FALSE, TRUE),
       (3, 7, 'CHR-007-BLK', TRUE, 8, FALSE, 1, TRUE, TRUE),
       (4, 7, 'CHR-007-WHT', TRUE, 5, FALSE, 2, FALSE, TRUE),
       (5, 19, 'WRD-019-WHT', TRUE, 12, FALSE, 1, TRUE, TRUE),
       (6, 19, 'WRD-019-GRN', TRUE, 4, FALSE, 2, FALSE, TRUE),
       (7, 16, 'SWT-016-BLK', TRUE, 20, FALSE, 1, TRUE, TRUE),
       (8, 16, 'SWT-016-WHT', TRUE, 15, FALSE, 2, FALSE, TRUE);

INSERT INTO product_variant_options (variant_id, attribute_option_id)
VALUES (1, 5), (2, 7), (3, 5), (4, 6), (5, 6), (6, 8), (7, 5), (8, 6);

SELECT setval(pg_get_serial_sequence('product_variants', 'id'), (SELECT max(id) FROM product_variants));


-- ---- More bundles ("frequently bought together") ------------------------------
INSERT INTO product_bundles (id, name, primary_product_id, active, sort_order)
VALUES (1, 'Ev ofisi dəsti', 4, TRUE, 1),
       (2, 'Yemək otağı dəsti', 9, TRUE, 2),
       (3, 'Texnologiya dəsti', 16, TRUE, 3),
       (4, 'Oturma zonası', 5, TRUE, 4);

INSERT INTO product_bundle_items (bundle_id, product_id)
VALUES (1, 7), (1, 12),
       (2, 11), (2, 2),
       (3, 10), (3, 3),
       (4, 2), (4, 1);

SELECT setval(pg_get_serial_sequence('product_bundles', 'id'), (SELECT max(id) FROM product_bundles));


-- ---- Reviews --------------------------------------------------------------
-- Guest-authored seed reviews (customer_id NULL) spread across products, ratings
-- and dates so the storefront and the admin moderation queue both look lived-in.
INSERT INTO product_reviews (product_id, author_name, rating, title, body, status, verified_purchase,
                             admin_reply, admin_reply_at, created_at)
VALUES
    (1, 'Aynur Vəliyeva', 5, 'Çox rahatdır', 'Uzun müddət oturanda belə yorulmuram, parçası da keyfiyyətlidir.', 'APPROVED', TRUE, NULL, NULL, now() - INTERVAL '2 days'),
    (1, 'Elvin Qasımov', 4, 'Gözlədiyimdən yaxşı', 'Rəngi şəkildəkindən bir az fərqli idi amma ümumilikdə razıyam.', 'APPROVED', TRUE, NULL, NULL, now() - INTERVAL '9 days'),
    (1, 'Nərmin Əhmədova', 5, NULL, 'Salonumuza tam uyğun oldu, çatdırılma da sürətli idi.', 'APPROVED', FALSE, 'Rəyiniz üçün təşəkkür edirik, Nərmin xanım!', now() - INTERVAL '20 days', now() - INTERVAL '21 days'),
    (2, 'Kamran Hüseynov', 4, 'Yaxşı işıq verir', 'Yataq otağı üçün ideal ölçüdür, işığı gözü yormur.', 'APPROVED', TRUE, NULL, NULL, now() - INTERVAL '5 days'),
    (2, 'Günel Rzayeva', 5, 'Zərif dizayn', 'Keramikası çox keyfiyyətlidir, foto ilə eynidir.', 'APPROVED', FALSE, NULL, NULL, now() - INTERVAL '14 days'),
    (3, 'Tural Məmmədov', 5, 'Səs keyfiyyəti əla', 'Noise-cancelling funksiyası təyyarədə çox işə yaradı.', 'APPROVED', TRUE, NULL, NULL, now() - INTERVAL '3 days'),
    (3, 'Səbinə Nağıyeva', 4, NULL, 'Batareya deyilən qədər 30 saat çıxmadı amma yenə razıyam.', 'APPROVED', TRUE, NULL, NULL, now() - INTERVAL '11 days'),
    (3, 'Orxan Fərəcov', 3, 'Normal', 'Qiymətinə görə pis deyil, amma bas səsi bir az zəifdir.', 'APPROVED', FALSE, NULL, NULL, now() - INTERVAL '30 days'),
    (3, 'Lalə Abbasova', 5, 'Tövsiyə edirəm', 'İkinci dəfə alıram, hədiyyə üçün aldım.', 'APPROVED', TRUE, NULL, NULL, now() - INTERVAL '45 days'),
    (4, 'Ceyhun Sadıqov', 5, 'İş prosesimi dəyişdi', 'Ayaq üstə işləmək seçimi arxamdakı ağrıları azaltdı.', 'APPROVED', TRUE, NULL, NULL, now() - INTERVAL '6 days'),
    (4, 'Aytac Musayeva', 4, 'Yığılması asan idi', 'Motoru səssizdir, yaddaş düymələri rahatdır.', 'APPROVED', FALSE, NULL, NULL, now() - INTERVAL '18 days'),
    (5, 'Rövşən İsmayılov', 5, 'Ailə üçün mükəmməl', 'Geniş və yumşaqdır, uşaqlar da çox sevir.', 'APPROVED', TRUE, NULL, NULL, now() - INTERVAL '4 days'),
    (5, 'Xəyalə Cəfərova', 4, NULL, 'Rəngi bir az açıq gəldi amma keyfiyyəti yaxşıdır.', 'APPROVED', FALSE, NULL, NULL, now() - INTERVAL '25 days'),
    (7, 'Fərid Quliyev', 5, 'Bel ağrım keçdi', 'Ofisdə gün boyu oturmaqdan şikayət etmirəm artıq.', 'APPROVED', TRUE, NULL, NULL, now() - INTERVAL '7 days'),
    (7, 'Aygün Talıbova', 5, 'Qol dayaqları əladır', '4D tənzimləmə hər masa hündürlüyünə uyğunlaşır.', 'APPROVED', TRUE, 'Xoş sözlərinizə görə minnətdarıq!', now() - INTERVAL '12 days', now() - INTERVAL '13 days'),
    (7, 'Anar Bayramov', 4, NULL, 'Yığılması bir az vaxt apardı, nəticə isə yaxşıdır.', 'APPROVED', FALSE, NULL, NULL, now() - INTERVAL '33 days'),
    (9, 'Nigar Əliyeva', 5, 'Möhtəşəm masa', 'Palıd taxta həqiqətən keyfiyyətlidir, ailəvi yeməklər üçün ideal.', 'APPROVED', TRUE, NULL, NULL, now() - INTERVAL '10 days'),
    (10, 'Vüsal Nəsirov', 4, 'Səsi güclüdür', 'Bağça məclisləri üçün kifayət qədər yüksək səs verir.', 'APPROVED', TRUE, NULL, NULL, now() - INTERVAL '8 days'),
    (10, 'Şəbnəm Quliyeva', 5, 'Su keçirmir, doğrudan', 'Hovuz kənarında istifadə etdim, heç problem olmadı.', 'APPROVED', FALSE, NULL, NULL, now() - INTERVAL '16 days'),
    (11, 'Elnur Sultanov', 4, NULL, 'Divara bərkitmək bir az çətin oldu, görünüşü isə yaxşıdır.', 'APPROVED', TRUE, NULL, NULL, now() - INTERVAL '22 days'),
    (13, 'Zeynəb Hacıyeva', 5, 'Mətbəx adamıza tam uyğun oldu', 'Fırlanma mexanizmi rahatdır, hündürlüyü də idealdır.', 'APPROVED', TRUE, NULL, NULL, now() - INTERVAL '1 days'),
    (13, 'Murad Əsgərov', 3, 'Orta', 'Gözlədiyim qədər möhkəm deyil amma qiymətinə dəyər.', 'APPROVED', FALSE, NULL, NULL, now() - INTERVAL '15 days'),
    (14, 'Pərvin Rəhimova', 5, 'Zərif və modern', 'Salonumuzun görünüşünü tamamilə dəyişdi.', 'APPROVED', TRUE, NULL, NULL, now() - INTERVAL '3 days'),
    (15, 'Kənan Vəkilov', 4, NULL, 'İşıq temperaturu isti və xoşdur, mətbəx üçün seçdim.', 'APPROVED', TRUE, NULL, NULL, now() - INTERVAL '9 days'),
    (16, 'Türkan Bağırova', 5, 'Gündəlik istifadə üçün əla', 'Yuxu izləmə funksiyası dəqiqdir, batareyası da uzun dözür.', 'APPROVED', TRUE, NULL, NULL, now() - INTERVAL '2 days'),
    (16, 'Samir Hacızadə', 5, 'Tövsiyə edirəm', 'İdman edərkən ürək dərəcəsini dəqiq göstərir.', 'APPROVED', TRUE, NULL, NULL, now() - INTERVAL '6 days'),
    (16, 'Ülviyyə Nəbiyeva', 4, NULL, 'Ekranı bir az kiçikdir amma funksionallığı yaxşıdır.', 'APPROVED', FALSE, NULL, NULL, now() - INTERVAL '19 days'),
    (17, 'Rəşad Kərimov', 5, 'Podkast üçün mükəmməl', 'Səs təmiz gəlir, əlavə redaktəyə ehtiyac qalmır.', 'APPROVED', TRUE, NULL, NULL, now() - INTERVAL '5 days'),
    (19, 'İlkin Novruzov', 5, 'Çox geniş', 'Üç nəfərlik ailəmizin bütün geyimləri rahat sığdı.', 'APPROVED', TRUE, NULL, NULL, now() - INTERVAL '4 days'),
    (19, 'Mələk Sadıqova', 4, NULL, 'Yığılması iki nəfər tələb edir amma nəticə əladır.', 'APPROVED', FALSE, NULL, NULL, now() - INTERVAL '27 days'),
    (20, 'Toğrul Əliyev', 5, 'Balkonumun ulduzu oldu', 'Hər axşam burda oturmaq bir zövqdür.', 'APPROVED', TRUE, NULL, NULL, now() - INTERVAL '7 days'),
    -- A couple left pending, so the moderation queue in admin isn't empty either.
    (13, 'Ruslan Bağırlı', 2, 'Gözlədiyim kimi olmadı', 'Rəngi elandakından fərqli gəldi.', 'PENDING', FALSE, NULL, NULL, now() - INTERVAL '1 days'),
    (16, 'Sevinc Tağıyeva', 5, 'Möhtəşəm!', 'Hədiyyə olaraq aldım, çox bəyənildi.', 'PENDING', TRUE, NULL, NULL, now() - INTERVAL '12 hours');


-- ---- Benefits (trust badges) ---------------------------------------------------
-- V13__seed_baseline.sql already seeded 4 rows (ids 1-4, icons package/shield-check/
-- truck/headset) — these add more variety on top rather than replacing them.
INSERT INTO benefits (id, icon, status, sort_order)
VALUES (5, 'award', 'ACTIVE', 5),
       (6, 'map-pin', 'ACTIVE', 6),
       (7, 'zap', 'ACTIVE', 7),
       (8, 'box', 'ACTIVE', 8),
       (9, 'star', 'ACTIVE', 9);

INSERT INTO benefit_translations (benefit_id, language_code, title, description)
VALUES (5, 'az', 'Keyfiyyət zəmanəti', 'Bütün məhsullara rəsmi zəmanət'),
       (6, 'az', '10+ şəhərdə çatdırılma', 'Ölkə üzrə geniş çatdırılma şəbəkəsi'),
       (7, 'az', 'Səmərəli sifariş prosesi', 'Bir neçə kliklə sürətli alış-veriş'),
       (8, 'az', 'Təhlükəsiz qablaşdırma', 'Məhsullarınız zədəsiz çatdırılır'),
       (9, 'az', 'Yüksək qiymətləndirmə', 'Minlərlə müştəri tərəfindən dəyərləndirilib');
SELECT setval(pg_get_serial_sequence('benefits', 'id'), (SELECT max(id) FROM benefits));


-- ---- Statistics -----------------------------------------------------------
-- V13 already seeded 4 rows (ids 1-4: product count / customers / cities / rating).
INSERT INTO statistics (id, value, suffix, status, sort_order)
VALUES (5, '12', '+', 'ACTIVE', 5),
       (6, '98', '%', 'ACTIVE', 6);

INSERT INTO statistic_translations (statistic_id, language_code, label)
VALUES (5, 'az', 'İl təcrübə'),
       (6, 'az', 'Müştəri məmnuniyyəti');
SELECT setval(pg_get_serial_sequence('statistics', 'id'), (SELECT max(id) FROM statistics));


-- ---- FAQ --------------------------------------------------------------------
-- V13 already seeded 4 rows (ids 1-4: order/delivery/payment/return basics).
INSERT INTO faqs (id, page_id, category, status, sort_order)
VALUES (5, NULL, 'warranty', 'ACTIVE', 5),
       (6, NULL, 'wholesale', 'ACTIVE', 6);

INSERT INTO faq_translations (faq_id, language_code, question, answer)
VALUES (5, 'az', 'Zəmanət müddəti nə qədərdir?', 'Məhsuldan asılı olaraq 12-24 ay rəsmi zəmanət təqdim olunur.'),
       (6, 'az', 'Topdan satış təklifi varmı?', 'Bəli, korporativ və topdan sifarişlər üçün bizimlə əlaqə saxlayın.');
SELECT setval(pg_get_serial_sequence('faqs', 'id'), (SELECT max(id) FROM faqs));
