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
    }
    private CharacterData() {}
}
