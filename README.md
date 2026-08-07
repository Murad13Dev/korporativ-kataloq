# Universal Korporativ Kataloq Platforması

White-label kataloq, kontent və e-ticarət platforması. Bir kod bazası — hər müştəri
admin paneldən öz saytını qurur: loqo, rənglər, şriftlər, menyular, səhifələr, məhsullar.

**Stack:** Spring Boot 3.3 (Java 17) · PostgreSQL 16 · React 19 + TypeScript + Vite · Nginx · Docker

---

## Tez başlanğıc

```bash
cp .env.example .env
# .env faylında MÜTLƏQ doldurun: DB_PASSWORD, JWT_SECRET, PAYMENT_WEBHOOK_SECRET
#   openssl rand -base64 24   → DB_PASSWORD
#   openssl rand -base64 48   → JWT_SECRET
#   openssl rand -hex 32      → PAYMENT_WEBHOOK_SECRET

docker compose up -d --build
```

Sayt: <http://localhost> · API sənədləri: <http://localhost/api/swagger-ui.html>

**İlk giriş:** `ADMIN_PASSWORD` boş qoyulubsa sistem güclü parol yaradır və ilk
başlanğıcda **bir dəfə** loga yazır:

```bash
docker compose logs backend | grep -A4 "İLK SUPER_ADMIN"
```

### Nümunə kataloq ilə başlamaq

Boş sistemi yox, işlək kataloqu görmək üçün `.env`-də:

```env
DEMO_DATA_LOCATION=,classpath:db/demo
```

12 məhsul, 3 **canlı endirim kampaniyası**, 2 promo kod, bloq yazıları və rəylər yüklənir.
Endirim lentləri, geri sayım, səbət və checkout dərhal işləyir.

### Development (Docker olmadan)

```bash
# 1. PostgreSQL
docker run -d --name catalog-db -p 5432:5432 \
  -e POSTGRES_DB=catalog -e POSTGRES_USER=catalog -e POSTGRES_PASSWORD=catalog \
  postgres:16.4-alpine

# 2. Backend  →  http://localhost:8080
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 3. Frontend →  http://localhost:5173
cd frontend && npm install && npm run dev
```

---

## Endirim sistemi

Bu platformanın əsas fərqləndirici xüsusiyyəti. **Endirim məhsulun sütunu deyil —
planlaşdırıla bilən kampaniyadır.**

```
Discount (kampaniya)
├── discountType : PERCENTAGE | FIXED_AMOUNT
├── value        : 20        |  30 ₼
├── maxDiscountAmount        : faiz endirimi üçün tavan ("30%, maks 50 ₼")
├── scope        : ALL_PRODUCTS | CATEGORIES | PRODUCTS
├── startsAt / endsAt        : avtomatik başlayır və bitir
├── priority     : toqquşmada kim qalib gəlir
└── lent görünüşü: showBadge · badgeLabel · badgeStyle · badgeColor · showCountdown
```

### Niyə belə?

| Problem | Bu dizaynın cavabı |
|---|---|
| "Cümədən bazar ertəsinə bütün mebelə 20%" | Bir sətir. Planlaşdırılır, öz-özünə başlayır və bitir |
| Kampaniya bitəndə qiymətləri geri yazmaq | Heç bir batch job yoxdur — kampaniya sadəcə uyğunlaşmır |
| Keçmiş sifarişlərin qiyməti dəyişməsin | Sifariş sətirləri **snapshot**-dur, məhsula bağlı deyil |
| "-120% endirim" bug-ları | Kampaniyalar **üst-üstə gəlmir** — bir məhsul, bir qiymət, bir lent |

### Hansı kampaniya qalib gəlir?

`DiscountResolver` işləyən kampaniyalar arasından seçir:

1. **Ən yüksək `priority`**
2. Bərabərdirsə → **ən spesifik scope** (məhsul > kateqoriya > hamısı)
3. Yenə bərabərdirsə → **müştəri üçün ən böyük qənaət**

Kateqoriya kampaniyası materialised path (`1/5/12`) vasitəsilə **alt kateqoriyaları da**
əhatə edir — "Mebel"ə endirim "Mebel → Oturacaqlar"a da tətbiq olunur, əlavə sorğu olmadan.

### Promo kodlar ayrıdır və birləşir

Kampaniya **məhsulun** qiymətini təyin edir. Promo kod **səbətin** yekununu azaldır.
İkisi birlikdə işləyir: kod kampaniyaların artıq formalaşdırdığı ara cəmə tətbiq olunur —
`excludeDiscounted` bayrağı bunu qadağan etməyibsə.

### UI/UX

```
┌─────────────────────┐
│▞ -35% ▚   [şəkil]   │  diaqonal lent, badgeStyle → tema rəngi
│                     │
├─────────────────────┤
│ Simsiz qulaqlıq     │
│ 259.35 ₼  399.00 ₼  │  yeni qiymət + üstündən xətt çəkilmiş köhnə
│ 139.65 ₼ qənaət     │  məbləğ açıq yazılır — müştəri hesablama etməsin
│ ⏱ 4g 12s qalıb      │  showCountdown aktivdirsə
└─────────────────────┘
```

- **6 lent stili** (`SALE`/`HOT`/`SEASONAL`/`CLEARANCE`/`BUNDLE`/`CUSTOM`) — hər biri
  **tema tokenindən** rəng alır, kodda sabit rəng yoxdur
- Üstündən xətt çəkilmiş qiymət `aria-hidden` — ekran oxuyucu üçün ayrıca cümlə var
  ("249 manat 199 manat" eşitmək mənasızdır)
- Geri sayım taymeri **adaptiv**: saatdan çox qalıbsa dəqiqədə bir, son saatda saniyədə bir

### Admin: kampaniyanı işə salmadan əvvəl görmək

```
GET /api/admin/discounts/{id}/preview
```

Neçə məhsulun təsirləndiyini, ümumi kataloq dəyərini və nümunə qiymətləri qaytarır.
**Heç nəyi dəyişmir.** Səhv yazılmış dəyəri müştəri deyil, operator tutur.

---

## Arxitektura

```
korporativ-kataloq/
├── backend/                  Spring Boot modular monolith
│   ├── common/               xəta modeli · security · cache · i18n · rate limit
│   ├── modules/
│   │   ├── auth · user       JWT + rotasiyalı refresh token
│   │   ├── language          çoxdillilik (az/en/ru)
│   │   ├── media             upload · MIME+magic byte yoxlaması · silmə qoruması
│   │   ├── siteconfig        versiyalı JSONB: draft → preview → publish → restore
│   │   ├── catalog/
│   │   │   ├── category      ağac · materialised path
│   │   │   ├── product       çoxdilli · şəkil · sənəd · attribut
│   │   │   ├── attribute     TEXT/NUMBER/BOOLEAN/SELECT/MULTI_SELECT
│   │   │   └── discount      ⭐ kampaniya mühərriki
│   │   ├── commerce/
│   │   │   ├── customer      müştəri hesabı (admin-dən ayrı identity domain)
│   │   │   ├── cart          canlı yenidən qiymətləndirmə · qonaq→hesab birləşməsi
│   │   │   ├── promo         səbət səviyyəli kuponlar
│   │   │   ├── order         vəziyyət maşını · snapshot · anbar
│   │   │   └── payment       provider abstraksiyası · idempotent callback
│   │   └── notification      async e-poçt
│   └── db/migration/         14 Flyway migrasiyası
│
├── frontend/                 React 19 + TS + Vite + Tailwind 4
│   ├── styles/theme.css      "Ink & Electric" dizayn sistemi
│   ├── lib/theme.ts          konfiqurasiya → CSS custom properties
│   ├── components/catalog/   ⭐ lent · qiymət · geri sayım · məhsul kartı
│   └── pages/                kataloq · məhsul · səbət · checkout · nəticə
│
├── docker-compose.yml        postgres · backend · frontend(nginx) · mailhog · backup
└── .env.example
```

### White-label necə işləyir?

`theme.css` faylında **heç bir sabit rəng yoxdur** — hamısı `--ctl-*` CSS dəyişənlərini
oxuyur. `lib/theme.ts` bu dəyişənləri dərc olunmuş konfiqurasiyadan yazır. Nəticə: müştəri
admin paneldən brendini dəyişir, bütün sayt buna uyğunlaşır — **rebuild yoxdur, müştəriyə
xas CSS faylı yoxdur.**

Konfiqurasiya admin tərəfindən yazıldığı üçün CSS kontekstinə çatmazdan əvvəl
yoxlanılır (hex regexi, ölçü aralıqları, şrift adı ağ siyahısı).

### Versiyalı dərc

Sayt konfiqurasiyası, səhifələr və menyular eyni prinsiplə işləyir:

```
v1 PUBLISHED  ──publish──►  v1 ARCHIVED
v2 DRAFT                    v2 PUBLISHED
```

Partial unique indekslər **verilənlər bazası səviyyəsində** eyni anda bir DRAFT və bir
PUBLISHED-dən çoxuna icazə vermir — iki adminin eyni anda dərc etməsi iki canlı
konfiqurasiya yarada bilməz.

`restore` köhnə versiyanı **birbaşa dərc etmir** — ondan yeni draft yaradır.

---

## Canlı dəstək

STOMP over WebSocket, SockJS fallback ilə (korporativ proxy-lər WebSocket upgrade-ni tez-tez bloklayır).

### Giriş modeli

Ziyarətçi anonimdir — yoxlanacaq token yoxdur. Ona görə söhbətin kanalı brauzerin yaratdığı
128-bitlik gizli açarla adreslənir:

```
/topic/chat/{visitorToken}
```

**Token elə səlahiyyətin özüdür.** Onu tapmaq UUIDv4 təxmin etmək qədər çətindir, və heç vaxt
URL-də görünmür — yəni referrer və ya server loglarına sızmır.

Adminlər normal autentifikasiya olunur: `CONNECT` freym-i bearer token daşıyır, və
`/topic/admin/chat` kanalı işçi olmayan hər kəsə bağlıdır.

### Yayım commit-dən sonra

Mesaj tranzaksiya daxilində yayımlansaydı, abunəçi sonradan geri qaytarılan (rollback)
mesajı görə bilərdi — client bazanın heç saxlamadığı şeyi göstərərdi. Ona görə bütün
yayımlar `afterCommit` mərhələsində baş verir.

---

## Ödəniş

`PaymentProvider` interfeysi 4 metoddan ibarətdir: `initiate` · `handleCallback` ·
`verify` · `refund`. Sistemin qalan hissəsi yalnız bu interfeysi görür.

**Yeni bank əlavə etmək = bir class yazmaq + `PAYMENT_PROVIDER` dəyişmək.**

Hazırda `MockPaymentProvider` var — real 3-D Secure axınının formasını təkrarlayır
(yönləndirmə → imzalanmış callback → server yoxlaması), amma bank müqaviləsi tələb etmir.
Frontend-də `/checkout/mock-payment` səhifəsi bankın ekranını əvəz edir.

### Ödənişin 3 qorunması

| Qoruma | Necə |
|---|---|
| **İdempotentlik** | Hər callback əvvəlcə `payment_events`-ə yazılır; `(provider, external_event_id)` unique indeksi təkrar göndərişi susdurur |
| **Həqiqilik** | İmzası yoxlanmayan callback audit üçün saxlanılır, lakin **heç bir statusu dəyişmir** |
| **Məbləğ uyğunluğu** | Provayderin bildirdiyi məbləğ sifarişlə uyğun gəlmirsə rədd edilir — redirect axınında klassik manipulyasiya vektoru |

Callback endpoint-i **həmişə 200 qaytarır** — əks halda provayder eyni hadisəni sonsuz
təkrarlayır. Problemlər `payment_events` sətrində qeyd olunur.

---

## Təhlükəsizlik

| Sahə | Tətbiq |
|---|---|
| Parol | BCrypt cost 12 · uzunluq-əsaslı siyasət (min 10) · geniş yayılmış parollar bloklanır |
| Access token | JWT, **yalnız yaddaşda** — localStorage-da deyil, XSS oğurlaya bilmir |
| Refresh token | Opaque random, **SHA-256 hash-i saxlanılır** · rotasiya · replay aşkarlanarsa bütün sessiyalar bağlanır |
| Identity ayrılığı | Admin və müştəri tokenləri fərqli audience — müştəri tokeni `/api/admin`-ə çata bilmir |
| Rate limit | Token bucket: login, şifrə bərpası, əlaqə formu · üstəlik nginx səviyyəsində |
| HTML | OWASP sanitizer — **yazarkən** təmizlənir, render zamanı deyil |
| Fayl yükləmə | MIME + uzantı + **magic byte** üçlü yoxlaması · SVG-də skript axtarışı |
| SQL | Yalnız parametrli sorğular · JPA Criteria |
| Anbar yarışı | `stock >= :qty` şərtli atomik UPDATE — son ədəd üçün iki checkout qalib gələ bilməz |
| Xəta cavabları | Constraint adları, SQL, stack trace heç vaxt cavaba düşmür |

---

## Komandalar

```bash
# Backend
cd backend
mvn compile                    # kompilyasiya
mvn test                       # testlər
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Frontend
cd frontend
npm run dev                    # dev server
npm run typecheck              # TypeScript
npm run build                  # production build
npm test                       # Vitest

# Docker
docker compose up -d --build
docker compose logs -f backend
docker compose --profile backup up -d     # gündəlik backup
docker compose --profile dev up -d        # + MailHog (localhost:8025)
```

---

## Admin panel

`/admin` ünvanında. Ayrıca JS chunk-dır (~59 kB gzip) — alıcı onu heç vaxt yükləmir.

| Ekran | Nə edir |
|---|---|
| **Dashboard** | Real göstəricilər + hərəkət tələb edən xəbərdarlıqlar (ödəniş gözləyən sifarişlər, azalan anbar, cavablanmamış sorğular) |
| **Endirimlər** | Kampaniya siyahısı + redaktor: **canlı lent önizləməsi** və aktivləşdirmədən əvvəl **təsir hesablaması** |
| **Məhsullar** | Filtrli siyahı, endirimli qiymət sütunu, seçilmiş/kopyala |
| **Məhsul redaktoru** | 5 tab: məzmun (dil tabları) · qiymət və anbar · şəkil/sənəd · xüsusiyyətlər · SEO (axtarış önizləməsi). Üstəlik **sürətli endirim** paneli |
| **Kateqoriyalar** | Yığılıb-açılan ağac, yan panel redaktoru, valideyn dəyişmə |
| **Attributlar** | Qruplu siyahı, data tipi seçimi (istifadədə kilidlənir), SELECT seçimləri + rəng nümunələri |
| **Promo kodlar** | Siyahı + redaktor, istifadə sayğacı, təsadüfi kod generatoru, istifadə tarixçəsi |
| **Sifarişlər** | Siyahı + detal: vəziyyət maşınına uyğun status keçidləri, tarixçə, geri qaytarma |
| **Müştərilər** | Siyahı + detal panel: ünvanlar, sifariş tarixçəsi, ümumi xərc, bloklama |
| **Canlı dəstək** | İki panelli konsol, WebSocket ilə canlı, hazır cavablar, təyinat, status |
| **Əlaqə sorğuları** | Status çipləri, iki panelli oxunuş, daxili qeyd, spam işarəsi, mənbə izi |
| **Səhifələr** | Dil üzrə dərc vəziyyəti, bölmə paleti, göstər/gizlət, sırala, kopyala + SEO |
| **Bloq** | Yazı siyahısı + redaktor, planlaşdırılmış dərc, kateqoriya/teq idarəsi, SEO önizləməsi |
| **Naviqasiya** | İki səviyyəli menyu ağacı, versiyalı draft/dərc, düymə linkləri |
| **Kontent blokları** | 5 tab: üstünlüklər, statistika, rəylər, partnyorlar, FAQ — dil üzrə tərcümə, media seçimi |
| **Media** | Yükləmə, alt mətn redaktəsi, istifadədə olan faylın silinmə qoruması |
| **Parametrlər** | Şirkət · **Dizayn (canlı önizləmə)** · Funksiya açarları · Versiya tarixçəsi |
| **İstifadəçilər** | Rol idarəsi, şifrə təyini, deaktivləşdirmə |

**Rol modeli:** `SUPER_ADMIN` / `ADMIN` / `EDITOR` / `SUPPORT`. Menyu elementləri rola görə
**silinir**, deaktiv edilmir — API eyni qaydaları müstəqil tətbiq edir, gizlədilmiş düymə
nəzakət göstəricisidir, təhlükəsizlik mexanizmi deyil.

**Dizayn redaktoru** dəyişiklikləri izolyasiya edilmiş önizləmədə göstərir — operatorun
içində dayandığı paneli yenidən rəngləmədən. Dəyişikliklər qaralamada saxlanılır;
sayt yalnız "Dərc et" düyməsindən sonra dəyişir, əvvəlki versiya arxivlənir.

---

## Layihənin vəziyyəti

Bu, PDF-dəki 14–16 həftəlik planın icrasıdır.

### ✅ İşlək və yoxlanılıb

- **Verilənlər bazası** — 14 migrasiya, ERD-dəki hər şey + endirim + səbət/sifariş/ödəniş/chat
- **Endirim mühərriki** — 23 test keçir
- **Biznes-məntiqi vahid testləri** — promo kod hesablaması, sifariş status maşını, slug generasiyası (59 əlavə test)
- **Auth** — admin + müştəri, rotasiyalı refresh, şifrə bərpası, rollar
- **Sayt konfiqurasiyası** — versiyalı JSONB, draft/preview/publish/restore
- **Kataloq** — kateqoriya ağacı, məhsul, attribut, media, public API
- **E-ticarət** — səbət, promo kod, checkout, sifariş vəziyyət maşını, ödəniş
- **Dashboard** — real aqreqasiyalar + xəbərdarlıqlar
- **Public sayt** — dizayn sistemi, kataloq, məhsul, səbət, checkout, ödəniş nəticəsi, bloq
- **Section renderi** — 23 bölmə komponenti; ana səhifə və bütün CMS səhifələri buradan qurulur
- **Dinamik naviqasiya** — header/footer menyuları admin paneldən idarə olunur
- **Bloq** — dil üzrə yazılar, planlaşdırılmış dərc, kateqoriya/teq, public sayt inteqrasiyası
- **Əlaqə** — forma + məhsul sorğusu + xəbər bülteni (2 mərhələli təsdiq), 3 qatlı spam qorunması
- **Kontent blokları** — üstünlük, statistika, rəy, partnyor, FAQ üçün tam admin CRUD (public API əvvəldən hazır idi)
- **Chat** — STOMP/WebSocket canlı dəstək, qonaq ziyarətçi identifikasiyası, admin qutusu
- **Müştəri kabineti** — qeydiyyat/giriş (qonaq səbətinin birləşməsi ilə), profil, şifrə, ünvanlar, sifariş tarixçəsi və ləğvi, şifrə bərpası (e-poçt linki)
- **SEO** — `/sitemap.xml` (kateqoriya, məhsul, bloq, CMS səhifə slaqları) + `/robots.txt`, hər səhifədə canonical/OG/robots meta
- **Admin panel** — 17 ekran (yuxarıdakı cədvəl)
- **İnfrastruktur** — Docker Compose, Nginx, backup, `.env.example`

Backend `mvn test` təmiz keçir (82/82) · frontend `tsc` + `vite build` təmiz keçir.

### ⏳ Hələ yazılmayıb

| Modul | Vəziyyət |
|---|---|
| İnteqrasiya testləri | Yalnız təmiz biznes-məntiqi (endirim, promo kod, sifariş statusu, slug) vahid testlərlə əhatə olunub — controller/DB səviyyəsində test yoxdur (Testcontainers qoşulmayıb) |

> **Qeyd:** `docker compose up` ilə canlı boot testi aparılıb (əvvəllər Docker daemon bu
> mühitdə işləmirdi). Altı real problem tapılıb və düzəldilib:
> 1. `cart_items` cədvəlində `created_by`/`updated_by` sütunları yox idi (V10 migrasiyası)
> 2. `@EnableJpaAuditing` `OffsetDateTime` sahələri üçün öz `DateTimeProvider`-i tələb edir —
>    Spring-in defolt provayderi bunu dəstəkləmir (`JpaConfig`-ə əlavə olundu)
> 3. Repository interfeysləri wrapper class-ların içində nested idi (`BlogRepositories`,
>    `ChatRepositories`, `ContactRepositories`, `ContentRepositories`) — paketlənmiş jar-da
>    Spring Data JPA-nın classpath skaneri bunları tapmırdı (13 repository). Hamısı ayrıca
>    top-level fayllara çıxarıldı — indi bütün 40 repository düzgün tapılır.
> 4. `Product` (images/documents/attributeValues) və `Cart` (items + items.product.images)
>    eyni `@EntityGraph`-da iki `List`-tipli kolleksiyanı birgə join-fetch etməyə çalışırdı —
>    Hibernate "cannot simultaneously fetch multiple bags" ilə çökürdü. Hər EntityGraph-da
>    bir bag saxlanıldı, qalanı lazy-load olaraq buraxıldı (`default_batch_fetch_size: 32`
>    onları effektiv batch edir).
> 5. `MediaService.publicUrls()` boş halda `Map.of()` qaytarırdı — bu, `.get(null)`
>    çağırışında (media ID-si olmayan səhifə/bloq yazısı üçün normal hal) `NullPointerException`
>    atır (adi `HashMap`-dan fərqli olaraq). `HashMap`-ə dəyişdirildi.
> 6. **Ən genişyayılmış problem:** demək olar bütün admin siyahı/axtarış sorğuları
>    `(:search IS NULL OR lower(x) LIKE ...)` şablonundan istifadə edir. PostgreSQL-in
>    "extended" protokolu hər bind parametrinin tipini PARSE zamanı, hər `?` üçün ayrıca
>    həll etməyə çalışır — eyni JPQL parametri bir neçə yerdə işlədiləndə bəzi `?`-lər
>    (məsələn tənha `? IS NULL`) heç bir tip konteksti daşımır və Postgres ya xəta verir,
>    ya da `bytea`-ya defolt edir (`function lower(bytea) does not exist`). Connection
>    URL-inə `preferQueryMode=simple` əlavə edilməklə bütün sinif problem aradan qalxdı.
>
> Bu altısı da yalnız tam Spring konteksti canlı işə salınanda üzə çıxan növ bug idi;
> `mvn test`/`mvn compile` onları tuta bilmirdi. Stek indi tam yoxlanılıb: sitemap, robots,
> admin giriş, kataloq API-si, demo data — hamısı işləyir.

---

## Lisenziya

Proprietary.
