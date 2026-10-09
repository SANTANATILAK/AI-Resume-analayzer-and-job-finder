# AI Resume Analyzer and Job Finder

Upload a resume, see the skills found, and get companies and roles ranked by fit.

- `frontend/` React + Vite (deploy on Vercel, root directory `frontend`)
- `backend/` Spring Boot 3 + MySQL (deploy on Render with Docker, root directory `backend`)

Backend environment variables: DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET, ADMIN_EMAIL, FRONTEND_URL.
Frontend environment variable: VITE_API_URL (the backend URL, no trailing slash).
Roles in the database are sample roles built from typical requirements, not confirmed vacancies.
