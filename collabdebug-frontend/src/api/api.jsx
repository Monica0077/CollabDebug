import axios from "axios";

// Allow overriding backend base URL via environment variable VITE_API_BASE; default to localhost:8080
const BASE_URL = import.meta.env.VITE_API_BASE || "http://localhost:8080";

const API = axios.create({
	baseURL: `${BASE_URL}`,
});

// Attach JWT token if available
API.interceptors.request.use((config) => {
	const token = localStorage.getItem("token");
    console.log(token);
	if (token) {
		config.headers = config.headers || {};
		config.headers.Authorization = `Bearer ${token}`;
	}
	return config;
});

export default API;

