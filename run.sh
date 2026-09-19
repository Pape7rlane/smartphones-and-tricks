#!/data/data/com.termux/files/usr/bin/bash

# ============================================================
#                 ppSR Control Center v4.5.54
# Android / Termux
# 白色透明框 · 无 Emoji 版
# ============================================================

BASE="$(cd "$(dirname "$0")" && pwd)"
BUILD="$BASE/build"

MISC1="$BASE/storage/shared/ppSR/misc"
MISC2="$BUILD/misc"

IP="127.0.0.1"
PORT="21000"
VERSION="4.5.54"

WHITE="\033[1;37m"
RESET="\033[0m"

# ============================================================
# 角色名称
# ============================================================

declare -A CHAR_NAME

# ----------------------------
# 毁灭
# ----------------------------

CHAR_NAME[1008]="阿兰"
CHAR_NAME[1014]="Saber"
CHAR_NAME[1107]="克拉拉"
CHAR_NAME[1109]="虎克"
CHAR_NAME[1205]="刃"
CHAR_NAME[1212]="镜流"
CHAR_NAME[1213]="丹恒•饮月"
CHAR_NAME[1214]="雪衣"
CHAR_NAME[1221]="云璃"
CHAR_NAME[1310]="流萤"
CHAR_NAME[1312]="米沙"
CHAR_NAME[1404]="万敌"
CHAR_NAME[1408]="白厄"
CHAR_NAME[1509]="吉尔伽美什"
CHAR_NAME[8001]="穹"
CHAR_NAME[8002]="星"

# ----------------------------
# 巡猎
# ----------------------------

CHAR_NAME[1002]="丹恒"
CHAR_NAME[1015]="Archer"
CHAR_NAME[1102]="希儿"
CHAR_NAME[1112]="托帕&账账"
CHAR_NAME[1206]="素裳"
CHAR_NAME[1209]="彦卿"
CHAR_NAME[1220]="飞霄"
CHAR_NAME[1223]="貊泽"
CHAR_NAME[1224]="三月七"
CHAR_NAME[1305]="真理医生"
CHAR_NAME[1315]="波提欧"
CHAR_NAME[1505]="不死途"

# ----------------------------
# 智识
# ----------------------------

CHAR_NAME[1003]="姬子"
CHAR_NAME[1013]="黑塔"
CHAR_NAME[1103]="希露瓦"
CHAR_NAME[1201]="青雀"
CHAR_NAME[1204]="景元"
CHAR_NAME[1302]="银枝"
CHAR_NAME[1314]="翡翠"
CHAR_NAME[1317]="乱破"
CHAR_NAME[1401]="大黑塔"
CHAR_NAME[1405]="那刻夏"
CHAR_NAME[1508]="远坂凛"
CHAR_NAME[1510]="姬子•启行"

# ----------------------------
# 同谐
# ----------------------------

CHAR_NAME[1009]="艾丝妲"
CHAR_NAME[1101]="布洛妮娅"
CHAR_NAME[1202]="停云"
CHAR_NAME[1207]="驭空"
CHAR_NAME[1215]="寒鸦"
CHAR_NAME[1303]="阮•梅"
CHAR_NAME[1306]="花火"
CHAR_NAME[1309]="知更鸟"
CHAR_NAME[1313]="星期日"
CHAR_NAME[1403]="缇宝"
CHAR_NAME[1412]="刻律德菈"
CHAR_NAME[8005]="穹"
CHAR_NAME[8006]="星"

# ----------------------------
# 虚无
# ----------------------------

CHAR_NAME[1004]="瓦尔特"
CHAR_NAME[1005]="卡芙卡"
CHAR_NAME[1006]="银狼"
CHAR_NAME[1106]="佩拉"
CHAR_NAME[1108]="桑博"
CHAR_NAME[1111]="卢卡"
CHAR_NAME[1210]="桂乃芬"
CHAR_NAME[1218]="椒丘"
CHAR_NAME[1225]="忘归人"
CHAR_NAME[1307]="黑天鹅"
CHAR_NAME[1308]="黄泉"
CHAR_NAME[1321]="大丽花"
CHAR_NAME[1406]="赛飞儿"
CHAR_NAME[1410]="海瑟音"
CHAR_NAME[1507]="千冶•刃"

# ----------------------------
# 存护
# ----------------------------

CHAR_NAME[1001]="三月七"
CHAR_NAME[1104]="杰帕德"
CHAR_NAME[1208]="符玄"
CHAR_NAME[1304]="砂金"
CHAR_NAME[1414]="丹恒•腾荒"
CHAR_NAME[8003]="穹"
CHAR_NAME[8004]="星"

# ----------------------------
# 丰饶
# ----------------------------

CHAR_NAME[1105]="娜塔莎"
CHAR_NAME[1110]="玲可"
CHAR_NAME[1203]="罗刹"
CHAR_NAME[1211]="白露"
CHAR_NAME[1217]="藿藿"
CHAR_NAME[1222]="灵砂"
CHAR_NAME[1301]="加拉赫"

# ----------------------------
# 记忆
# ----------------------------

CHAR_NAME[1402]="阿格莱雅"
CHAR_NAME[1407]="遐蝶"
CHAR_NAME[1409]="风堇"
CHAR_NAME[1413]="长夜月"
CHAR_NAME[1415]="昔涟"
CHAR_NAME[1512]="知更鸟•晴歌"
CHAR_NAME[8007]="穹"
CHAR_NAME[8008]="星"

# ----------------------------
# 欢愉
# ----------------------------

CHAR_NAME[1501]="火花"
CHAR_NAME[1502]="爻光"
CHAR_NAME[1503]="真珠"
CHAR_NAME[1504]="绯英"
CHAR_NAME[1506]="银狼LV.<unbreak>999</unbreak>"
CHAR_NAME[1513]="砂金•戏浪"
CHAR_NAME[8009]="穹"
CHAR_NAME[8010]="星"

# ============================================================
# 命途 ID
# ============================================================

DESTRUCTION=(
    1008 1014 1107 1109
    1205 1212 1213 1214
    1221 1310 1312 1404
    1408 1509 8001 8002
)

HUNT=(
    1002 1015 1102 1112
    1206 1209 1220 1223
    1224 1305 1315 1505
)

ERUDITION=(
    1003 1013 1103 1201
    1204 1302 1314 1317
    1401 1405 1508 1510
)

HARMONY=(
    1009 1101 1202 1207
    1215 1303 1306 1309
    1313 1403 1412 8005
    8006
)

NIHILITY=(
    1004 1005 1006 1106
    1108 1111 1210 1218
    1225 1307 1308 1321
    1406 1410 1507
)

PRESERVATION=(
    1001 1104 1208 1304
    1414 8003 8004
)

ABUNDANCE=(
    1105 1110 1203 1211
    1217 1222 1301
)

REMEMBRANCE=(
    1402 1407 1409 1413
    1415 1512 8007 8008
)

ELATION=(
    1501 1502 1503 1504
    1506 1513 8009 8010
)

# ============================================================
# 同步 misc
# ============================================================

sync_misc() {

    mkdir -p "$MISC1"
    mkdir -p "$MISC2"

    cp -rf "$MISC1"/. "$MISC2"/ 2>/dev/null
}

# ============================================================
# 保存 persistent.json
# ============================================================

save_persistent() {

    mkdir -p "$MISC1"
    mkdir -p "$MISC2"

    if [ -f "$MISC2/persistent.json" ]; then

        cp \
        "$MISC2/persistent.json" \
        "$MISC1/persistent.json" \
        2>/dev/null

    fi
}

# ============================================================
# 白色边框
# ============================================================

box_top() {

    echo -e "${WHITE}╭────────────────────────────────╮${RESET}"
}

box_bottom() {

    echo -e "${WHITE}╰────────────────────────────────╯${RESET}"
}

box_line() {

    printf "${WHITE}│${RESET} %-30s ${WHITE}│${RESET}\n" "$1"
}

# ============================================================
# 主界面
# ============================================================

status() {

    clear

    box_top

    box_line "ppSR 游戏服务器 v${VERSION}"

    box_line ""

    box_line "地址 : $IP:$PORT"
    box_line "版本 : ppSR v${VERSION}"

    box_line ""

    box_line "1. 启动服务端"
    box_line "2. 切换命途"
    box_line "3. 自定义配队"
    box_line "4. 退出"

    box_bottom

    echo
}

# ============================================================
# 启动服务器
# ============================================================

start() {

    clear

    box_top
    box_line "启动 ppSR v${VERSION}"
    box_line ""
    box_line "地址 : ${IP}:${PORT}"
    box_bottom

    echo

    sync_misc

    if [ ! -f "$BUILD/ppsr.pyc" ]; then

        echo "错误：找不到："
        echo "$BUILD/ppsr.pyc"
        echo

        read -p "按回车返回..."
        return
    fi

    cd "$BUILD" || return

    echo "服务器启动中..."
    echo

    python ppsr.pyc
}

# ============================================================
# 命途中文名称
# ============================================================

path_name() {

    case "$1" in

        Destruction)
            echo "毁灭"
            ;;

        Hunt)
            echo "巡猎"
            ;;

        Erudition)
            echo "智识"
            ;;

        Harmony)
            echo "同谐"
            ;;

        Nihility)
            echo "虚无"
            ;;

        Preservation)
            echo "存护"
            ;;

        Abundance)
            echo "丰饶"
            ;;

        Remembrance)
            echo "记忆"
            ;;

        Elation)
            echo "欢愉"
            ;;

        *)
            echo "$1"
            ;;

    esac
}

# ============================================================
# 切换命途
# ============================================================

switch_path() {

    while true; do

        clear

        box_top
        box_line "切换命途"
        box_bottom

        echo

        echo "1. 开拓者"
        echo "2. 三月七"
        echo "0. 返回"

        echo

        read -p "请选择: " TYPE

        case "$TYPE" in

            1)

                clear

                box_top
                box_line "开拓者命途"
                box_bottom

                echo

                echo "1. 毁灭"
                echo "2. 存护"
                echo "3. 同谐"
                echo "4. 记忆"
                echo "5. 欢愉"

                echo "0. 返回"

                echo

                read -p "请选择命途: " P

                case "$P" in

                    1) VALUE="Destruction" ;;
                    2) VALUE="Preservation" ;;
                    3) VALUE="Harmony" ;;
                    4) VALUE="Remembrance" ;;
                    5) VALUE="Elation" ;;
                    0) continue ;;
                    *)
                        echo "无效选择"
                        sleep 1
                        continue
                        ;;
                esac

                python - "$MISC2/persistent.json" "$VALUE" <<'PY'
import sys
import json
import os

path = sys.argv[1]
value = sys.argv[2]

data = {}

if os.path.exists(path):
    try:
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
    except:
        data = {}

data.setdefault("avatar", {})
data["avatar"]["tb_path"] = value

with open(path, "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, indent=4)
PY

                save_persistent

                echo
                echo "已切换到：$(path_name "$VALUE")"
                sleep 1
                ;;

            2)

                clear

                box_top
                box_line "三月七命途"
                box_bottom

                echo

                echo "1. 存护"
                echo "2. 巡猎"

                echo "0. 返回"

                echo

                read -p "请选择命途: " P

                case "$P" in

                    1) VALUE="Preservation" ;;
                    2) VALUE="Hunt" ;;
                    0) continue ;;
                    *)
                        echo "无效选择"
                        sleep 1
                        continue
                        ;;
                esac

                python - "$MISC2/persistent.json" "$VALUE" <<'PY'
import sys
import json
import os

path = sys.argv[1]
value = sys.argv[2]

data = {}

if os.path.exists(path):
    try:
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
    except:
        data = {}

data.setdefault("avatar", {})
data["avatar"]["march_path"] = value

with open(path, "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, indent=4)
PY

                save_persistent

                echo
                echo "已切换到：$(path_name "$VALUE")"
                sleep 1
                ;;

            0)
                return
                ;;

            *)
                echo "无效选择"
                sleep 1
                ;;
        esac

    done
}

# ============================================================
# 获取当前配队
# ============================================================

get_lineup() {

    python - "$MISC2/persistent.json" <<'PY'
import sys
import json
import os

path = sys.argv[1]

if not os.path.exists(path):
    print("")
    raise SystemExit

try:

    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)

    lineup = data.get("avatar", {}).get("lineup", [])

    for x in lineup:
        print(x)

except:

    print("")
PY
}

# ============================================================
# 显示当前配队
# ============================================================

show_lineup() {

    box_top
    box_line "当前配队"
    box_bottom

    echo

    local INDEX=1

    while read -r ID; do

        [ -z "$ID" ] && continue

        NAME="${CHAR_NAME[$ID]}"

        if [ -z "$NAME" ]; then
            NAME="未知角色"
        fi

        echo "$INDEX. $ID - $NAME"

        INDEX=$((INDEX+1))

    done < <(get_lineup)

    if [ "$INDEX" -eq 1 ]; then
        echo "暂无角色"
    fi

    echo
}

# ============================================================
# 判断角色是否已经存在
# ============================================================

lineup_has_id() {

    local TARGET="$1"

    while read -r ID; do

        if [ "$ID" = "$TARGET" ]; then
            echo "yes"
            return
        fi

    done < <(get_lineup)

    echo "no"
}

# ============================================================
# 添加角色
# ============================================================

add_character() {

    local ID="$1"

    python - "$MISC2/persistent.json" "$ID" <<'PY'
import sys
import json
import os

path = sys.argv[1]
new_id = int(sys.argv[2])

data = {}

if os.path.exists(path):

    try:
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
    except:
        data = {}

data.setdefault("avatar", {})

lineup = data["avatar"].get("lineup", [])

if new_id not in lineup:
    lineup.append(new_id)

data["avatar"]["lineup"] = lineup

with open(path, "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, indent=4)
PY
}

# ============================================================
# 根据命途获取 ID
# ============================================================

choose_path() {

    case "$1" in

        1)
            IDS=("${DESTRUCTION[@]}")
            TITLE="毁灭"
            ;;

        2)
            IDS=("${HUNT[@]}")
            TITLE="巡猎"
            ;;

        3)
            IDS=("${ERUDITION[@]}")
            TITLE="智识"
            ;;

        4)
            IDS=("${HARMONY[@]}")
            TITLE="同谐"
            ;;

        5)
            IDS=("${NIHILITY[@]}")
            TITLE="虚无"
            ;;

        6)
            IDS=("${PRESERVATION[@]}")
            TITLE="存护"
            ;;

        7)
            IDS=("${ABUNDANCE[@]}")
            TITLE="丰饶"
            ;;

        8)
            IDS=("${REMEMBRANCE[@]}")
            TITLE="记忆"
            ;;

        9)
            IDS=("${ELATION[@]}")
            TITLE="欢愉"
            ;;

        *)
            return 1
            ;;
    esac

    return 0
}

# ============================================================
# 按命途选择角色
# ============================================================

select_character_from_path() {

    while true; do

        clear

        box_top
        box_line "按命途选择角色"
        box_bottom

        echo

        echo "1. 毁灭"
        echo "2. 巡猎"
        echo "3. 智识"
        echo "4. 同谐"
        echo "5. 虚无"
        echo "6. 存护"
        echo "7. 丰饶"
        echo "8. 记忆"
        echo "9. 欢愉"
        echo "0. 返回"

        echo

        read -p "请选择命途: " P

        [ "$P" = "0" ] && return

        choose_path "$P" || {

            echo "无效选择"
            sleep 1
            continue
        }

        while true; do

            clear

            box_top
            box_line "$TITLE"
            box_bottom

            echo

            local INDEX=1

            for ID in "${IDS[@]}"; do

                NAME="${CHAR_NAME[$ID]}"

                if [ -z "$NAME" ]; then
                    NAME="未知角色"
                fi

                echo "$INDEX. $ID - $NAME"

                INDEX=$((INDEX+1))
            done

            echo
            echo "0. 返回"
            echo

            read -p "请选择角色: " C

            [ "$C" = "0" ] && break

            if ! [[ "$C" =~ ^[0-9]+$ ]]; then

                echo "无效选择"
                sleep 1
                continue
            fi

            if [ "$C" -lt 1 ] || [ "$C" -gt "${#IDS[@]}" ]; then

                echo "无效选择"
                sleep 1
                continue
            fi

            ID="${IDS[$((C-1))]}"
            NAME="${CHAR_NAME[$ID]}"

            if [ "$(lineup_has_id "$ID")" = "yes" ]; then

                echo
                echo "角色已经在当前配队中"
                sleep 1
                continue
            fi

            add_character "$ID"
            save_persistent

            echo
            echo "已添加：$ID - $NAME"

            sleep 1
        done

    done
}

# ============================================================
# 整个命途加入
# ============================================================

add_entire_path() {

    while true; do

        clear

        box_top
        box_line "整个命途加入"
        box_bottom

        echo

        echo "1. 毁灭"
        echo "2. 巡猎"
        echo "3. 智识"
        echo "4. 同谐"
        echo "5. 虚无"
        echo "6. 存护"
        echo "7. 丰饶"
        echo "8. 记忆"
        echo "9. 欢愉"
        echo "0. 返回"

        echo

        read -p "请选择命途: " P

        [ "$P" = "0" ] && return

        choose_path "$P" || {

            echo "无效选择"
            sleep 1
            continue
        }

        echo
        echo "命途 : $TITLE"
        echo "角色数量 : ${#IDS[@]}"
        echo

        read -p "确定加入整个 $TITLE 命途吗？输入 YES 确认: " CONFIRM

        if [ "$CONFIRM" != "YES" ]; then

            echo "已取消"
            sleep 1
            continue
        fi

        ADDED=0
        SKIPPED=0

        for ID in "${IDS[@]}"; do

            if [ "$(lineup_has_id "$ID")" = "yes" ]; then

                SKIPPED=$((SKIPPED+1))

            else

                add_character "$ID"

                echo "已添加：$ID - ${CHAR_NAME[$ID]}"

                ADDED=$((ADDED+1))
            fi

        done

        save_persistent

        echo
        echo "命途 : $TITLE"
        echo "新增 : $ADDED"
        echo "已存在 : $SKIPPED"

        echo

        read -p "按回车继续..."

    done
}

# ============================================================
# 添加角色菜单
# ============================================================

select_from_path() {

    while true; do

        clear

        box_top
        box_line "添加角色"
        box_bottom

        echo

        echo "1. 按命途选择角色"
        echo "2. 整个命途加入"
        echo "0. 返回"

        echo

        read -p "请选择: " CHOICE

        case "$CHOICE" in

            1)
                select_character_from_path
                ;;

            2)
                add_entire_path
                ;;

            0)
                return
                ;;

            *)
                echo "无效选择"
                sleep 1
                ;;
        esac

    done
}

# ============================================================
# 手动输入角色 ID
# ============================================================

manual_add() {

    clear

    box_top
    box_line "手动输入角色 ID"
    box_bottom

    echo
    echo "可以一次输入多个 ID"
    echo "例如：1501 1502 1503"
    echo

    read -p "请输入 ID: " INPUT

    [ -z "$INPUT" ] && return

    ADDED=0
    SKIPPED=0
    INVALID=0

    for ID in $INPUT; do

        if ! [[ "$ID" =~ ^[0-9]+$ ]]; then

            echo "无效 ID：$ID"
            INVALID=$((INVALID+1))
            continue
        fi

        if [ "$(lineup_has_id "$ID")" = "yes" ]; then

            echo "已存在：$ID"
            SKIPPED=$((SKIPPED+1))
            continue
        fi

        add_character "$ID"

        NAME="${CHAR_NAME[$ID]}"

        if [ -z "$NAME" ]; then
            NAME="未知角色"
        fi

        echo "已添加：$ID - $NAME"

        ADDED=$((ADDED+1))
    done

    save_persistent

    echo
    echo "新增：$ADDED"
    echo "已存在：$SKIPPED"
    echo "无效：$INVALID"

    echo

    read -p "按回车继续..."
}

# ============================================================
# 删除角色
# ============================================================

remove_character() {

    clear

    show_lineup

    read -p "请输入要删除的序号: " INDEX

    if ! [[ "$INDEX" =~ ^[0-9]+$ ]]; then

        echo "无效序号"
        sleep 1
        return
    fi

    python - "$MISC2/persistent.json" "$INDEX" <<'PY'
import sys
import json
import os

path = sys.argv[1]
index = int(sys.argv[2]) - 1

if not os.path.exists(path):
    print("没有配队")
    raise SystemExit

try:

    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)

except:

    print("读取失败")
    raise SystemExit

lineup = data.get("avatar", {}).get("lineup", [])

if index < 0 or index >= len(lineup):

    print("无效序号")
    raise SystemExit

removed = lineup.pop(index)

data.setdefault("avatar", {})
data["avatar"]["lineup"] = lineup

with open(path, "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, indent=4)

print(f"已删除：{removed}")
PY

    save_persistent

    echo

    read -p "按回车继续..."
}

# ============================================================
# 清空配队
# ============================================================

clear_lineup() {

    echo

    read -p "确定清空整个配队吗？输入 YES 确认: " CONFIRM

    if [ "$CONFIRM" != "YES" ]; then

        echo "已取消"
        sleep 1
        return
    fi

    python - "$MISC2/persistent.json" <<'PY'
import sys
import json
import os

path = sys.argv[1]

data = {}

if os.path.exists(path):

    try:
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
    except:
        data = {}

data.setdefault("avatar", {})
data["avatar"]["lineup"] = []

with open(path, "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, indent=4)
PY

    save_persistent

    echo
    echo "配队已清空"

    sleep 1
}

# ============================================================
# 自定义配队
# ============================================================

custom_lineup() {

    while true; do

        clear

        show_lineup

        echo "1. 按命途添加"
        echo "2. 手动输入 ID"
        echo "3. 删除角色"
        echo "4. 清空配队"
        echo "5. 保存并返回"
        echo "0. 返回"

        echo

        read -p "请选择: " CHOICE

        case "$CHOICE" in

            1)
                select_from_path
                ;;

            2)
                manual_add
                ;;

            3)
                remove_character
                ;;

            4)
                clear_lineup
                ;;

            5)
                save_persistent

                echo "配队已保存"

                sleep 1

                return
                ;;

            0)
                return
                ;;

            *)
                echo "无效选择"
                sleep 1
                ;;
        esac

    done
}

# ============================================================
# 主循环
# ============================================================

while true; do

    status

    read -p "请选择: " MENU

    case "$MENU" in

        1)
            start
            ;;

        2)
            switch_path
            ;;

        3)
            custom_lineup
            ;;

        4)

            clear

            box_top
            box_line "已退出 ppSR Control Center"
            box_bottom

            exit 0
            ;;

        *)
            echo "无效选择，请重新输入"
            sleep 1
            ;;
    esac

done