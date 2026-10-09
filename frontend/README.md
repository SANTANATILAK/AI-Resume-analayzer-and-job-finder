# InternMatch frontend (React + Vite)
Run locally: `npm install && npm run dev` -> http://localhost:5173 (set VITE_API_URL in .env, see .env.example)

## Deploy on Vercel
1. Push this folder as its own GitHub repo. Import it in Vercel (framework: Vite).
2. Add environment variable VITE_API_URL = your deployed backend URL (no trailing slash). Deploy.
3. Copy the Vercel URL into the backend's FRONTEND_URL variable and redeploy the backend (CORS).
Netlify also works: build `npm run build`, publish `dist` (public/_redirects handles routing).
