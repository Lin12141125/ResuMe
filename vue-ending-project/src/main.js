import { createApp } from "vue";
import { createPinia } from "pinia";
import axios from "axios";

import App from "./App.vue";
import router from "./router";

import * as ElementPlusIconsVue from "@element-plus/icons-vue";
import ElementPlus, { ElMessage } from "element-plus";
import "./assets/main.css";
import "element-plus/dist/index.css";

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
		localStorage.removeItem("ms_token");
		localStorage.removeItem("ms_userid");
		localStorage.removeItem("ms_username");
		router.push("/Signin");
	}
	return Promise.reject(error);
});

app.use(createPinia());
app.use(router);

router.beforeEach(async (to, from) => {
	if (["/upload", "/user", "/statistic", "/resume", "/agent"].includes(to.path.toLowerCase())) {
		try {
			const { data } = await sessionClient.get("/api/auth/me");
			if (data.data.role !== "USER") throw new Error("Recruiter session required");
			localStorage.setItem("ms_userid", data.data.id);
			localStorage.setItem("ms_username", data.data.account);
			localStorage.setItem("ms_token", "session");
		} catch {
			for (const key of ["ms_userid", "ms_username", "ms_token"]) localStorage.removeItem(key);
			ElMessage.warning("请先登录招聘者账号");
			return "/Signin";
		}
	}
});

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
	app.component(key, component);
}
app.use(ElementPlus);
app.mount("#app");
