# Şriftlər

Bu qovluğa iki variable şrift faylı əlavə edin:

| Fayl | Şrift | Mənbə |
|---|---|---|
| `inter-var.woff2` | Inter (mətn) | https://rsms.me/inter/ |
| `sora-var.woff2` | Sora (başlıqlar) | https://fonts.google.com/specimen/Sora |

## Niyə CDN yox?

`nginx.conf`-dakı Content-Security-Policy üçüncü tərəf mənbələrini bloklayır və
korporativ kataloq ziyarətçilərin IP ünvanını şrift provayderinə sızdırmamalıdır.
Şriftlər öz domenimizdən verilir.

## Fayllar olmasa nə olur?

Heç nə pozulmur. `@font-face` qaydası `font-display: swap` ilə işləyir və
`--font-sans` dəyişənindəki sistem şrift yığını istifadə olunur:

```
ui-sans-serif, system-ui, -apple-system, sans-serif
```

Fayllar əlavə edildikdən sonra `index.html`-də `<link rel="preload">` sətirlərini
şərh işarəsindən çıxarın.

## Fərqli şrift istifadə etmək

Admin panel → **Parametrlər → Tema** bölməsindən `fontFamily` və `headingFontFamily`
dəyərlərini dəyişin. Yeni şrift faylını bu qovluğa qoyub `src/styles/theme.css`-də
uyğun `@font-face` blokunu əlavə edin.
