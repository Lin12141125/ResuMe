import {createRouter, createWebHashHistory} from "vue-router";
import Home from "../views/Home.vue";
import axios from "axios";

const routes = [
    {
        path: '/',
        redirect: '/index'
    }, {
        path: "/",
        name: "Home",
        component: Home,
        children: [
            {
                path: "/index",
                name: "index",
                meta: {
                    title: '用户管理'
                },
                component: () => import ( /* webpackChunkName: "index" */ "../views/ManageUser.vue")
            },
            {
                path: "/resumesManage",
                name: "resumesManage",
                meta: {
                    title: '简历管理'
                },
                component: () => import ( /* webpackChunkName: "resumesManage" */ "../views/ResumeManage.vue")
            },
            {
                path: "/resume",
                name: "resume",
                meta: {
                    title: '解析结果展示'
                },
                component: () => import ( /* webpackChunkName: "charts" */ "../views/Resume.vue")
            }, 
        ]
    }, {
        path: "/login",
        name: "Login",
        meta: {
            title: '登录'
        },
        component: () => import ( /* webpackChunkName: "login" */ "../views/Login.vue")
    }, 
];

const router = createRouter({
    history: createWebHashHistory(),
    routes
});

router.beforeEach(async (to) => {
    document.title = `${to.meta.title} |智能简历解析系统`;
    if (to.path !== '/login') {
        try {
            const { data } = await axios.get('/api/auth/me');
            if (data.data.role !== 'ADMIN') throw new Error('Administrator session required');
            localStorage.setItem('ms_admin_token', 'session');
            localStorage.setItem('ms_username', data.data.account);
        } catch {
            localStorage.removeItem('ms_admin_token');
            return '/login';
        }
    }
});

export default router;