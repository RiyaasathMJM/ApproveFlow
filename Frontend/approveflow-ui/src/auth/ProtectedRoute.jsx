import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { homePathFor, useAuth } from './AuthContext.jsx'

export default function ProtectedRoute({ roles }) {
  const { user, loading } = useAuth()
  const location = useLocation()

  if (loading) return <div className="spinner" aria-label="Loading" />
  if (!user) return <Navigate to="/login" replace state={{ from: location }} />

  // role illaadha page-ku pona, own home-ku anuppum (server-la-yum check aagum)
  if (roles && !roles.some((r) => user.roles.includes(r))) {
    return <Navigate to={homePathFor(user)} replace />
  }
  return <Outlet />
}