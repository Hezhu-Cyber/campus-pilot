import axios from 'axios'

const http = axios.create({ baseURL: '/api', timeout: 10000 })

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('cp_token')
  if (token) config.headers['authorization'] = token
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && body.success === false) {
      return Promise.reject(new Error(body.errorMsg || '请求失败'))
    }
    return body
  },
  (error) => {
    if (error.response) {
      if (error.response.status === 401) {
        localStorage.removeItem('cp_token')
        localStorage.removeItem('cp_user')
        if (!location.hash.includes('/login')) location.hash = '#/login'
        return Promise.reject(new Error('请先登录'))
      }
      const msg = error.response.data && error.response.data.errorMsg
      if (msg) return Promise.reject(new Error(msg))
    }
    return Promise.reject(new Error('网络异常，请稍后重试'))
  }
)

export const get = (url, params) => http.get(url, { params })
export const post = (url, data, config) => http.post(url, data, config)
export const put = (url, data) => http.put(url, data)
export const del = (url, params) => http.delete(url, { params })

export default http

// 将数据库中的图片路径规范化为浏览器可访问的 /imgs/... 地址
export const imgUrl = (p) => (p && !p.startsWith('/imgs') && !/^https?:/.test(p) ? '/imgs' + p : p)

