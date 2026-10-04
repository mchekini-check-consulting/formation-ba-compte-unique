# syntax=docker/dockerfile:1
# Frontend Angular -> fichiers statiques servis par nginx distroless, non-root.

# ---- Build : Node.js, jamais livré en production
FROM node:24-bookworm-slim AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# ---- Runtime : nginx Chainguard (distroless : sans shell, utilisateur 65532, port 8080)
# Image épinglée par digest (le tier gratuit ne publie que :latest)
FROM cgr.dev/chainguard/nginx:latest@sha256:a104d1995e56b7a15e8f152078dfbdb1ecbf9f9d1af311e7906e7b4c0c790cf2
COPY nginx.conf /etc/nginx/conf.d/nginx.default.conf
COPY --from=build /app/dist/app/browser /usr/share/nginx/app
USER 65532
EXPOSE 8080
# Image construite par la CI (.github/workflows/ci.yaml), taguée avec le sha court du commit
