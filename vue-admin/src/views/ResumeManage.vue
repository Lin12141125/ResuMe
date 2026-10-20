<template>
	<div class="flex flex-col">
		<div>
			<el-button @click="goBack()">返回</el-button>
			<div class="mt-4 text-lg antialiased font-medium">上传简历列表</div>
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
					prop="resumecode"
					label="简历ID"
					width="70"
					align="center"
				></el-table-column>
				<el-table-column
					prop="resumename"
					label="简历名"
					align="center"
				></el-table-column>
				<el-table-column
					label="状态"
					align="center"
				>
					<template #default="scope">
						<el-tag
							:type="
								scope.row.state === '已解析'
									? 'success'
									: scope.row.state === '正在解析'
									? ''
									: 'danger'
							"
							>{{ scope.row.state }}</el-tag
						>
					</template>
				</el-table-column>
				<el-table-column
					label="操作"
					width="180"
					align="center"
				>
					<template #default="scope">
						<el-button
							type="text"
							icon="el-icon-edit"
							@click="goTo(scope.$index, scope.row)"
						>
							查看
						</el-button>
						<el-button
							type="text"
							icon="el-icon-delete"
							class="red"
							@click="handleDelete(scope.$index, scope.row)"
						>
							删除
						</el-button>
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
	</div>
</template>

<script>
	import { ref, reactive, onMounted } from "vue";
	import { useRouter, useRoute } from "vue-router";
	import axios from "axios";
	import { ElMessage, ElMessageBox } from "element-plus";

	export default {
		name: "basetable",
		setup() {
			const route = useRoute();
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
			// 获取表格数据

			const goBack = () => {
				router.go(-1);
			};

			const goTo = (index, row) => {
				router.push({ path: "/resume", query: { resumecode: row.resumecode } });
			};

			const getData = () => {
				axios
					.get("/IRAS/FindUserResumes/", {
						params: {
							user_id: route.query.user_id,
							user_type: route.query.user_type,
						},
					})
					.then((res) => {
						if (res.data.msg == "success") {
							receiveData = res.data.data;
							tableData.value = receiveData;
							pageTotal.value = receiveData.length || 50;
						} else {
						}
					})
					.catch((error) => {
					});
			};

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
							.post("/IRAS/ResumeDelete/", {
								user_id: route.query.user_id,
								user_type: route.query.user_type,
								code_list: [row.resumecode],
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

			onMounted(() => {
				getData();
			});

			return {
				query,
				tableData,
				pageTotal,
				handlePageChange,
				handleDelete,
				goTo,
				goBack,
			};
		},
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
</style>
