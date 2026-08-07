package az.catalog.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slug generation is the one place a typo turns into a broken public URL, so the
 * Azerbaijani/Cyrillic transliteration table and the collision-suffix loop are pinned
 * down here rather than discovered the first time an editor types a title with "ə" in it.
 */
class SlugUtilTest {

    @Nested
    @DisplayName("slugify")
    class Slugify {

        @Test
        @DisplayName("Azərbaycan hərfləri transliterasiya olunur")
        void transliteratesAzerbaijaniLetters() {
            assertThat(SlugUtil.slugify("Şəkərli Kişmiş")).isEqualTo("sekerli-kismis");
        }

        @Test
        @DisplayName("kiril hərfləri transliterasiya olunur")
        void transliteratesCyrillic() {
            assertThat(SlugUtil.slugify("Привет мир")).isEqualTo("privet-mir");
        }

        @Test
        @DisplayName("boşluqlar tirəyə çevrilir")
        void spacesBecomeDashes() {
            assertThat(SlugUtil.slugify("Hello World")).isEqualTo("hello-world");
        }

        @Test
        @DisplayName("ardıcıl boşluqlar tək tirəyə sıxılır")
        void collapsesRepeatedWhitespace() {
            assertThat(SlugUtil.slugify("Hello    World")).isEqualTo("hello-world");
        }

        @Test
        @DisplayName("hərf/rəqəm olmayan simvollar silinir")
        void stripsNonAlphanumeric() {
            assertThat(SlugUtil.slugify("Hello, World! 100% Natural")).isEqualTo("hello-world-100-natural");
        }

        @Test
        @DisplayName("baş və sonda tirə qalmır")
        void trimsEdgeDashes() {
            assertThat(SlugUtil.slugify("  Hello World  ")).isEqualTo("hello-world");
        }

        @Test
        @DisplayName("null və boş dəyər boş sətir qaytarır")
        void nullAndBlankReturnEmpty() {
            assertThat(SlugUtil.slugify(null)).isEmpty();
            assertThat(SlugUtil.slugify("   ")).isEmpty();
        }

        @Test
        @DisplayName("180 simvoldan uzun başlıq kəsilir")
        void truncatesToMaxLength() {
            String longTitle = "kelime ".repeat(40);
            assertThat(SlugUtil.slugify(longTitle)).hasSizeLessThanOrEqualTo(180);
        }
    }

    @Nested
    @DisplayName("uniqueSlug")
    class UniqueSlug {

        @Test
        @DisplayName("boş isə əsas slug qaytarılır")
        void returnsBaseSlugWhenFree() {
            assertThat(SlugUtil.uniqueSlug("Yeni Məhsul", slug -> false)).isEqualTo("yeni-mehsul");
        }

        @Test
        @DisplayName("tutulubsa -2 şəkilçisi əlavə olunur")
        void appendsSuffixWhenTaken() {
            Set<String> taken = Set.of("yeni-mehsul");
            assertThat(SlugUtil.uniqueSlug("Yeni Məhsul", taken::contains)).isEqualTo("yeni-mehsul-2");
        }

        @Test
        @DisplayName("bir neçə şəkilçi tutulubsa ilk boş olanı tapılır")
        void findsFirstFreeSuffix() {
            Set<String> taken = Set.of("yeni-mehsul", "yeni-mehsul-2", "yeni-mehsul-3");
            assertThat(SlugUtil.uniqueSlug("Yeni Məhsul", taken::contains)).isEqualTo("yeni-mehsul-4");
        }

        @Test
        @DisplayName("boş başlıq 'item' bazası ilə əvəzlənir")
        void blankTitleFallsBackToItem() {
            assertThat(SlugUtil.uniqueSlug("!!!", slug -> false)).isEqualTo("item");
        }
    }

    @Nested
    @DisplayName("isValidSlug")
    class IsValidSlug {

        @Test
        @DisplayName("kiçik hərf, rəqəm və tək tirədən ibarət slug etibarlıdır")
        void validSlugsPass() {
            assertThat(SlugUtil.isValidSlug("hello-world")).isTrue();
            assertThat(SlugUtil.isValidSlug("product-100")).isTrue();
            assertThat(SlugUtil.isValidSlug("a")).isTrue();
        }

        @Test
        @DisplayName("null, boş və çox uzun dəyər etibarsızdır")
        void nullBlankAndTooLongAreInvalid() {
            assertThat(SlugUtil.isValidSlug(null)).isFalse();
            assertThat(SlugUtil.isValidSlug("")).isFalse();
            assertThat(SlugUtil.isValidSlug("a".repeat(181))).isFalse();
        }

        @Test
        @DisplayName("böyük hərf, boşluq və kənar/ardıcıl tirə etibarsızdır")
        void malformedSlugsAreInvalid() {
            assertThat(SlugUtil.isValidSlug("Hello-World")).isFalse();
            assertThat(SlugUtil.isValidSlug("hello world")).isFalse();
            assertThat(SlugUtil.isValidSlug("-hello")).isFalse();
            assertThat(SlugUtil.isValidSlug("hello-")).isFalse();
            assertThat(SlugUtil.isValidSlug("hello--world")).isFalse();
        }
    }
}
