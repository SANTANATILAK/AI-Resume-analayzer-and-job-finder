import axios from 'axios'
const api = axios.create({ baseURL: import.meta.env.VITE_API_URL || 'http://localhost:9090' })
api.interceptors.request.use(c => { const t = localStorage.getItem('token'); if (t) c.headers.Authorization = `Bearer ${t}`; return c })
api.interceptors.response.use(r => r, e => {
  if (e.response?.status === 401 && localStorage.getItem('token')) { localStorage.clear(); window.location.assign('/login') }
  return Promise.reject(e) })
export const errMsg = e => e.response?.data?.message || (e.response?.status === 409 ? 'Email already registered' : e.response?.status === 401 ? 'Invalid email or password' : e.message === 'Network Error' ? 'Cannot reach the server. Check that the backend is running.' : 'Something went wrong. Try again.')
export default api
