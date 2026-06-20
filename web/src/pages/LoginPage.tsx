import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { ApiError } from '../api/client'

interface LocationState {
  from?: { pathname: string }
}

export default function LoginPage() {
  const { login, register } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as LocationState | null)?.from?.pathname ?? '/'

  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      if (mode === 'login') await login(email, password)
      else await register(email, password)
      navigate(from, { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Có lỗi xảy ra.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="center-narrow panel">
      <h1 className="title">{mode === 'login' ? 'Đăng nhập' : 'Tạo tài khoản'}</h1>
      <p className="subtitle">
        {mode === 'login' ? 'Đăng nhập để đặt vé.' : 'Đăng ký nhanh bằng email.'}
      </p>

      {error && <div className="alert">{error}</div>}

      <form onSubmit={submit}>
        <div className="field">
          <label htmlFor="email">Email</label>
          <input
            id="email"
            type="email"
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
        </div>
        <div className="field">
          <label htmlFor="password">Mật khẩu</label>
          <input
            id="password"
            type="password"
            autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
            minLength={8}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </div>
        <button className="btn btn--primary" disabled={busy} style={{ width: '100%' }}>
          {busy ? 'Đang xử lý…' : mode === 'login' ? 'Đăng nhập' : 'Đăng ký'}
        </button>
      </form>

      <p className="muted" style={{ marginTop: 16, textAlign: 'center' }}>
        {mode === 'login' ? 'Chưa có tài khoản? ' : 'Đã có tài khoản? '}
        <a
          href="#"
          onClick={(e) => {
            e.preventDefault()
            setError(null)
            setMode(mode === 'login' ? 'register' : 'login')
          }}
          style={{ color: 'var(--brand)' }}
        >
          {mode === 'login' ? 'Đăng ký' : 'Đăng nhập'}
        </a>
      </p>
    </div>
  )
}
