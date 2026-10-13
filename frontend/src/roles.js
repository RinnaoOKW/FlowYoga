export const roles = {
  admin: { code: 'ADMIN', label: '管理员', account: 'admin', eyebrow: 'A LITTLE ORDER, A LOT OF EASE', headline: '让每一堂课，\n从容开始。', description: '把日常安排交给 FlowYoga，\n把更多时间留给人与练习。', welcome: '欢迎回来，馆内的每一天都由你从容安排。', button: '进入管理空间' },
  coach: { code: 'COACH', label: '教练', account: 'coach', eyebrow: 'TEACH WITH CARE, MOVE WITH PURPOSE', headline: '专注每一次，\n用心陪伴。', description: '在呼吸与动作之间，\n陪伴每一个微小的进步。', welcome: '愿今天的每一堂课，都有温柔而坚定的力量。', button: '进入教练空间' },
  member: { code: 'MEMBER', label: '会员', account: 'member', eyebrow: 'BREATHE IN. FIND YOUR FLOW.', headline: '慢下来，\n回到自己的节奏。', description: '给身体一段舒展的时间，\n也给心绪一个安静的角落。', welcome: '从一次深呼吸开始，给自己一段从容的练习时光。', button: '进入会员空间' },
}
export const rolePath = code => code.toLowerCase()
export const permissionNames = {
  'member:manage': '会员管理', 'coach:manage': '教练管理', 'course:manage': '课程排课', 'card:issue': '会员开卡', 'report:read': '运营统计',
  'schedule:own': '我的课表', 'attendance:write': '到场登记', 'schedule:read': '查看课表', 'booking:own': '我的预约', 'card:own': '我的会员卡',
}
