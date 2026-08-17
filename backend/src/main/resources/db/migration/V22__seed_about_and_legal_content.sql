-- =============================================================================
-- V22 : Content for ABOUT, PRIVACY, TERMS, DELIVERY
--
-- V13 created these four system pages but only gave them a title and one-line
-- description — no page_sections, so DynamicPage rendered them as a bare <h1> and a
-- single sentence (see frontend DynamicPage's empty-sections fallback). They are
-- linked from the header, footer and legal menus from day one, so an installation
-- that never touches the admin panel still ships four dead-looking pages. This adds
-- real starter content using only section types and column shapes V13 already used
-- for HOME and CONTACT — no schema change, and every field here stays editable from
-- the Pages screen exactly like anything an admin builds by hand.
--
-- Generic wording throughout ("platforma", "biz") rather than a fabricated founding
-- year or specific numbers: this is a white-label default a client publishes as-is or
-- edits before launch, not a real company's history.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- ABOUT (/haqqimizda)
-- ---------------------------------------------------------------------------
INSERT INTO page_sections (page_version_id, section_type, variant, eyebrow, title, subtitle,
                           content, button_text, button_url, background_style, configuration, sort_order)
SELECT pv.id, s.section_type, s.variant, s.eyebrow, s.title, s.subtitle,
       s.content, s.button_text, s.button_url, s.background_style, s.configuration::jsonb, s.sort_order
FROM page_versions pv
         JOIN pages p ON p.id = pv.page_id AND p.code = 'ABOUT'
         JOIN (VALUES
                   ('PAGE_HEADER', 'CENTERED', 'Haqqımızda', 'Kataloqunuz, bir platformada',
                    'Məhsul idarəetməsindən ödənişə qədər — hər şey bir yerdə.',
                    NULL, NULL, NULL, 'MUTED', '{}', 1),

                   ('RICH_TEXT', 'NARROW', NULL, NULL, NULL,
                    '<p>Biz məhsullarınızı bir yerdə toplayıb idarə etməyi və hər kanalda satmağı asanlaşdıran universal kataloq platformasıyıq. Məqsədimiz sadədir: işiniz məhsul satmaqdırsa, texnologiya buna mane olmamalıdır.</p><p>Kataloqdan tutmuş ödənişə, endirim kampaniyalarından canlı dəstəyə qədər — bütün prosesi bir platformada birləşdirdik ki, siz biznesinizə fokuslana biləsiniz.</p>',
                    NULL, NULL, 'DEFAULT', '{}', 2),

                   ('TIMELINE', 'HORIZONTAL', 'Yol xəritəmiz', 'Necə buraya çatdıq', NULL,
                    NULL, NULL, NULL, 'MUTED',
                    '{"entries":[' ||
                    '{"year":"Faza 1","title":"Başlanğıc","description":"Sadə, çevik kataloq idarəetməsi ideyası ilə yola çıxdıq."},' ||
                    '{"year":"Faza 2","title":"İnkişaf","description":"Endirim mühərriki, səbət və sifariş axını əlavə olundu."},' ||
                    '{"year":"Faza 3","title":"Genişlənmə","description":"Çoxdillilik, loyallıq proqramı və B2B hesablar qoşuldu."},' ||
                    '{"year":"Faza 4","title":"Bu gün","description":"Minlərlə məhsulu idarə edən müştərilərə xidmət göstəririk."}' ||
                    ']}', 3),

                   ('STATISTICS', 'COUNTER', NULL, 'Rəqəmlərlə biz', NULL,
                    NULL, NULL, NULL, 'DARK', '{}', 4),

                   ('TESTIMONIALS', 'GRID', 'Rəylər', 'Müştərilərimizin fikirləri', NULL,
                    NULL, NULL, NULL, 'DEFAULT', '{"limit":3}', 5),

                   ('CTA_BANNER', 'CENTERED', NULL, 'Sualınız var?', 'Komandamızla birbaşa danışın.',
                    NULL, 'Əlaqə saxla', '/elaqe', 'ACCENT', '{}', 6)
    ) AS s(section_type, variant, eyebrow, title, subtitle, content, button_text, button_url,
           background_style, configuration, sort_order) ON TRUE
WHERE pv.language_code = 'az' AND pv.status = 'PUBLISHED';


-- ---------------------------------------------------------------------------
-- PRIVACY (/mexfilik-siyaseti)
-- ---------------------------------------------------------------------------
INSERT INTO page_sections (page_version_id, section_type, variant, eyebrow, title, subtitle,
                           content, background_style, configuration, sort_order)
SELECT pv.id, s.section_type, s.variant, s.eyebrow, s.title, s.subtitle,
       s.content, s.background_style, s.configuration::jsonb, s.sort_order
FROM page_versions pv
         JOIN pages p ON p.id = pv.page_id AND p.code = 'PRIVACY'
         JOIN (VALUES
                   ('PAGE_HEADER', 'CENTERED', NULL, 'Məxfilik siyasəti',
                    'Məlumatlarınızın necə toplandığı və istifadə edildiyi haqqında', NULL, 'MUTED', '{}', 1),

                   ('RICH_TEXT', 'NARROW', NULL, NULL, NULL,
                    '<h2>Hansı məlumatları toplayırıq</h2>' ||
                    '<p>Sifariş verərkən, hesab yaradarkən və ya bizimlə əlaqə saxlayarkən adınızı, telefon nömrənizi, e-poçt ünvanınızı, çatdırılma ünvanınızı və sifariş tarixçənizi toplayırıq. Ödəniş məlumatlarınız birbaşa bank provayderi tərəfindən emal olunur — kart nömrəniz bizim serverlərimizdə saxlanılmır.</p>' ||
                    '<h2>Məlumatlardan necə istifadə edirik</h2>' ||
                    '<p>Toplanan məlumatlar yalnız sifarişinizi tamamlamaq, çatdırılmanı təşkil etmək, dəstək xidməti göstərmək və razılığınız olduqda yeniliklərdən xəbərdar etmək üçün istifadə olunur. Məlumatlarınız heç vaxt üçüncü tərəflərə satılmır.</p>' ||
                    '<h2>Çərəzlər</h2>' ||
                    '<p>Sayt, təcrübənizi yaxşılaşdırmaq üçün zəruri çərəzlərdən (səbət, sessiya) istifadə edir. Analitik çərəzlər yalnız razılığınızla aktivləşdirilir və brauzer parametrlərindən istənilən vaxt idarə oluna bilər.</p>' ||
                    '<h2>Məlumatların qorunması</h2>' ||
                    '<p>Bütün həssas məlumatlar şifrələnmiş kanal (HTTPS) ilə ötürülür, parollar geri döndürülməz şəkildə hash-lənir və giriş sessiyaları məhdud müddətli tokenlərlə qorunur.</p>' ||
                    '<h2>Hüquqlarınız</h2>' ||
                    '<ul><li>Hesabınızdakı məlumatlara istənilən vaxt baxa bilərsiniz</li><li>Yanlış məlumatın düzəldilməsini tələb edə bilərsiniz</li><li>Hesabınızın və şəxsi məlumatlarınızın silinməsini tələb edə bilərsiniz</li><li>Marketinq bildirişlərindən istənilən an çıxa bilərsiniz</li></ul>' ||
                    '<h2>Əlaqə</h2>' ||
                    '<p>Məxfilik siyasəti ilə bağlı suallarınız üçün "Əlaqə" səhifəsindən bizimlə əlaqə saxlaya bilərsiniz.</p>',
                    'DEFAULT', '{}', 2)
    ) AS s(section_type, variant, eyebrow, title, subtitle, content, background_style, configuration, sort_order) ON TRUE
WHERE pv.language_code = 'az' AND pv.status = 'PUBLISHED';


-- ---------------------------------------------------------------------------
-- TERMS (/istifade-sertleri)
-- ---------------------------------------------------------------------------
INSERT INTO page_sections (page_version_id, section_type, variant, eyebrow, title, subtitle,
                           content, background_style, configuration, sort_order)
SELECT pv.id, s.section_type, s.variant, s.eyebrow, s.title, s.subtitle,
       s.content, s.background_style, s.configuration::jsonb, s.sort_order
FROM page_versions pv
         JOIN pages p ON p.id = pv.page_id AND p.code = 'TERMS'
         JOIN (VALUES
                   ('PAGE_HEADER', 'CENTERED', NULL, 'İstifadə şərtləri',
                    'Saytdan istifadə etməzdən əvvəl tanış olun', NULL, 'MUTED', '{}', 1),

                   ('RICH_TEXT', 'NARROW', NULL, NULL, NULL,
                    '<h2>Ümumi müddəalar</h2>' ||
                    '<p>Bu sayta daxil olmaqla və ya sifariş verməklə aşağıdakı şərtləri qəbul etmiş olursunuz. Şərtlərlə razı deyilsinizsə, xahiş edirik saytdan istifadə etməyin.</p>' ||
                    '<h2>Hesab və qeydiyyat</h2>' ||
                    '<p>Sifariş vermək üçün hesab yaratmaq məcburi deyil — qonaq kimi checkout edə bilərsiniz. Hesab yaratdıqda təqdim etdiyiniz məlumatların düzgünlüyünə görə siz məsuliyyət daşıyırsınız.</p>' ||
                    '<h2>Qiymətlər və sifarişlər</h2>' ||
                    '<p>Bütün qiymətlər saytda göstərilən valyutada təqdim olunur. Aktiv kampaniyalar zamanı qiymət avtomatik yenilənir; sifariş təsdiqləndikdən sonra qiymət dəyişmir. Stokda olmayan məhsul üçün sifariş qəbul edilmir.</p>' ||
                    '<h2>Ödəniş</h2>' ||
                    '<p>Onlayn kart ödənişi və çatdırılma zamanı nağd ödəniş dəstəklənir. Onlayn ödənişlər təhlükəsiz, 3-D Secure dəstəkli provayder vasitəsilə həyata keçirilir.</p>' ||
                    '<h2>Çatdırılma və geri qaytarma</h2>' ||
                    '<p>Çatdırılma müddətləri "Çatdırılma və ödəniş" səhifəsində göstərilir. İstifadə edilməmiş, orijinal qablaşdırmada olan məhsulları alışdan 14 gün ərzində geri qaytara bilərsiniz.</p>' ||
                    '<h2>Məsuliyyətin məhdudlaşdırılması</h2>' ||
                    '<p>Platforma məhsul təsvirlərinin dəqiqliyi üçün səy göstərir, lakin texniki xətadan (qiymət, stok göstəricisi) yaranan uyğunsuzluq aşkarlandıqda operator sifarişlə əlaqə saxlayaraq düzəliş təklif edəcək.</p>' ||
                    '<h2>Dəyişikliklər</h2>' ||
                    '<p>Bu şərtlər vaxtaşırı yenilənə bilər. Yenilənmiş şərtlər saytda dərc olunduğu andan qüvvəyə minir.</p>',
                    'DEFAULT', '{}', 2)
    ) AS s(section_type, variant, eyebrow, title, subtitle, content, background_style, configuration, sort_order) ON TRUE
WHERE pv.language_code = 'az' AND pv.status = 'PUBLISHED';


-- ---------------------------------------------------------------------------
-- DELIVERY (/chatdirilma-ve-odenis) — linked from the footer support menu since V13;
-- left the same kind of empty as the other three.
-- ---------------------------------------------------------------------------
INSERT INTO page_sections (page_version_id, section_type, variant, eyebrow, title, subtitle,
                           content, background_style, configuration, sort_order)
SELECT pv.id, s.section_type, s.variant, s.eyebrow, s.title, s.subtitle,
       s.content, s.background_style, s.configuration::jsonb, s.sort_order
FROM page_versions pv
         JOIN pages p ON p.id = pv.page_id AND p.code = 'DELIVERY'
         JOIN (VALUES
                   ('PAGE_HEADER', 'CENTERED', NULL, 'Çatdırılma və ödəniş',
                    'Çatdırılma müddətləri, haqqı və dəstəklənən ödəniş üsulları', NULL, 'MUTED', '{}', 1),

                   ('RICH_TEXT', 'NARROW', NULL, NULL, NULL,
                    '<h2>Çatdırılma üsulları</h2>' ||
                    '<ul><li><strong>Kuryerlə çatdırılma</strong> — göstərdiyiniz ünvana çatdırılır</li><li><strong>Mağazadan götürmə</strong> — sifarişi özünüz götürə bilərsiniz, çatdırılma haqqı tələb olunmur</li></ul>' ||
                    '<h2>Çatdırılma müddəti</h2>' ||
                    '<p>Bakı daxilində sifarişlər adətən 1-2 iş günü, regionlara isə 2-4 iş günü ərzində çatdırılır. Sifariş təsdiqləndikdən sonra izləmə linki e-poçtunuza göndərilir.</p>' ||
                    '<h2>Çatdırılma haqqı</h2>' ||
                    '<p>Müəyyən məbləğdən yuxarı sifarişlərdə çatdırılma pulsuzdur — dəqiq həddi səbət səhifəsində görə bilərsiniz. Bu həddən aşağı sifarişlərə standart çatdırılma haqqı əlavə olunur.</p>' ||
                    '<h2>Ödəniş üsulları</h2>' ||
                    '<ul><li>Onlayn kart ödənişi (Visa/Mastercard, 3-D Secure ilə qorunur)</li><li>Çatdırılma zamanı nağd ödəniş</li></ul>' ||
                    '<h2>Sifarişi izləmə</h2>' ||
                    '<p>Sifariş nömrəniz ilə "Sifarişi izlə" səhifəsindən statusunu istənilən vaxt yoxlaya bilərsiniz.</p>',
                    'DEFAULT', '{}', 2)
    ) AS s(section_type, variant, eyebrow, title, subtitle, content, background_style, configuration, sort_order) ON TRUE
WHERE pv.language_code = 'az' AND pv.status = 'PUBLISHED';
