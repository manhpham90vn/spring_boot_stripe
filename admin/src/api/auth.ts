import { http, setToken } from './client'
import type { TokenResponse, UserResponse } from '../types/auth'

export const authApi = {
  async login(email: string, password: string): Promise<UserResponse> {
    const res = await http.post<TokenResponse>('/api/auth/public/login', { email, password })
    setToken(res.accessToken)
    return http.get<UserResponse>('/api/auth/me')
  },
  me: () => http.get<UserResponse>('/api/auth/me'),
}
