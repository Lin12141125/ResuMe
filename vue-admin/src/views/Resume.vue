<template>
    <div class="www">
        <el-button @click="goBack()">返回</el-button>
        <el-descriptions title="基本信息" direction="vertical" :column="6" size="default" border>
            <el-descriptions-item label="姓名" :span="1" label-align="center" align="center">{{ name }}</el-descriptions-item>
            <el-descriptions-item label="年龄" :span="1" label-align="center" align="center">{{ age }}</el-descriptions-item>
            <el-descriptions-item label="出生日期" :span="1" label-align="center" align="center">{{ birthday
            }}</el-descriptions-item>
            <el-descriptions-item label="籍贯" label-align="center" align="center">{{ jiguan }}</el-descriptions-item>
            <el-descriptions-item label="政治面貌" label-align="center" align="center">{{ zhengzhi }}</el-descriptions-item>
            <el-descriptions-item label="学历" label-align="center" align="center">
                <el-tag>{{ xueli }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="毕业院校" :span="2" label-align="center" align="center"><el-tag>{{ school
            }}</el-tag></el-descriptions-item>
            <el-descriptions-item label="邮箱" :span="2" label-align="center" align="center">{{ mail }}</el-descriptions-item>
            <el-descriptions-item label="电话" :span="2" label-align="center" align="center">{{ phone
            }}</el-descriptions-item>
            <el-descriptions-item label="技能专长" :span="8" label-align="center" align="center">
                <p v-for="item in skill" >{{item}}</p>
            </el-descriptions-item>
            <el-descriptions-item label="荣誉奖项" :span="8" label-align="center" align="center">
                <p v-for="item in honor" >{{item}}</p>
            </el-descriptions-item>
        </el-descriptions>
        <el-divider></el-divider>

        <el-descriptions v-for="item in school_exp" title="教育背景" :column="3" border direction="vertical">
            <el-descriptions-item label="时间" :span="1" label-align="center" align="center">{{ item.时间
            }}</el-descriptions-item>
            <el-descriptions-item label="就读学校" :span="1" label-align="center" align="center"><el-tag>{{ item.就读学校
            }}</el-tag></el-descriptions-item>
            <el-descriptions-item label="就读专业" :span="1" label-align="center" align="center"><el-tag>{{ item.就读专业
            }}</el-tag></el-descriptions-item>
        </el-descriptions>

        <el-divider></el-divider>

        <el-descriptions v-for="item in work_exp" title="工作经历" :column="3" border direction="vertical">
            <el-descriptions-item label="时间" :span="1" label-align="center" align="center">{{ item.时间
            }}</el-descriptions-item>
            <el-descriptions-item label="公司" :span="1" label-align="center" align="center"><el-tag>{{ item.公司
            }}</el-tag></el-descriptions-item>
            <el-descriptions-item label="职位" :span="1" label-align="center" align="center"><el-tag>{{ item.职位
            }}</el-tag></el-descriptions-item>
            <el-descriptions-item label="工作内容" :span="8" label-align="center" align="center">{{ item.工作内容
            }}</el-descriptions-item>
        </el-descriptions>

    </div>
</template>
  
<script>
import { computed, ref } from 'vue'
import axios from 'axios';
export default {
    data() {
        return {
            name: "无",
            age: "无",
            phone: "无",
            birthday: "无",
            mail: "无",
            // 籍贯
            jiguan: "无",
            // 政治面貌
            zhengzhi: "无",
            //学历
            xueli: "无",
            //毕业院校+就读学校
            school: "无",
            //技能专长
            skill: "无",
            //荣誉奖项
            honor: "无",
            school_exp: [],
            work_exp: [],
        }
    },
    mounted() {
        axios.get('/IRAS/SingleResumePresent/', {
            params: {
                "resumecode": this.$route.query.resumecode,
            },
        }).then(res => {
            if (res.data.msg == "success") {
                var receiveData = res.data.data
                this.dataProcess(receiveData)
            }
            else {
            }
        }).catch(error => {
        });
    },
    methods: {
        goBack() {
            this.$router.go(-1)
        },
        dataProcess(data) {
            this.name = data.姓名
            this.age = data.年龄
            this.birthday = data.出生日期
            this.jiguan = data.籍贯
            this.zhengzhi = data.政治面貌
            this.xueli = data.最高学历
            this.school = data.学校
            this.mail = data.邮箱
            this.phone = data.电话
            this.skill = data.技能专长
            this.honor = data.荣誉奖项
            for (var i = 0; i < data.教育背景.length; i++) {
                this.school_exp.push(data.教育背景[i])
            }
            for (var i = 0; i < data.工作经历.length; i++) {
                this.work_exp.push(data.工作经历[i])
            }
        }
    },

}


const size = ref('')
const blockMargin = computed(() => {
    const marginMap = {
        large: '32px',
        default: '28px',
        small: '24px',
    }
    return {
        marginTop: marginMap[size.value] || marginMap.default,
    }
})
</script>
  
<style scoped>
.el-descriptions {
    margin-top: 20px;
}
</style>
  