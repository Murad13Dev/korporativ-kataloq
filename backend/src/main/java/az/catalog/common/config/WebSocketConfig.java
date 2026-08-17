package az.catalog.common.config;

import az.catalog.common.security.AuthPrincipal;
import az.catalog.common.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

/**
 * STOMP over WebSocket for live chat.
 *
 * <p><b>The access model.</b> A visitor is anonymous by definition — there is no token to
 * check — so a conversation's channel is keyed by a 128-bit token the browser generates
 * and only it holds:
 *
 * <pre>/topic/chat/{visitorToken}</pre>
 *
 * The token <em>is</em> the capability. Guessing it is as hard as guessing a UUIDv4, and
 * it never appears in a URL, so it does not leak through referrers or server logs.
 *
 * <p>Admins authenticate normally: their {@code CONNECT} frame carries the bearer token,
 * and the inbox channel {@code /topic/admin/chat} is closed to anyone who is not staff.
 */
@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    public static final String TOPIC_CONVERSATION = "/topic/chat/";
    public static final String TOPIC_ADMIN_INBOX = "/topic/admin/chat";
    /** Live viewer-count broadcast for one product; anyone may subscribe, nothing sensitive rides on it. */
    public static final String TOPIC_PRODUCT_PRESENCE = "/topic/product/presence/";

    private final JwtService jwtService;
    private final AppProperties properties;

    @Override
    public void configureMessageBroker(@NonNull MessageBrokerRegistry registry) {
        // Simple in-memory broker: a single-node deployment needs nothing more, and
        // swapping in a relay later changes only this line.
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(@NonNull StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(
                        properties.getCors().getAllowedOrigins().toArray(String[]::new))
                // SockJS fallback keeps the widget working behind proxies that block
                // WebSocket upgrades, which is common on corporate networks.
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(@NonNull ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) {
                    return message;
                }

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    authenticate(accessor);
                }

                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    guardSubscription(accessor);
                }

                return message;
            }
        });
    }

    /** Attaches the admin principal when the CONNECT frame carries a valid bearer token. */
    private void authenticate(StompHeaderAccessor accessor) {
        List<String> authorization = accessor.getNativeHeader("Authorization");
        if (authorization == null || authorization.isEmpty()) {
            return;
        }

        String header = authorization.get(0);
        if (header == null || !header.startsWith("Bearer ")) {
            return;
        }

        try {
            AuthPrincipal principal = jwtService.parseAccessToken(header.substring(7).trim());
            accessor.setUser(new UsernamePasswordAuthenticationToken(
                    principal, null, principal.getAuthorities()));
        } catch (RuntimeException ex) {
            // An invalid token leaves the session anonymous rather than refusing the
            // connection — a visitor with a stale customer token must still get support.
            log.debug("Ignoring invalid bearer token on STOMP CONNECT: {}", ex.getMessage());
        }
    }

    /**
     * The admin inbox is staff-only. Conversation topics are not checked here: their
     * token is the capability, and validating it would mean a database round trip on
     * every subscribe.
     */
    private void guardSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(TOPIC_ADMIN_INBOX)) {
            return;
        }

        var user = accessor.getUser();
        boolean isAdmin = user instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof AuthPrincipal principal
                && principal.isAdmin();

        if (!isAdmin) {
            log.warn("Rejected non-admin subscription to {}", destination);
            throw new org.springframework.security.access.AccessDeniedException(
                    "Admin inbox requires staff authentication");
        }
    }
}
