package com.ppsr.control;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CharacterData {
    static final String[] PATH_LABELS = {"毁灭", "巡猎", "智识", "同谐", "虚无", "存护", "丰饶", "记忆", "欢愉"};
    static final String[] PATH_VALUES = {"Destruction", "Hunt", "Erudition", "Harmony", "Nihility", "Preservation", "Abundance", "Remembrance", "Elation"};
    static final Map<String, List<Integer>> PATHS;
    static final Map<Integer, String> NAMES;
    static {
        Map<String, List<Integer>> paths = new LinkedHashMap<>();
        // 毁灭
        paths.put("Destruction", Arrays.asList(1008,1014,1107,1109,1205,1212,1213,1214,1221,1310,1312,1404,1408,1509,8001,8002));
        // 巡猎
        paths.put("Hunt", Arrays.asList(1002,1015,1102,1112,1206,1209,1220,1223,1224,1305,1315,1504));
        // 智识
        paths.put("Erudition", Arrays.asList(1003,1013,1103,1201,1204,1302,1314,1317,1401,1405,1508,1510));
        // 同谐
        paths.put("Harmony", Arrays.asList(1009,1101,1202,1207,1215,1303,1306,1309,1313,1403,1412,8005,8006));
        // 虚无
        paths.put("Nihility", Arrays.asList(1004,1005,1006,1106,1108,1111,1210,1218,1225,1307,1308,1321,1406,1410,1507));
        // 存护
        paths.put("Preservation", Arrays.asList(1001,1104,1208,1304,1414,8003,8004));
        // 丰饶
        paths.put("Abundance", Arrays.asList(1105,1110,1203,1211,1217,1222,1301));
        // 记忆
        paths.put("Remembrance", Arrays.asList(1402,1407,1409,1413,1415,1512,8007,8008));
        // 欢愉
        paths.put("Elation", Arrays.asList(1501,1502,1503,1505,1506,1511,1513,8009,8010));
        PATHS = Collections.unmodifiableMap(paths);
        Map<Integer, String> names = new LinkedHashMap<>();
        // 毁灭
        names.put(1008, "阿兰");
        names.put(1014, "Saber");
        names.put(1107, "克拉拉");
        names.put(1109, "虎克");
        names.put(1205, "刃");
        names.put(1212, "镜流");
        names.put(1213, "丹恒•饮月");
        names.put(1214, "雪衣");
        names.put(1221, "云璃");
        names.put(1310, "流萤");
        names.put(1312, "米沙");
        names.put(1404, "万敌");
        names.put(1408, "白厄");
        names.put(1509, "吉尔伽美什");
        names.put(8001, "开拓者•毁灭•穹");
        names.put(8002, "开拓者•毁灭•星");
        // 巡猎
        names.put(1002, "丹恒");
        names.put(1015, "Archer");
        names.put(1102, "希儿");
        names.put(1112, "托帕&账账");
        names.put(1206, "素裳");
        names.put(1209, "彦卿");
        names.put(1220, "飞霄");
        names.put(1223, "貊泽");
        names.put(1224, "三月七");
        names.put(1305, "真理医生");
        names.put(1315, "波提欧");
        names.put(1504, "不死途");
        // 智识
        names.put(1003, "姬子");
        names.put(1013, "黑塔");
        names.put(1103, "希露瓦");
        names.put(1201, "青雀");
        names.put(1204, "景元");
        names.put(1302, "银枝");
        names.put(1314, "翡翠");
        names.put(1317, "乱破");
        names.put(1401, "大黑塔");
        names.put(1405, "那刻夏");
        names.put(1508, "远坂凛");
        names.put(1510, "姬子•启行");
        // 同谐
        names.put(1009, "艾丝妲");
        names.put(1101, "布洛妮娅");
        names.put(1202, "停云");
        names.put(1207, "驭空");
        names.put(1215, "寒鸦");
        names.put(1303, "阮•梅");
        names.put(1306, "花火");
        names.put(1309, "知更鸟");
        names.put(1313, "星期日");
        names.put(1403, "缇宝");
        names.put(1412, "刻律德菈");
        names.put(8005, "开拓者•同谐•穹");
        names.put(8006, "开拓者•同谐•星");
        // 虚无
        names.put(1004, "瓦尔特");
        names.put(1005, "卡芙卡");
        names.put(1006, "银狼");
        names.put(1106, "佩拉");
        names.put(1108, "桑博");
        names.put(1111, "卢卡");
        names.put(1210, "桂乃芬");
        names.put(1218, "椒丘");
        names.put(1225, "忘归人");
        names.put(1307, "黑天鹅");
        names.put(1308, "黄泉");
        names.put(1321, "大丽花");
        names.put(1406, "赛飞儿");
        names.put(1410, "海瑟音");
        names.put(1507, "千冶•刃");
        // 存护
        names.put(1001, "三月七");
        names.put(1104, "杰帕德");
        names.put(1208, "符玄");
        names.put(1304, "砂金");
        names.put(1414, "丹恒•腾荒");
        names.put(8003, "开拓者•存护•穹");
        names.put(8004, "开拓者•存护•星");
        // 丰饶
        names.put(1105, "娜塔莎");
        names.put(1110, "玲可");
        names.put(1203, "罗刹");
        names.put(1211, "白露");
        names.put(1217, "藿藿");
        names.put(1222, "灵砂");
        names.put(1301, "加拉赫");
        // 记忆
        names.put(1402, "阿格莱雅");
        names.put(1407, "遐蝶");
        names.put(1409, "风堇");
        names.put(1413, "长夜月");
        names.put(1415, "昔涟");
        names.put(1512, "知更鸟•晴歌");
        names.put(8007, "开拓者•记忆•穹");
        names.put(8008, "开拓者•记忆•星");
        // 欢愉
        names.put(1501, "火花");
        names.put(1502, "爻光");
        names.put(1503, "真珠");
        names.put(1505, "绯英");
        names.put(1506, "银狼LV.<unbreak>999</unbreak>");
        names.put(1511, "星神★阿哈");
        names.put(1513, "砂金•戏浪");
        names.put(8009, "开拓者•欢愉•穹");
        names.put(8010, "开拓者•欢愉•星");
        NAMES = Collections.unmodifiableMap(names);
    }
    static String nameOf(int id) { return NAMES.get(id); }
    static String displayNameOf(int id) {
        String name = nameOf(id);
        return name == null ? "未知角色(" + id + ")" : name;
    }

    static String pathOf(int id) {
        for (Map.Entry<String, List<Integer>> entry : PATHS.entrySet()) {
            if (entry.getValue().contains(id)) return entry.getKey();
        }
        return null;
    }

    static String pathLabelOf(String path) {
        for (int i = 0; i < PATH_VALUES.length; i++) {
            if (PATH_VALUES[i].equals(path)) return PATH_LABELS[i];
        }
        return null;
    }

    private CharacterData() {}
}
