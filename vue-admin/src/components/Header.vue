<template>
	<div class="header">
		<!-- 折叠按钮 -->
		<div
			class="collapse-btn"
			@click="collapseChage"
		>
			<el-icon v-if="!collapse">
				<Fold />
			</el-icon>
			<el-icon v-else>
				<Expand />
			</el-icon>
		</div>
		<div class="logo">简历信息抽取系统CMS</div>
		<div class="header-right">
			<div class="header-user-con">
				<div class="user-avator">
					<el-avatar
						src="https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png"
					/>
				</div>
				<!-- 用户名下拉菜单 -->
				<el-dropdown
					class="user-name"
					trigger="click"
					@command="handleCommand"
				>
					<span class="el-dropdown-link">
						{{ username }}
						<el-icon>
							<CaretBottom />
						</el-icon>
					</span>
					<template #dropdown>
						<el-dropdown-menu>
							<el-dropdown-item
								divided
								command="loginout"
								>退出登录</el-dropdown-item
							>
						</el-dropdown-menu>
					</template>
				</el-dropdown>
			</div>
		</div>
	</div>
</template>
<script setup>
	import { computed, onMounted } from "vue";
	import { useStore } from "vuex";
	import { useRouter } from "vue-router";
	import axios from "axios";
	import { ElMessage } from "element-plus";
	const username = localStorage.getItem("ms_username");
	const message = 2;

	const store = useStore();
	const collapse = computed(() => store.state.collapse);
	// 侧边栏折叠
	const collapseChage = () => {
		store.commit("handleCollapse", !collapse.value);
	};

	onMounted(() => {
		if (document.body.clientWidth < 1500) {
			collapseChage();
		}
	});

	// 用户名下拉菜单选择事件
	const router = useRouter();
	const handleCommand = async (command) => {
		if (command == "loginout") {
			try { await axios.post("/api/auth/logout"); }
			catch { ElMessage.error("退出失败，请重试"); return; }
			localStorage.removeItem("ms_username");
			localStorage.removeItem("ms_admin_token");
			router.push("/login");
		}
	};
</script>
<style scoped>
	.header {
		position: relative;
		box-sizing: border-box;
		width: 100%;
		height: 70px;
		font-size: 22px;
		color: #fff;
	}

	.collapse-btn {
		float: left;
		padding: 0 21px;
		cursor: pointer;
		line-height: 70px;
	}

	.header .logo {
		float: left;
		width: 250px;
		line-height: 70px;
	}

	.header-right {
		float: right;
		padding-right: 50px;
	}

	.header-user-con {
		display: flex;
		height: 70px;
		align-items: center;
	}

	.btn-fullscreen {
		transform: rotate(45deg);
		margin-right: 5px;
		font-size: 24px;
	}

	.btn-bell,
	.btn-fullscreen {
		position: relative;
		width: 30px;
		height: 30px;
		text-align: center;
		border-radius: 15px;
		cursor: pointer;
	}

	.btn-bell-badge {
		position: absolute;
		right: 0;
		top: -2px;
		width: 8px;
		height: 8px;
		border-radius: 4px;
		background: #f56c6c;
		color: #fff;
	}

	.btn-bell .el-icon-bell {
		color: #fff;
	}

	.user-name {
		margin-left: 10px;
	}

	.user-avator {
		margin-left: 20px;
	}

	.user-avator img {
		display: block;
		width: 40px;
		height: 40px;
		border-radius: 50%;
	}

	.el-dropdown-link {
		color: #fff;
		cursor: pointer;
	}

	.el-dropdown-menu__item {
		text-align: center;
	}
</style>
