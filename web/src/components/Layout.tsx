import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export default function Layout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  return (
    <>
      <nav className="nav">
        <NavLink to="/" className="nav__brand">
          Ticket<span>Hub</span>
        </NavLink>
        <NavLink to="/" end>
          Sự kiện
        </NavLink>
        {user && <NavLink to="/tickets">Vé của tôi</NavLink>}
        <div className="nav__spacer" />
        {user ? (
          <>
            <span className="nav__user">{user.email}</span>
            <button
              className="btn"
              onClick={() => {
                logout()
                navigate('/')
              }}
            >
              Đăng xuất
            </button>
          </>
        ) : (
          <NavLink to="/login" className="btn btn--primary">
            Đăng nhập
          </NavLink>
        )}
      </nav>
      <main className="container">
        <Outlet />
      </main>
    </>
  )
}
