import api from './index'

class TimeService {
  constructor() {
    this.offset = 0;
  }

  async initialize() {
    try {
      // 从后端获取时间配置
      const res = await api.get('/auth/time');
      // 计算本地时间与服务器逻辑时间的偏移量
      // offset = serverTime - localTime
      this.offset = res.data - Date.now();
      console.log('TimeService initialized, offset:', this.offset);
    } catch (e) {
      console.warn('Failed to sync time with backend, using local time', e);
    }
  }

  now() {
    return new Date(Date.now() + this.offset);
  }
}

export default new TimeService();
