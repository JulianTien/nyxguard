#!/bin/bash
# 模拟步行 GPS 轨迹
# 用法: ./mock_walk.sh [起点纬度] [起点经度] [终点纬度] [终点经度] [步数]
# 示例: ./mock_walk.sh 39.908722 116.397499 39.915 116.404 30
#       (从天安门往东北方向步行)

ADB="$HOME/Library/Android/sdk/platform-tools/adb"
DEVICE="-s 127.0.0.1:5555"

START_LAT=${1:-39.908722}
START_LNG=${2:-116.397499}
END_LAT=${3:-39.915}
END_LNG=${4:-116.404}
STEPS=${5:-30}
INTERVAL=10  # 每10秒一个点，与定位频率一致

echo "模拟步行路线:"
echo "  起点: ($START_LAT, $START_LNG)"
echo "  终点: ($END_LAT, $END_LNG)"
echo "  步数: $STEPS, 间隔: ${INTERVAL}s"
echo ""

for i in $(seq 0 $STEPS); do
    # 线性插值计算当前位置
    PROGRESS=$(echo "scale=6; $i / $STEPS" | bc)
    LAT=$(echo "scale=6; $START_LAT + ($END_LAT - $START_LAT) * $PROGRESS" | bc)
    LNG=$(echo "scale=6; $START_LNG + ($END_LNG - $START_LNG) * $PROGRESS" | bc)

    echo "[$i/$STEPS] 发送位置: ($LAT, $LNG)"
    $ADB $DEVICE emu geo fix $LNG $LAT 2>/dev/null || \
    $ADB $DEVICE shell "am broadcast -a android.intent.action.MOCK_LOCATION --ef latitude $LAT --ef longitude $LNG" 2>/dev/null

    if [ $i -lt $STEPS ]; then
        sleep $INTERVAL
    fi
done

echo ""
echo "模拟步行完成！"
