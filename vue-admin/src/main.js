import router from "./router";
import axios from "axios";
import store from "./store";

import "./assets/css/style.css";

import ElementPlus from "element-plus";
import "element-plus/dist/index.css";
import * as ElementPlusIconsVue from "@element-plus/icons-vue";

import { createApp } from "vue";
import App from "./App.vue";
import * as echarts from "echarts";

const app = createApp(App);
const apiBase = import.meta.env.VITE_API_BASE_URL || "http://127.0.0.1:8080";
const sessionClient = axios.create({ baseURL: apiBase, withCredentials: true });

axios.interceptors.request.use(async (config) => {
	if (config.url?.startsWith("/IRAS/") || config.url?.startsWith("/api/")) {
		config.baseURL = apiBase;
		config.withCredentials = true;
		if (!["get", "head", "options"].includes((config.method || "get").toLowerCase())) {
			const { data } = await sessionClient.get("/api/auth/csrf");
			config.headers["X-CSRF-TOKEN"] = data.token;
		}
	}
	return config;
});
axios.interceptors.response.use((response) => response, (error) => {
	if (error.response?.status === 401) {
		localStorage.removeItem("ms_admin_token");
		router.push("/login");
	}
	return Promise.reject(error);
});
app.config.globalProperties.$echarts = echarts;

app.use(store);
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
	app.component(key, component);
}
app.use(ElementPlus);
app.use(router);
app.mount("#app");
