export interface TokenResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
}

export interface UserResponse {
  id: string
  email: string
  role: string
}
