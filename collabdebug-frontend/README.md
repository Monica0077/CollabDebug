# React + Vite

This template provides a minimal setup to get React working in Vite with HMR and some ESLint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Babel](https://babeljs.io/) (or [oxc](https://oxc.rs) when used in [rolldown-vite](https://vite.dev/guide/rolldown)) for Fast Refresh
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/) for Fast Refresh

## React Compiler

The React Compiler is enabled on this template. See [this documentation](https://react.dev/learn/react-compiler) for more information.

Note: This will impact Vite dev & build performances.

## Expanding the ESLint configuration

If you are developing a production application, we recommend using TypeScript with type-aware lint rules enabled. Check out the [TS template](https://github.com/vitejs/vite/tree/main/packages/create-vite/template-react-ts) for information on how to integrate TypeScript and [`typescript-eslint`](https://typescript-eslint.io) in your project.

## Local development with backend

The frontend expects the backend API to be available on http://localhost:8080 by default. The Vite dev server runs on port 5174 which matches the backend CORS configuration.

- Start the backend (Spring Boot) on port 8080.
- Start the frontend dev server from this folder:

	# install deps
	npm install

	# start vite dev server (runs on port 5174)
	npm run dev

If your backend runs on a different host/port, set the environment variable when starting Vite:

	# Windows PowerShell example
	$Env:VITE_API_BASE = 'http://localhost:8080'; npm run dev

The frontend will send authentication requests to `/api/auth/*` and sandbox file uploads to `/sandbox/create`.

## Running tests

We included a small unit test using Vitest + Testing Library. To run tests locally:

1. Install dev dependencies:

	npm install --save-dev vitest @testing-library/react @testing-library/jest-dom @testing-library/user-event

2. Run tests:

	npm test

