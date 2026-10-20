<template>
	<main class="agent-workspace" v-loading="busy">
		<header class="workspace-heading">
			<div><p class="eyebrow">IRAS / 招聘工作台</p><h1>岗位与证据</h1></div>
			<el-button :icon="Refresh" aria-label="刷新工作台" title="刷新工作台" @click="perform(load)">刷新</el-button>
		</header>
		<el-alert v-if="error" :title="error" type="error" show-icon @close="error = ''" />
		<el-tabs v-model="tab">
			<el-tab-pane label="岗位匹配" name="jobs">
				<div class="workspace-grid">
					<aside class="job-sidebar">
						<el-button :icon="Plus" type="primary" @click="newJob">新建岗位</el-button>
						<ul class="job-list">
							<li v-for="item in jobs" :key="item.id"><button :class="{ selected: job.id === item.id }" @click="perform(() => selectJob(item.id))"><strong>{{ item.title }}</strong><span>{{ item.confirmed ? '已确认' : '待确认' }} · v{{ item.version }}</span></button></li>
						</ul>
						<el-empty v-if="!jobs.length" description="暂无岗位" :image-size="64" />
					</aside>
					<section class="job-content">
						<h2>{{ job.id ? '岗位要求' : '新建岗位' }}</h2>
						<el-form label-position="top" @submit.prevent="saveJob">
							<el-form-item label="岗位名称"><el-input v-model="job.title" maxlength="160" /></el-form-item>
							<el-form-item label="职位描述"><el-input v-model="job.description" type="textarea" :rows="5" maxlength="10000" /></el-form-item>
							<div v-if="job.id" class="requirement-grid">
								<el-form-item label="必需项"><el-input v-model="mustHave" type="textarea" :rows="4" @input="dirty = true" /></el-form-item>
								<el-form-item label="加分项"><el-input v-model="preferred" type="textarea" :rows="4" @input="dirty = true" /></el-form-item>
								<el-form-item label="待确认问题"><el-input v-model="questions" type="textarea" :rows="3" @input="dirty = true" /></el-form-item>
							</div>
							<div class="actions">
								<el-button type="primary" :icon="DocumentChecked" @click="saveJob">{{ job.id ? '保存修改' : '解析并创建' }}</el-button>
								<el-button v-if="job.id" :disabled="dirty || job.confirmed || !!questions.trim()" :icon="Check" @click="confirmJob">确认要求</el-button>
								<el-button v-if="job.id" :icon="Delete" type="danger" plain aria-label="删除岗位" title="删除岗位" @click="deleteJob" />
								<el-tag v-if="job.id" :type="job.confirmed && !dirty ? 'success' : 'warning'">{{ job.confirmed && !dirty ? '已确认' : '待确认' }}</el-tag>
							</div>
						</el-form>
						<section v-if="job.id" class="task-section">
							<h2>匹配任务</h2>
							<el-input v-model="requestText" type="textarea" :rows="2" maxlength="4000" aria-label="匹配任务要求" />
							<div class="actions"><el-button type="primary" :icon="VideoPlay" :disabled="!job.confirmed || dirty || activeTask" @click="startTask">开始匹配</el-button><el-button v-if="activeTask" :icon="Close" @click="cancelTask">取消任务</el-button></div>
							<div v-if="task" class="task-progress">
								<div class="actions"><el-tag>{{ stateNames[task.state] || task.state }}</el-tag><span class="muted">{{ task.id }}</span><el-button :icon="Refresh" circle title="刷新任务" aria-label="刷新任务" @click="perform(() => refreshTask(task.id))" /></div>
								<el-alert v-if="task.error" type="error" :title="task.error" :closable="false" />
								<el-timeline><el-timeline-item v-for="(event, index) in task.trace" :key="index">{{ event.step === 'TOOL' ? event.tool : `模型第 ${event.round} 轮` }}<span v-if="event.elapsedMs" class="muted"> · {{ (event.elapsedMs / 1000).toFixed(1) }}s</span></el-timeline-item></el-timeline>
								<div v-if="task.state === 'WAITING'"><p>{{ task.clarification }}</p><el-input v-model="answer" type="textarea" :rows="2" aria-label="澄清答复" /><el-button class="reply-button" :icon="Promotion" type="primary" :disabled="!answer.trim()" @click="replyTask">提交答复</el-button></div>
							</div>
						</section>
					</section>
				</div>
			</el-tab-pane>
			<el-tab-pane label="报告与审批" name="reports">
				<div class="report-heading"><el-select v-model="reportId" placeholder="选择报告" @change="value => perform(() => loadReport(value))"><el-option v-for="item in reports" :key="item.id" :value="item.id" :label="`${item.status} · 岗位 ${item.job_id} · ${new Date(item.created_at).toLocaleString()}`" /></el-select><el-button v-if="report" :icon="Check" type="primary" :disabled="report.status !== 'DRAFT'" @click="approve">确认报告</el-button></div>
				<el-empty v-if="!report" description="暂无选中报告" />
				<section v-else class="report-content">
					<div class="actions"><h2>证据对照</h2><el-tag :type="report.status === 'STALE' ? 'danger' : 'info'">{{ report.status }}</el-tag></div>
					<el-alert title="仅供人工参考，不作为自动录用或拒绝依据" type="warning" :closable="false" />
					<el-empty v-if="!report.body.candidates.length" description="未找到候选简历" />
					<section v-for="candidate in report.body.candidates" :key="candidate.resumeId" class="candidate-section">
						<h3>{{ resumeName(candidate.resumeId) }}</h3>
						<el-table class="desktop-assessments" :data="candidate.assessments" stripe><el-table-column prop="requirement" label="岗位要求" min-width="150" /><el-table-column label="证据状态" width="120"><template #default="{ row }"><el-tag :type="row.status === 'EVIDENCE' ? 'success' : row.status === 'UNKNOWN' ? 'info' : 'warning'">{{ evidenceNames[row.status] }}</el-tag></template></el-table-column><el-table-column label="来源" min-width="220"><template #default="{ row }"><div v-for="(citation, index) in row.citations" :key="index"><el-button link type="primary" @click="showEvidence(candidate.resumeId, citation.evidenceId)">#{{ citation.evidenceId }}</el-button><blockquote>{{ citation.quote }}</blockquote></div><span v-if="!row.citations.length" class="muted">未找到依据</span></template></el-table-column></el-table>
						<div class="mobile-assessments"><section v-for="assessment in candidate.assessments" :key="assessment.requirement" class="mobile-assessment"><div class="actions"><strong>{{ assessment.requirement }}</strong><el-tag>{{ evidenceNames[assessment.status] }}</el-tag></div><div v-for="(citation, index) in assessment.citations" :key="index"><el-button link type="primary" @click="showEvidence(candidate.resumeId, citation.evidenceId)">#{{ citation.evidenceId }}</el-button><blockquote>{{ citation.quote }}</blockquote></div><span v-if="!assessment.citations.length" class="muted">未找到依据</span></section></div>
						<h4>面试问题</h4><ol><li v-for="question in candidate.interviewQuestions" :key="question">{{ question }}</li></ol>
					</section>
				</section>
			</el-tab-pane>
			<el-tab-pane label="简历校对" name="corrections">
				<el-select v-model="resumeId" placeholder="选择简历" @change="value => perform(() => loadExtraction(value))"><el-option v-for="item in resumes" :key="item.resumecode" :value="item.resumecode" :label="item.resumename" /></el-select>
				<el-form v-if="resumeId" label-position="top" class="correction-form"><el-form-item label="结构化资料 (JSON)"><el-input v-model="extraction" type="textarea" :rows="18" aria-label="结构化资料 JSON" /></el-form-item><el-button type="primary" :icon="DocumentChecked" @click="correctResume">保存新版本</el-button></el-form>
			</el-tab-pane>
		</el-tabs>
		<el-dialog v-model="evidenceOpen" title="证据来源" width="min(720px, 94vw)"><div v-for="item in evidence" :key="item.id"><p class="muted">#{{ item.id }} · {{ item.source === 'CORRECTION' ? '人工校对' : '文档原文' }} · 版本 {{ item.version }} · {{ item.page ? `第 ${item.page} 页` : '无页码' }} · 片段 {{ item.paragraph }}</p><pre>{{ item.content }}</pre></div></el-dialog>
	</main>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from "vue";
import axios from "axios";
import { ElMessage, ElMessageBox } from "element-plus";
import { Refresh, Plus, Check, DocumentChecked, VideoPlay, Close, Promotion, Delete } from "@element-plus/icons-vue";

const tab = ref("jobs");
const busy = ref(false);
const error = ref("");
const jobs = ref([]);
const resumes = ref([]);
const reports = ref([]);
const job = ref({ title: "", description: "" });
const mustHave = ref("");
const preferred = ref("");
const questions = ref("");
const dirty = ref(false);
const requestText = ref("按已确认的岗位要求检索我的简历，逐项核对证据，并生成面试问题。未知项保持未知，不进行录用或拒绝判断。");
const task = ref(null);
const answer = ref("");
const reportId = ref("");
const report = ref(null);
const resumeId = ref(null);
const extraction = ref("");
const evidenceOpen = ref(false);
const evidence = ref([]);
let stream;
const activeTask = computed(() => task.value && ["QUEUED", "RUNNING", "WAITING"].includes(task.value.state));
const stateNames = { QUEUED: "排队中", RUNNING: "核对中", WAITING: "待澄清", COMPLETED: "已生成草稿", FAILED: "失败", CANCELLED: "已取消" };
const evidenceNames = { EVIDENCE: "有证据", UNKNOWN: "未知", CONTRADICTION: "存在矛盾" };
const taskKey = () => `iras-task-${localStorage.getItem("ms_userid")}`;
const lines = value => value.split("\n").map(item => item.trim()).filter(Boolean);
watch(() => [job.value.title, job.value.description], () => { dirty.value = true; }, { flush: "sync" });

async function perform(action) {
	if (busy.value) return;
	busy.value = true;
	error.value = "";
	try { await action(); }
	catch (failure) { error.value = failure.response?.data?.detail || failure.message || "请求失败"; }
	finally { busy.value = false; }
}
async function load() {
	const responses = await Promise.all([axios.get("/api/jobs"), axios.get("/api/resumes"), axios.get("/api/reports")]);
	[jobs.value, resumes.value, reports.value] = responses.map(response => response.data.data);
}
function newJob() { job.value = { title: "", description: "" }; mustHave.value = preferred.value = questions.value = ""; dirty.value = false; }
async function selectJob(id) {
	job.value = (await axios.get(`/api/jobs/${id}`)).data.data;
	mustHave.value = job.value.requirements.mustHave.join("\n");
	preferred.value = job.value.requirements.preferred.join("\n");
	questions.value = job.value.requirements.questions.join("\n");
	dirty.value = false;
}
function saveJob() { perform(async () => {
	if (!job.value.title.trim() || !job.value.description.trim()) throw new Error("请填写岗位名称和职位描述");
	const input = { title: job.value.title, description: job.value.description, requirements: { mustHave: lines(mustHave.value), preferred: lines(preferred.value), questions: lines(questions.value) } };
	const response = job.value.id ? await axios.put(`/api/jobs/${job.value.id}`, input) : await axios.post("/api/jobs", input);
	await load(); await selectJob(response.data.data.id); ElMessage.success("岗位已保存");
}); }
function confirmJob() { perform(async () => { await axios.post(`/api/jobs/${job.value.id}/confirm`); await selectJob(job.value.id); await load(); }); }
function deleteJob() { perform(async () => {
	try { await ElMessageBox.confirm("删除岗位也会删除其任务与报告，简历将保留。", "删除岗位", { type: "warning" }); } catch { return; }
	const id = job.value.id;
	await axios.delete(`/api/jobs/${id}`);
	if (task.value?.job_id === id) { stream?.close(); task.value = null; localStorage.removeItem(taskKey()); }
	report.value = null; reportId.value = ""; newJob(); await load();
}); }
function startTask() { perform(async () => {
	task.value = (await axios.post("/api/agent/tasks", { jobId: job.value.id, request: requestText.value })).data.data;
	localStorage.setItem(taskKey(), task.value.id); subscribe(task.value.id);
}); }
function subscribe(id) {
	stream?.close();
	const base = (import.meta.env.VITE_API_BASE_URL || "http://127.0.0.1:8080").replace(/\/$/, "");
	stream = new EventSource(`${base}/api/agent/tasks/${id}/events`, { withCredentials: true });
	stream.addEventListener("progress", async event => {
		task.value = JSON.parse(event.data);
		if (!["QUEUED", "RUNNING"].includes(task.value.state)) {
			stream?.close();
			if (task.value.reportId) {
				try { await load(); await loadReport(task.value.reportId); tab.value = "reports"; }
				catch (failure) { error.value = failure.response?.data?.detail || "报告加载失败"; }
			}
		}
	});
	stream.onerror = () => { stream?.close(); error.value = "进度连接已断开，可刷新任务查看最新状态"; };
}
async function refreshTask(id) {
	task.value = (await axios.get(`/api/agent/tasks/${id}`)).data.data;
	if (!job.value.id && task.value.job_id) await selectJob(task.value.job_id);
	if (["QUEUED", "RUNNING"].includes(task.value.state)) subscribe(id);
	if (task.value.reportId) await loadReport(task.value.reportId);
}
function cancelTask() { perform(async () => { task.value = (await axios.post(`/api/agent/tasks/${task.value.id}/cancel`)).data.data; stream?.close(); }); }
function replyTask() { perform(async () => { task.value = (await axios.post(`/api/agent/tasks/${task.value.id}/reply`, { answer: answer.value })).data.data; answer.value = ""; subscribe(task.value.id); }); }
async function loadReport(id) { report.value = (await axios.get(`/api/reports/${id}`)).data.data; reportId.value = id; }
function approve() { perform(async () => {
	try { await ElMessageBox.confirm("确认已核对证据来源、版本与未知项？", "人工确认", { type: "warning" }); }
	catch { return; }
	report.value = (await axios.post(`/api/reports/${report.value.id}/approve`)).data.data; await load();
}); }
function resumeName(id) { return resumes.value.find(item => item.resumecode === id)?.resumename || `简历 ${id}`; }
function showEvidence(id, evidenceId) { perform(async () => {
	evidence.value = (await axios.get(`/api/resumes/${id}/evidence`)).data.data.filter(item => item.id === evidenceId); evidenceOpen.value = true;
}); }
async function loadExtraction(id) { extraction.value = JSON.stringify((await axios.get("/IRAS/SingleResumePresent/", { params: { resumecode: id } })).data.data, null, 2); }
function correctResume() { perform(async () => {
	let input;
	try { input = JSON.parse(extraction.value); } catch { throw new Error("JSON 格式不正确"); }
	try { await ElMessageBox.confirm("保存新版本会使相关报告过期，并取消正在进行的匹配任务。", "确认纠错", { type: "warning" }); } catch { return; }
	await axios.put(`/api/resumes/${resumeId.value}/extraction`, input); await load();
	if (reportId.value) await loadReport(reportId.value);
	ElMessage.success("新版本已保存");
}); }
onMounted(() => perform(async () => { await load(); const id = localStorage.getItem(taskKey()); if (id) { try { await refreshTask(id); } catch { localStorage.removeItem(taskKey()); } } }));
onBeforeUnmount(() => stream?.close());
</script>

<style scoped>
.agent-workspace { max-width: 1240px; margin: 28px auto 64px; padding: 0 24px; color: #263442; }
.workspace-heading, .report-heading, .actions { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.workspace-heading { margin-bottom: 24px; }
.eyebrow { font-size: 12px; color: #64767d; margin: 0 0 6px; }
h1 { font-size: 28px; font-weight: 650; margin: 0; }
h2 { font-size: 20px; margin: 0 0 18px; font-weight: 600; }
h3 { font-size: 18px; margin: 0 0 14px; overflow-wrap: anywhere; }
h4 { margin: 20px 0 10px; }
.workspace-grid { display: grid; grid-template-columns: 240px minmax(0, 1fr); gap: 28px; }
.job-sidebar { border-right: 1px solid #dde4e7; padding-right: 24px; }
.job-list { list-style: none; padding: 0; margin: 20px 0; }
.job-list button { text-align: left; width: 100%; padding: 14px 12px; border-bottom: 1px solid #e5eaed; border-left: 3px solid transparent; background: transparent; overflow-wrap: anywhere; }
.job-list button.selected { background: #edf6f3; border-left-color: #167765; }
.job-list strong, .job-list span { display: block; }
.job-list span, .muted { font-size: 12px; color: #657580; overflow-wrap: anywhere; }
.job-content { min-width: 0; padding: 4px 0 20px; }
.requirement-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 18px; }
.requirement-grid > :last-child { grid-column: 1 / -1; }
.actions { justify-content: flex-start; margin: 16px 0; }
.task-section { margin-top: 28px; padding-top: 28px; border-top: 1px solid #dde4e7; }
.task-progress { background: #f5f8f9; padding: 8px 16px 16px; border-radius: 4px; }
.task-progress :deep(.el-timeline) { padding: 16px 0 0 8px; }
.reply-button { margin-top: 12px; }
.report-heading { margin: 12px 0 24px; }
.report-heading .el-select { flex: 1; min-width: 200px; max-width: 600px; }
.candidate-section { padding: 28px 0; border-bottom: 1px solid #dde4e7; }
.mobile-assessments { display: none; }
blockquote { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; color: #52626b; font-size: 13px; }
ol { list-style: decimal; padding-left: 24px; line-height: 1.8; }
.correction-form { margin-top: 24px; }
pre { white-space: pre-wrap; overflow-wrap: anywhere; font: inherit; }
@media (max-width: 720px) {
	.desktop-assessments { display: none; }
	.mobile-assessments { display: block; }
	.mobile-assessment { padding: 0 0 18px; border-bottom: 1px solid #dde4e7; overflow-wrap: anywhere; }
	.agent-workspace { padding: 0 16px; margin-top: 20px; }
	.workspace-grid { grid-template-columns: minmax(0, 1fr); gap: 20px; }
	.job-sidebar { border-right: 0; border-bottom: 1px solid #dde4e7; padding: 0 0 16px; }
	.job-list { max-height: 180px; overflow: auto; }
	.requirement-grid { grid-template-columns: minmax(0, 1fr); }
	.workspace-heading { align-items: flex-start; }
	h1 { font-size: 24px; }
}
</style>