# =============================================================================
# Stage 1 — build the SPA
# =============================================================================
FROM node:22-alpine AS build

WORKDIR /build

COPY package.json package-lock.json* ./
RUN npm ci --no-audit --no-fund

COPY . .

ARG VITE_API_URL=/api
ENV VITE_API_URL=${VITE_API_URL}

RUN npm run build


# =============================================================================
# Stage 2 — serve with nginx, which also proxies /api and /uploads
# =============================================================================
FROM nginx:1.27-alpine

RUN apk add --no-cache wget

COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY proxy_params_catalog /etc/nginx/proxy_params_catalog

# Mount point for the shared uploads volume.
RUN mkdir -p /usr/share/nginx/uploads

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD wget -qO- http://localhost/healthz || exit 1

CMD ["nginx", "-g", "daemon off;"]
