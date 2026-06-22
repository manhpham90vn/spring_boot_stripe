import { useEffect } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { useAuthStore } from './stores/auth'
import Layout from './components/Layout'
import RequireAuth from './components/RequireAuth'
import EventsPage from './pages/EventsPage'
import EventDetailPage from './pages/EventDetailPage'
import LoginPage from './pages/LoginPage'
import MyTicketsPage from './pages/MyTicketsPage'

export default function App() {
  // Khôi phục phiên một lần khi app khởi động (thay cho AuthProvider trước đây).
  useEffect(() => {
    useAuthStore.getState().init()
  }, [])

  return (
    <BrowserRouter>
      <Routes>
        <Route element={<Layout />}>
          <Route index element={<EventsPage />} />
          <Route path="events/:id" element={<EventDetailPage />} />
          <Route path="login" element={<LoginPage />} />
          <Route
            path="tickets"
            element={
              <RequireAuth>
                <MyTicketsPage />
              </RequireAuth>
            }
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
