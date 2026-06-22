import { http, setToken } from './client'
import type { TokenResponse, UserResponse } from '../types/auth'

export const authApi = {
  async login(email: string, password: string): Promise<TokenResponse> {
    const res = await http.post<TokenResponse>('/api/auth/public/login', { email, password })
    setToken(res.accessToken)
    return res
  },
  register: (email: string, password: string) =>
    http.post<UserResponse>('/api/auth/public/register', { email, password }),
  me: () => http.get<UserResponse>('/api/auth/me'),
}
