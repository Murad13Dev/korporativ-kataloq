package az.catalog.common.util;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.springframework.stereotype.Component;

/**
 * Everything an admin types into a rich-text editor passes through here before it is stored.
 * Sanitising on write (not on render) means the public API can never emit an unsafe payload
 * even if a consumer forgets to escape.
 */
@Component
public class HtmlSanitizer {

    private static final PolicyFactory RICH_TEXT = Sanitizers.FORMATTING
            .and(Sanitizers.BLOCKS)
            .and(Sanitizers.LINKS)
            .and(Sanitizers.TABLES)
            .and(Sanitizers.IMAGES)
            .and(new HtmlPolicyBuilder()
                    .allowElements("h1", "h2", "h3", "h4", "h5", "h6", "hr", "figure", "figcaption", "span")
                    .allowAttributes("class").onElements("span", "p", "div", "figure", "table", "td", "th")
                    .allowAttributes("target", "rel").onElements("a")
                    .allowAttributes("loading").onElements("img")
                    .allowUrlProtocols("https", "http", "mailto", "tel")
                    .requireRelNofollowOnLinks()
                    .toFactory());

    /** For fields that should carry no markup at all (titles, meta descriptions, alt text). */
    private static final PolicyFactory PLAIN_TEXT = new HtmlPolicyBuilder().toFactory();

    public String sanitizeRichText(String html) {
        return html == null ? null : RICH_TEXT.sanitize(html);
    }

    public String stripTags(String text) {
        return text == null ? null : PLAIN_TEXT.sanitize(text);
    }
}
