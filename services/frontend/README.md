# Frontend (Next.js) workspace

This folder contains the frontend Next.js application and shared UI packages.

Getting started (local dev):

1. Install pnpm (if not already):

```bash
npm install -g pnpm
```

2. Install dependencies and start dev server:

```bash
cd services/frontend
pnpm install
pnpm dev
```

Dev via docker-compose (with backend services):

```bash
# from repo root
docker-compose -f docker-compose.yml -f services/frontend/docker-compose.override.yml up --build
```

Notes:
- This is an initial scaffold. API client generation and Tailwind setup will be added in follow-up PRs.