<template>
	<div
		class="flex justify-center items-center min-h-screen px-4 mx-auto max-w-7xl"
	>
		<div class="absolute z-0">
			<img
				class="object-cover object-top w-full h-full"
				src="../assets/img/login.jpg"
				alt=""
			/>
		</div>
		<div
			class="self-center max-w-96 px-12 py-8 rounded border-2 border-slate-500 overflow-hidden z-10 bg-slate-100"
		>
			<div class="w-full text-center text-2xl text-[#222121] py-2 my-4">
				简历信息抽取系统CMS
			</div>
			<el-form
				:model="param"
				:rules="rules"
				ref="login"
				label-width="0px"
				class="p-7.5"
			>
				<el-form-item prop="username">
					<el-input
						v-model="param.username"
						placeholder="管理员账户"
					>
						<template #prepend>
							<el-icon><User /></el-icon>
						</template>
					</el-input>
				</el-form-item>
				<el-form-item prop="password">
					<el-input
						type="password"
						placeholder="密码"
						v-model="param.password"
						@keyup.enter="submitForm()"
						show-password
					>
						<template #prepend>
							<el-icon><Lock /></el-icon>
						</template>
					</el-input>
				</el-form-item>
				<div class="text-center mt-4">
					<el-button
						type="primary"
                        class="inline-flex items-center justify-center w-full px-4 py-8 text-base font-semibold text-white transition-all duration-200 border border-transparent rounded-md bg-gradient-to-r from-fuchsia-600 to-blue-600 focus:outline-none hover:opacity-80 focus:opacity-80"
						@click="submitForm()"
						>登录</el-button
					>
				</div>
			</el-form>
		</div>
	</div>
</template>

<script setup>
	import { ref, reactive } from "vue";
	import { ElMessage } from "element-plus";
	import { useRouter } from "vue-router";
	import axios from "axios";
	const router = useRouter();
	const param = reactive({
		username: "",
		password: "",
	});
	const rules = {
		username: [
			{
				required: true,
				message: "请输入用户名",
				trigger: "blur",
			},
		],
		password: [
			{
				required: true,
				message: "请输入密码",
				trigger: "blur",
			},
		],
	};
	const login = ref(null);
	const submitForm = () => {
		login.value.validate((valid) => {
			if (valid) {
				axios({
					method: "post",
					url: "/IRAS/AdminLogin/",
					headers: {
						"Content-Type": "application/json",
					},
					data: {
						admin_account: param.username,
						password: param.password,
					},
				})
					.then((res) => {
						if (res.data.msg == "success") {
							ElMessage.success("登录成功");
							localStorage.setItem("ms_userid", res.data.admin_id);
							localStorage.setItem("ms_admin_token", res.data.token);
							localStorage.setItem("ms_username", param.username);
							router.push("/");
						} else if (res.data.code == 301) {
							ElMessage.error("登录失败，密码错误");
						} else {
							ElMessage.error("登录失败，账号不存在");
						}
					})
					.catch((error) => {
						ElMessage.error(error.response?.data?.detail || "登录失败，请检查账号和服务连接");
					});
			} else {
				ElMessage.error("输入不规范");
				return false;
			}
		});
	};
	const goBack = () => {
		router.go(-1);
	};
</script>
