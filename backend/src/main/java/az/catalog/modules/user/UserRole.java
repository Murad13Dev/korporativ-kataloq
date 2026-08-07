package az.catalog.modules.user;

/**
 * Back-office roles, ordered from most to least privileged.
 *
 * <ul>
 *   <li>{@code SUPER_ADMIN} — everything, including managing other admins</li>
 *   <li>{@code ADMIN} — everything except user management</li>
 *   <li>{@code EDITOR} — catalog, content and media; no orders, payments or settings</li>
 *   <li>{@code SUPPORT} — orders, contact requests and chat; read-only catalog</li>
 * </ul>
 */
public enum UserRole {
    SUPER_ADMIN,
    ADMIN,
    EDITOR,
    SUPPORT
}
