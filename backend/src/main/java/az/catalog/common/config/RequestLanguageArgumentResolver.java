package az.catalog.common.config;

import az.catalog.modules.language.LanguageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@RequiredArgsConstructor
public class RequestLanguageArgumentResolver implements HandlerMethodArgumentResolver {

    private final LanguageService languageService;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(RequestLanguage.class)
                && String.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(@NonNull MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  @NonNull NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        String header = webRequest.getHeader("Accept-Language");
        return languageService.resolveOrDefault(extractPrimaryTag(header));
    }

    /**
     * Takes the first tag of an {@code Accept-Language} value and drops the region and
     * quality weight: {@code "ru-RU,ru;q=0.9,en;q=0.8"} → {@code "ru"}.
     */
    private String extractPrimaryTag(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        String first = header.split(",")[0].trim();
        int semicolon = first.indexOf(';');
        if (semicolon > 0) {
            first = first.substring(0, semicolon);
        }
        int dash = first.indexOf('-');
        return dash > 0 ? first.substring(0, dash) : first;
    }
}
