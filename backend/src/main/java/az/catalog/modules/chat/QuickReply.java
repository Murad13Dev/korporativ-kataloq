package az.catalog.modules.chat;

import az.catalog.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Canned reply the support team can insert with one click. */
@Entity
@Table(name = "chat_quick_replies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuickReply extends BaseEntity {

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    /** {@code null} means the reply applies to every language. */
    @Column(name = "language_code", length = 8)
    private String languageCode;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
