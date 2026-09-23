package com.ppsr.control;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

final class CharacterData {
    static final String[] PATH_LABELS = {"毁灭", "巡猎", "智识", "同谐", "虚无", "存护", "丰饶", "记忆", "欢愉"};
    static final String[] PATH_VALUES = {"Destruction", "Hunt", "Erudition", "Harmony", "Nihility", "Preservation", "Abundance", "Remembrance", "Elation"};
    static final Map<String, List<Integer>> PATHS;
    static final Map<Integer, String> NAMES;
    static {
        Map<String, List<Integer>> paths = new LinkedHashMap<>();
        paths.put("Destruction", Arrays.asList(1008,1014,1107,1109,1205,1212,1213,1214,1221,1310,1312,1404,1408,1509,8001,8002));
        paths.put("Hunt", Arrays.asList(1002,1015,1102,1112,1206,1209,1220,1223,1224,1305,1315,1504));
        paths.put("Erudition", Arrays.asList(1003,1013,1103,1201,1204,1302,1314,1317,1401,1405,1508,1510));
        paths.put("Harmony", Arrays.asList(1009,1101,1202,1207,1215,1303,1306,1309,1313,1403,1412,8005,8006));
        paths.put("Nihility", Arrays.asList(1004,1005,1006,1106,1108,1111,1210,1218,1225,1307,1308,1321,1406,1410,1507));
        paths.put("Preservation", Arrays.asList(1001,1104,1208,1304,1414,8003,8004));
        paths.put("Abundance", Arrays.asList(1105,1110,1203,1211,1217,1222,1301));
        paths.put("Remembrance", Arrays.asList(1402,1407,1409,1413,1415,1512,8007,8008));
        paths.put("Elation", Arrays.asList(1501,1502,1503,1505,1506,1513,8009,8010));
        PATHS = Collections.unmodifiableMap(paths);
        Map<Integer, String> names = new HashMap<>();
        addNames(names, 1001, "三月七", 1002, "丹恒", 1003, "黑塔", 1004, "瓦尔特", 1005, "卡芙卡", 1006, "银狼", 1008, "刃", 1009, "布洛妮娅", 1013, "景元", 1014, "克拉拉", 1015, "彦卿", 1101, "希儿", 1102, "桑博", 1103, "杰帕德", 1104, "娜塔莎", 1105, "佩拉", 1106, "克拉拉", 1107, "希露瓦", 1108, "阿兰", 1109, "艾丝妲", 1110, "虎克", 1111, "玲可", 1112, "卢卡", 1201, "青雀", 1202, "停云", 1203, "罗刹", 1204, "景元", 1205, "刃", 1206, "素裳", 1207, "驭空", 1208, "符玄", 1209, "彦卿", 1210, "桂乃芬", 1211, "白露", 1212, "寒鸦", 1213, "镜流", 1214, "藿藿", 1215, "阮·梅", 1217, "玲可", 1218, "花火", 1220, "雪衣", 1221, "大黑塔", 1222, "米沙", 1223, "银枝", 1224, "飞霄", 1225, "黑天鹅", 1302, "姬子", 1305, "丹恒·饮月", 1312, "黄泉", 1314, "真理医生", 1315, "托帕与账账", 1317, "花火", 1321, "黑天鹅", 1403, "知更鸟", 1404, "云璃", 1405, "翡翠", 1406, "砂金", 1407, "流萤", 1408, "加拉赫", 1409, "星期日", 1410, "椒丘", 1412, "忘归人", 1413, "灵砂", 1414, "遐蝶", 1415, "阿格莱雅", 1501, "开拓者·欢愉", 1502, "缇宝", 1503, "万敌", 1504, "开拓者·巡猎", 1505, "开拓者·智识", 1506, "开拓者·虚无", 1507, "开拓者·存护", 1508, "开拓者·同谐", 1509, "开拓者·毁灭", 1510, "开拓者·记忆", 1512, "遐蝶", 1513, "昔涟", 8001, "开拓者·毁灭", 8002, "开拓者·毁灭", 8003, "开拓者·存护", 8004, "开拓者·存护", 8005, "开拓者·同谐", 8006, "开拓者·同谐", 8007, "开拓者·记忆", 8008, "开拓者·记忆", 8009, "开拓者·欢愉", 8010, "开拓者·欢愉");
        NAMES = Collections.unmodifiableMap(names);
    }
    private static void addNames(Map<Integer, String> names, Object... values) { for (int i = 0; i + 1 < values.length; i += 2) names.put((Integer) values[i], (String) values[i + 1]); }
    static String nameOf(int id) { return NAMES.get(id); }
    private CharacterData() {}
}
