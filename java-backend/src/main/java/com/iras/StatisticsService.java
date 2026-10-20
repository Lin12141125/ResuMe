package com.iras;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class StatisticsService {
    private static final Set<String> SCHOOLS_985 = Set.of(("北京大学 清华大学 上海交通大学 浙江大学 武汉大学 南京大学 复旦大学 中国科学技术大学 华中科技大学 中国人民大学 天津大学 北京师范大学 西安交通大学 吉林大学 山东大学 国防科技大学 中山大学 南开大学 哈尔滨工业大学 北京航空航天大学 四川大学 同济大学 厦门大学 北京理工大学 东南大学 华南理工大学 大连理工大学 华东师范大学 中南大学 西北工业大学 中国农业大学 东北大学 湖南大学 电子科技大学 兰州大学 重庆大学 中国海洋大学 西北农林科技大学 中央民族大学").split(" "));
    private static final Set<String> SCHOOLS_211 = Set.of(("北京科技大学 武汉理工大学 南京理工大学 北京协和医学院 西南大学 华中师范大学 暨南大学 河海大学 南京师范大学 北京交通大学 南京农业大学 华东理工大学 苏州大学 华中农业大学 中南财经政法大学 南京航空航天大学 西安电子科技大学 合肥工业大学 西南交通大学 西北大学 湖南师范大学 郑州大学 东北师范大学 北京邮电大学 华南师范大学 哈尔滨工程大学 上海财经大学 云南大学 上海大学 西南财经大学 东华大学 中国政法大学 海军军医大学 北京化工大学 中国石油大学（北京） 中国地质大学（武汉） 北京外国语大学 南昌大学 中国传媒大学 太原理工大学 北京工业大学 空军军医大学 江南大学 陕西师范大学 中国矿业大学 对外经济贸易大学 福州大学 北京林业大学 中国石油大学（华东） 安徽大学 长安大学 辽宁大学 贵州大学 中央财经大学 北京体育大学 河北工业大学 北京中医药大学 东北农业大学 东北林业大学 四川农业大学 中国药科大学 新疆大学 中国矿业大学（北京） 中国地质大学（北京） 天津医科大学 上海外国语大学 大连海事大学 广西大学 华北电力大学 内蒙古大学 海南大学 石河子大学 宁夏大学 延边大学 中央音乐学院 西藏大学 青海大学").split(" "));
    private final ResumeService resumes;
    public StatisticsService(ResumeService resumes) { this.resumes = resumes; }

    public Map<String, Object> calculate(AccountPrincipal user, Long owner, List<Long> ids) {
        var available = resumes.list(user, owner);
        var selected = new LinkedHashSet<Long>();
        if (ids == null) available.forEach(row -> selected.add(((Number) row.get("resumecode")).longValue()));
        else {
            if (ids.size() > 100) throw new ApiException(400, "Too many resume IDs");
            selected.addAll(ids);
        }
        var ages = counts("20_25", "25_30", "30+", "20以下", "未知");
        var degrees = counts("本科以下", "本科", "研究生", "博士", "未知");
        var schools = counts("985类", "211类", "其他类", "未知");
        var years = counts("0_3", "3_6", "6+", "未知");
        var cities = new TreeMap<String, Integer>();
        for (long id : selected) {
            JsonNode data = resumes.detail(id, user);
            int age = data.path("年龄").asInt(0);
            increment(ages, age <= 0 ? "未知" : age < 20 ? "20以下" : age <= 25 ? "20_25" : age <= 30 ? "25_30" : "30+");
            String degree = data.path("最高学历").asText();
            increment(degrees, unknown(degree) ? "未知" : degree.contains("博士") ? "博士" : degree.contains("硕士") || degree.contains("研究生") ? "研究生"
                    : degree.contains("本科") || degree.contains("学士") ? "本科" : "本科以下");
            String school = data.path("学校").asText();
            increment(schools, unknown(school) ? "未知" : SCHOOLS_985.contains(school) ? "985类" : SCHOOLS_211.contains(school) ? "211类" : "其他类");
            int experience = ResumeService.workYears(data);
            increment(years, data.path("工作经历").isEmpty() ? "未知" : experience <= 3 ? "0_3" : experience <= 6 ? "3_6" : "6+");
            String city = data.path("籍贯").asText();
            if (!unknown(city)) cities.merge(city, 1, Integer::sum);
        }
        return Map.of("age_part", ages, "degree_part", degrees, "school", schools, "years", years,
                "cities", cities.entrySet().stream().map(entry -> Map.of("name", entry.getKey(), "value", entry.getValue())).toList());
    }
    private boolean unknown(String value) { return value.isBlank() || Set.of("无", "未知", "未识别").contains(value); }
    private LinkedHashMap<String, Integer> counts(String... keys) {
        var values = new LinkedHashMap<String, Integer>();
        for (String key : keys) values.put(key, 0);
        return values;
    }
    private void increment(Map<String, Integer> counts, String key) { counts.merge(key, 1, Integer::sum); }
}