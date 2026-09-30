# ---------- build ----------
FROM node:20-alpine AS build
WORKDIR /app

ENV NG_CLI_ANALYTICS=false \
    CI=true

COPY package.json package-lock.json* ./
RUN if [ -f package-lock.json ]; then npm ci --no-audit --no-fund; \
    else npm install --no-audit --no-fund; fi

COPY . .
# Unit tests run in the CI "admin-app" job; the image only builds the production bundle.
RUN npx ng build --configuration production

# ---------- runtime ----------
FROM nginx:1.27-alpine

ENV API_GATEWAY_URL=http://api-gateway:8080

COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist/admin-app/browser /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=3s --retries=5 \
  CMD wget -q --spider http://127.0.0.1/healthz || exit 1
