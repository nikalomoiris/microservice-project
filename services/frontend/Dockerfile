FROM node:20-alpine AS base
WORKDIR /app

# Install pnpm
RUN npm install -g pnpm

FROM base AS deps
COPY package.json pnpm-workspace.yaml ./
COPY apps/web/package.json ./apps/web/
COPY packages/ui/package.json ./packages/ui/
RUN pnpm install --frozen-lockfile --prod=false

FROM base AS builder
COPY . .
RUN pnpm -w --filter apps/web build

FROM node:20-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
COPY --from=builder /app/apps/web/.next ./.next
COPY --from=builder /app/apps/web/public ./public
COPY --from=builder /app/apps/web/package.json ./package.json
RUN npm install -g pnpm && pnpm install --prod
EXPOSE 3000
WORKDIR /app
CMD ["pnpm", "start"]
