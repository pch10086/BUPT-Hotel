#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
酒店空调系统自动化测试脚本
根据测试用例自动执行空调操作（开机、关机、调温、调风速等）

注意：
- 此脚本只执行测试用例中的空调操作
- 不包含办理入住和结账功能
- 请在运行脚本前手动办理房间入住
- 测试完成后手动执行结账操作
"""

import requests
import time
import json
from typing import Dict, List, Optional, Tuple
from dataclasses import dataclass
from enum import Enum

# 配置
API_BASE_URL = "http://localhost:8080/api"  # 如果服务器在不同机器，改为服务器IP，如 "http://192.168.1.100:8080/api"

# 房间映射（测试用例中的列对应实际房间号）
ROOM_MAPPING = {
    1: "101",  # 房间1 -> 101
    2: "102",  # 房间2 -> 102
    3: "103",  # 房间3 -> 103
    4: "104",  # 房间4 -> 104
    5: "105",  # 房间5 -> 105
}

# 时间配置
# 逻辑时间1分钟 = 真实时间10秒
LOGIC_MINUTE_TO_REAL_SECOND = 10


class OperationType(Enum):
    POWER_ON = "开机"
    POWER_OFF = "关机"
    SET_TEMP = "设置温度"
    SET_FAN = "设置风速"
    SET_TEMP_FAN = "设置温度和风速"


@dataclass
class TestOperation:
    """测试操作"""
    time_point: int
    room_id: str
    operation_type: OperationType
    target_temp: Optional[float] = None
    fan_speed: Optional[str] = None
    mode: str = "COOL"  # 默认制冷模式
    expected_error: bool = False  # 是否为非法请求（预期会失败）


class TestScript:
    def __init__(self, api_base_url: str = API_BASE_URL):
        self.api_base_url = api_base_url
        self.session = requests.Session()
        self.token = None
        self.results = []
        
    # 注意：当前API不需要认证，所以不需要登录
    # 注意：办理入住和结账由用户手动操作，脚本不包含这些功能
    
    def power_on(self, room_id: str, mode: str, target_temp: float, fan_speed: str) -> Tuple[bool, str]:
        """开机"""
        try:
            response = self.session.post(
                f"{self.api_base_url}/guest/powerOn",
                json={
                    "roomId": room_id,
                    "mode": mode,
                    "targetTemp": target_temp,
                    "fanSpeed": fan_speed
                }
            )
            if response.status_code == 200:
                return True, "成功"
            else:
                return False, f"HTTP {response.status_code}: {response.text}"
        except Exception as e:
            return False, str(e)
    
    def power_off(self, room_id: str) -> Tuple[bool, str]:
        """关机"""
        try:
            response = self.session.post(
                f"{self.api_base_url}/guest/powerOff",
                params={"roomId": room_id}
            )
            if response.status_code == 200:
                return True, "成功"
            else:
                return False, f"HTTP {response.status_code}: {response.text}"
        except Exception as e:
            return False, str(e)
    
    def change_state(self, room_id: str, target_temp: float, fan_speed: str) -> Tuple[bool, str]:
        """调整状态（温度/风速）"""
        try:
            response = self.session.post(
                f"{self.api_base_url}/guest/changeState",
                json={
                    "roomId": room_id,
                    "targetTemp": target_temp,
                    "fanSpeed": fan_speed
                }
            )
            if response.status_code == 200:
                return True, "成功"
            else:
                return False, f"HTTP {response.status_code}: {response.text}"
        except Exception as e:
            return False, str(e)
    
    def get_room_status(self, room_id: str) -> Optional[Dict]:
        """获取房间状态"""
        try:
            response = self.session.get(
                f"{self.api_base_url}/guest/status",
                params={"roomId": room_id}
            )
            if response.status_code == 200:
                return response.json()
            return None
        except Exception as e:
            print(f"获取状态失败 {room_id}: {e}")
            return None
    
    def parse_operation(self, value: str, room_num: int) -> Optional[TestOperation]:
        """解析操作字符串"""
        if not value or value.strip() == "":
            return None
        
        value = value.strip()
        room_id = ROOM_MAPPING[room_num]
        
        # 开机
        if value == "开机":
            return TestOperation(
                time_point=0,  # 会在调用时设置
                room_id=room_id,
                operation_type=OperationType.POWER_ON,
                target_temp=25.0,  # 默认温度
                fan_speed="MIDDLE"  # 默认风速
            )
        
        # 关机
        if value == "关机":
            return TestOperation(
                time_point=0,
                room_id=room_id,
                operation_type=OperationType.POWER_OFF
            )
        
        # 设置温度和风速 "18, 高"
        if "," in value:
            parts = [p.strip() for p in value.split(",")]
            temp = float(parts[0])
            fan = parts[1] if len(parts) > 1 else "MIDDLE"
            return TestOperation(
                time_point=0,
                room_id=room_id,
                operation_type=OperationType.SET_TEMP_FAN,
                target_temp=temp,
                fan_speed=self._parse_fan_speed(fan)
            )
        
        # 纯数字：设置温度
        try:
            temp = float(value)
            return TestOperation(
                time_point=0,
                room_id=room_id,
                operation_type=OperationType.SET_TEMP,
                target_temp=temp
            )
        except ValueError:
            pass
        
        # 纯风速：设置风速
        if value in ["高", "中", "低"]:
            return TestOperation(
                time_point=0,
                room_id=room_id,
                operation_type=OperationType.SET_FAN,
                fan_speed=self._parse_fan_speed(value)
            )
        
        return None
    
    def _parse_fan_speed(self, fan_str: str) -> str:
        """解析风速字符串"""
        mapping = {
            "高": "HIGH",
            "中": "MIDDLE",
            "低": "LOW"
        }
        return mapping.get(fan_str, "MIDDLE")
    
    def execute_operation(self, op: TestOperation) -> Tuple[bool, str]:
        """执行操作"""
        print(f"  [{op.room_id}] {op.operation_type.value}", end="")
        
        if op.operation_type == OperationType.POWER_ON:
            # 获取当前房间状态以确定当前温度和风速
            status = self.get_room_status(op.room_id)
            if status:
                current_temp = op.target_temp or status.get("targetTemp", 25.0)
                current_fan = op.fan_speed or status.get("fanSpeed", "MIDDLE")
            else:
                current_temp = op.target_temp or 25.0
                current_fan = op.fan_speed or "MIDDLE"
            
            success, msg = self.power_on(op.room_id, op.mode, current_temp, current_fan)
            if op.expected_error:
                # 如果是非法请求，失败才是正确的
                success = not success
                msg = "预期失败，实际失败" if not success else "预期失败，但成功了（错误）"
            print(f" - {msg}")
            return success, msg
        
        elif op.operation_type == OperationType.POWER_OFF:
            success, msg = self.power_off(op.room_id)
            print(f" - {msg}")
            return success, msg
        
        elif op.operation_type == OperationType.SET_TEMP:
            # 获取当前状态
            status = self.get_room_status(op.room_id)
            if not status:
                return False, "无法获取房间状态"
            current_fan = status.get("fanSpeed", "MIDDLE")
            success, msg = self.change_state(op.room_id, op.target_temp, current_fan)
            if op.expected_error:
                success = not success
                msg = "预期失败，实际失败" if not success else "预期失败，但成功了（错误）"
            print(f" - 温度={op.target_temp}℃ {msg}")
            return success, msg
        
        elif op.operation_type == OperationType.SET_FAN:
            status = self.get_room_status(op.room_id)
            if not status:
                return False, "无法获取房间状态"
            current_temp = status.get("targetTemp", 25.0)
            success, msg = self.change_state(op.room_id, current_temp, op.fan_speed)
            print(f" - 风速={op.fan_speed} {msg}")
            return success, msg
        
        elif op.operation_type == OperationType.SET_TEMP_FAN:
            success, msg = self.change_state(op.room_id, op.target_temp, op.fan_speed)
            if op.expected_error:
                success = not success
                msg = "预期失败，实际失败" if not success else "预期失败，但成功了（错误）"
            print(f" - 温度={op.target_temp}℃, 风速={op.fan_speed} {msg}")
            return success, msg
        
        return False, "未知操作类型"
    
    def run_test_case(self, test_data: List[List[str]]):
        """运行测试用例
        test_data: 二维列表，每行是一个时间点，每列是一个房间的操作
        
        注意：此脚本只执行测试用例中的空调操作，不包含办理入住和结账
        请在运行脚本前手动办理房间入住，测试完成后手动结账
        """
        print("=" * 60)
        print("开始执行测试用例")
        print("=" * 60)
        print("⚠️  重要提示：请确保5个房间（101-105）已办理入住！")
        print("⚠️  此脚本只执行测试用例中的空调操作，不包含办理入住和结账功能")
        print("=" * 60)
        
        # 1. 解析测试用例
        print("\n[步骤1] 解析测试用例...")
        operations_by_time = {}  # {time_point: [operations]}
        
        for row_idx, row in enumerate(test_data):
            if len(row) < 6:  # 至少需要索引列+5个房间列
                continue
            
            # 第一列是时间点（逻辑时间，单位：分钟）
            try:
                time_point_minutes = int(row[0])  # 逻辑时间（分钟）
            except (ValueError, IndexError):
                print(f"  警告：第{row_idx+1}行时间点格式错误，跳过")
                continue
            
            for col_idx in range(1, 6):  # 列1-5对应房间1-5
                if col_idx >= len(row):
                    continue
                
                value = row[col_idx]
                op = self.parse_operation(value, col_idx)
                
                if op:
                    op.time_point = time_point_minutes
                    if time_point_minutes not in operations_by_time:
                        operations_by_time[time_point_minutes] = []
                    operations_by_time[time_point_minutes].append(op)
        
        print(f"  解析完成，共 {len(operations_by_time)} 个时间点，{sum(len(ops) for ops in operations_by_time.values())} 个操作")
        
        # 2. 按时间顺序执行
        print("\n[步骤2] 按时间顺序执行操作...")
        print("-" * 60)
        
        if not operations_by_time:
            print("  没有找到任何操作，退出测试")
            return
        
        # 按时间点排序
        sorted_time_points = sorted(operations_by_time.keys())
        last_time_point = None
        
        for time_point_minutes in sorted_time_points:
            # 计算需要等待的时间（真实时间，秒）
            if last_time_point is not None:
                time_diff_minutes = time_point_minutes - last_time_point
                wait_seconds = time_diff_minutes * LOGIC_MINUTE_TO_REAL_SECOND
                if wait_seconds > 0:
                    print(f"\n等待 {time_diff_minutes} 分钟（逻辑时间） = {wait_seconds} 秒（真实时间，1逻辑分钟=10秒）...")
                    time.sleep(wait_seconds)
            
            print(f"\n[时间点 {time_point_minutes} 分钟（逻辑时间）]")
            ops = operations_by_time[time_point_minutes]
            
            for op in ops:
                success, msg = self.execute_operation(op)
                self.results.append({
                    "time_point": time_point_minutes,
                    "room_id": op.room_id,
                    "operation": op.operation_type.value,
                    "success": success,
                    "message": msg
                })
            
            last_time_point = time_point_minutes
        
        # 3. 输出结果
        print("\n" + "=" * 60)
        print("测试用例执行完成")
        print("=" * 60)
        print(f"\n总共执行 {len(self.results)} 个操作")
        success_count = sum(1 for r in self.results if r["success"])
        print(f"成功: {success_count}, 失败: {len(self.results) - success_count}")
        
        # 输出详细结果
        print("\n详细结果:")
        for result in self.results:
            status = "✓" if result["success"] else "✗"
            print(f"  {status} [时间{result['time_point']}分钟] {result['room_id']} {result['operation']}: {result['message']}")
        
        print("\n" + "=" * 60)
        print("提示：测试完成后，请切换到前台账号执行结账操作")
        print("=" * 60)


def load_test_case_from_text():
    """从文本格式加载测试用例（根据你提供的表格）"""
    # 这里手动输入测试用例数据
    # 格式：每行是一个时间点
    # 列0：时间点（逻辑时间，单位：分钟）
    # 列1-5：房间1-5的操作（对应房间号101-105）
    # 
    # 注意：时间点是逻辑时间（分钟），每个逻辑分钟 = 10秒真实时间
    # 例如：时间点0立即执行，时间点1在10秒后执行，时间点2在20秒后执行
    test_data = [
        ["0", "开机", "", "", "", ""],  # 时间0分钟：房间1开机
        ["1", "18", "开机", "", "", ""],  # 时间1分钟：房间1设18℃，房间2开机
        ["2", "", "", "开机", "开机", ""],  # 时间2分钟：房间3和房间4开机
        ["3", "", "19", "", "", ""],  # 时间3分钟：房间2设19℃
        ["4", "", "", "开机", "22", ""],  # 时间4分钟：房间3开机，房间4设22℃
        ["5", "高", "", "", "", ""],  # 时间5分钟：房间1设高风
        ["6", "", "关机", "", "", ""],  # 时间6分钟：房间2关机
        ["7", "", "开机", "", "高", ""],  # 时间7分钟：房间2开机，房间4设高风
        ["8", "", "", "", "", ""],  # 时间8分钟：无操作
        ["9", "22", "", "18, 高", "", ""],  # 时间9分钟：房间1设22℃，房间3设18℃高风
        ["10", "", "", "", "", ""],  # 时间10分钟：无操作
        ["11", "", "22", "", "", ""],  # 时间11分钟：房间2设22℃
        ["12", "", "", "", "低", ""],  # 时间12分钟：房间4设低风
        ["13", "", "", "", "", ""],  # 时间13分钟：无操作
        ["14", "关机", "", "24, 低", "", ""],  # 时间14分钟：房间1关机，房间3设24℃低风
        ["15", "", "", "", "20, 高", ""],  # 时间15分钟：房间4设20℃高风
        ["16", "", "关机", "", "", ""],  # 时间16分钟：房间2关机
        ["17", "", "", "高", "", ""],  # 时间17分钟：房间3设高风
        ["18", "开机", "", "20, 中", "", ""],  # 时间18分钟：房间1开机，房间3设20℃中风
        ["19", "", "开机", "", "", ""],  # 时间19分钟：房间2开机
        ["20", "", "", "", "25", ""],  # 时间20分钟：房间4设25℃
        ["21", "", "", "", "", ""],  # 时间21分钟：无操作
        ["22", "", "", "关机", "", ""],  # 时间22分钟：房间3关机
        ["23", "", "", "", "关机", ""],  # 时间23分钟：房间4关机
        ["24", "关机", "", "", "", ""],  # 时间24分钟：房间1关机
        ["25", "", "关机", "关机", "", ""],  # 时间25分钟：房间2和房间3关机
    ]
    # 注意：第5列（房间5）目前都是空的，需要根据实际测试用例填写
    return test_data


if __name__ == "__main__":
    print("酒店空调系统自动化测试脚本")
    print("=" * 60)
    
    # 加载测试用例
    test_data = load_test_case_from_text()
    
    # 创建测试脚本实例
    script = TestScript(API_BASE_URL)
    
    # 运行测试
    script.run_test_case(test_data)

