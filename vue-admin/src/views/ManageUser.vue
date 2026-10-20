<template>
	<div class="flex flex-col">
		<div>
            <div class="mt-4 text-lg antialiased font-medium">
                用户列表
            </div>
			<el-button
				class="py-4 mb-2"
				type="primary"
				@click="handleEdit"
				>新增用户</el-button
			>
		</div>
		<div class="flex flex-col w-full pt-4">
			<el-table
				:data="tableData"
				stripe
				class="w-full rounded-xl z-10 shadow-md hover:shadow-lg transition-all"
				ref="multipleTable"
				header-cell-class-name="table-header"
			>
				<el-table-column
					prop="user_id"
					label="用户ID"
					width="70"
					align="center"
				></el-table-column>
				<el-table-column
					prop="user_type"
					label="用户类型"
					width="100"
					align="center"
				></el-table-column>
				<el-table-column
					prop="user_account"
					label="用户名"
					align="center"
				></el-table-column>
				<el-table-column
					label="操作"
					width="200"
					align="center"
				>
					<template #default="scope">
						<el-button
							type="text"
							icon="el-icon-edit"
							@click="goTo(scope.$index, scope.row)"
							>查看简历
						</el-button>
						<el-button
							type="text"
							icon="el-icon-delete"
							class="red"
							@click="handleDelete(scope.$index, scope.row)"
							>删除用户</el-button
						>
					</template>
				</el-table-column>
			</el-table>
			<div class="self-end mt-8">
				<el-pagination
					background
					layout="total, prev, pager, next"
					:current-page="query.pageIndex"
					:page-size="query.pageSize"
					:total="pageTotal"
					@current-change="handlePageChange"
				></el-pagination>
			</div>
		</div>

		<!-- 编辑弹出框 -->
		<el-dialog
			title="新增用户"
			v-model="editVisible"
			width="60%"
		>
			<el-form
				:model="param"
				:rules="rules"
				ref="register"
			>
				<el-form-item prop="username">
					<el-input
						v-model="param.username"
						placeholder="username"
					>
						<template #prepend>
							<el-icon>
								<User />
							</el-icon>
						</template>
					</el-input>
				</el-form-item>
				<el-form-item prop="password">
					<el-input
						type="password"
						placeholder="password"
						v-model="param.password"
						show-password
					>
						<template #prepend>
							<el-icon>
								<Lock />
							</el-icon>
						</template>
					</el-input>
				</el-form-item>
				<el-form-item prop="passwordconfirm">
					<el-input
						type="password"
						placeholder="passwordconfirm"
						v-model="param.passwordconfirm"
						@keyup.enter="submitForm()"
						show-password
					>
						<template #prepend>
							<el-icon>
								<Lock />
							</el-icon>
						</template>
					</el-input>
				</el-form-item>
			</el-form>
			<template #footer>
				<span class="dialog-footer">
					<el-button @click="editVisible = false">取 消</el-button>
					<el-button
						type="primary"
						@click="submitForm()"
						>确 定</el-button
					>
				</span>
			</template>
		</el-dialog>
	</div>
</template>

<script setup>
	import { ref, reactive } from "vue";
	import { useRouter } from "vue-router";
	import axios from "axios";
	import { ElMessage, ElMessageBox } from "element-plus";

	const router = useRouter();
	var receiveData = new Array();
	const query = reactive({
		address: "",
		name: "",
		pageIndex: 1,
		pageSize: 10,
	});
	const tableData = ref([]);
	const pageTotal = ref(0);
	const goTo = (index, row) => {
		router.push({
			path: "/resumesManage",
			query: {
				user_id: row.user_id,
				user_type: row.user_type == "求职者" ? 0 : 1,
			},
		});
	};
	// 获取表格数据
	const getData = () => {
		axios
			.get("/IRAS/FindAllUsers/")
			.then((res) => {
				if (res.data.msg == "success") {
					receiveData = res.data.data;
					tableData.value = receiveData;
					pageTotal.value = receiveData.length || 50;
				} else {
					ElMessage.error("查询失败");
				}
			})
			.catch((error) => {
			});
	};
	getData();
	// 分页导航
	const handlePageChange = (val) => {
		query.pageIndex = val;
		getData();
	};
	// 删除操作
	const handleDelete = (index, row) => {
		// 二次确认删除
		ElMessageBox.confirm("确定要删除吗？", "提示", {
			type: "warning",
		})
			.then(() => {
				axios
					.post("/IRAS/UserDelete/", {
						user_id: row.user_id,
					})
					.then((res) => {
						if (res.data.msg == "success") {
							ElMessage.success("删除成功");
						} else {
							ElMessage.error("删除失败");
						}
					})
					.catch((error) => {
					})
					.finally(() => {
						getData();
					});
			})
			.catch(() => {});
	};

	// 表格编辑时弹窗和保存
	const editVisible = ref(false);
	const handleEdit = () => {
		editVisible.value = true;
	};

	const param = reactive({
		username: "",
		passwordconfirm: "",
		password: "",
		usertype: 1,
	});

	var validatePass = (rule, value, callback) => {
		if (value == "") {
			callback(new Error("请再次输入密码"));
		} else if (value !== param.password) {
			callback(new Error("两次输入密码不一致"));
		} else {
			callback();
		}
	};
	const rules = {
		usertype: [
			{
				required: true,
				message: "请选择用户类型",
				trigger: "blur",
			},
		],
		username: [
			{
				required: true,
				message: "请输入用户名",
				trigger: "blur",
			},
		],
		password: [{ required: true, min: 10, message: "密码至少10个字符", trigger: "blur" }],
		passwordconfirm: [
			{ required: true, validator: validatePass, trigger: "blur" },
		],
	};
	const register = ref(null);
	const submitForm = () => {
		register.value.validate((valid) => {
			if (valid) {
				axios({
					method: "post",
					url: "/api/admin/users",
					headers: {
						"Content-Type": "application/json",
					},
					data: {
						user_account: param.username,
						password: param.password,
						user_type: param.usertype == "求职者Seeker" ? 0 : 1,
					},
				})
					.then((res) => {
						if (res.data.msg == "success") {
							ElMessage.success("注册成功");
							getData();
						} else {
							ElMessage.error("注册失败，用户名重复");
						}
					})
					.catch((error) => {
					});
			} else {
				ElMessage.error("输入不规范");
			}
			editVisible.value = false;
			register.value.resetFields();
		});
	};
</script>

<style scoped>
	.handle-box {
		margin-bottom: 20px;
	}

	.handle-select {
		width: 120px;
	}

	.handle-input {
		width: 300px;
		display: inline-block;
	}

	.table {
		width: 100%;
		font-size: 14px;
	}

	.red {
		color: #ff0000;
	}

	.mr10 {
		margin-right: 10px;
	}

	.table-td-thumb {
		display: block;
		margin: auto;
		width: 40px;
		height: 40px;
	}

	.breadcrumb-button {
		margin-left: auto;
		height: 28px;
		width: 80px;
	}

	.breadcrumb-container {
		display: flex;
		align-items: center;
	}
</style>
