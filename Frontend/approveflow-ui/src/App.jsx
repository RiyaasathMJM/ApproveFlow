import { Navigate, Route, Routes } from 'react-router-dom'
import { homePathFor, useAuth } from './auth/AuthContext.jsx'
import ProtectedRoute from './auth/ProtectedRoute.jsx'
import Layout from './components/Layout.jsx'
import Placeholder from './components/Placeholder.jsx'
import LoginPage from './pages/LoginPage.jsx'
import RequestDetailPage from './pages/RequestDetailPage.jsx'
import RequestFormPage from './pages/RequestFormPage.jsx'
import RequestListPage from './pages/RequestListPage.jsx'
import PendingApprovalsPage from './pages/PendingApprovalsPage.jsx'

function HomeRedirect() {
  const { user } = useAuth()
  return <Navigate to={user ? homePathFor(user) : '/login'} replace />
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      <Route element={<ProtectedRoute />}>
        <Route element={<Layout />}>
          <Route path="/" element={<HomeRedirect />} />
          <Route path="/requests/:id" element={<RequestDetailPage />} />

          <Route element={<ProtectedRoute roles={['REQUESTER']} />}>
            <Route path="/requests" element={<RequestListPage title="My Requests" canCreate />} />
            <Route path="/requests/new" element={<RequestFormPage />} />
            <Route path="/requests/:id/edit" element={<RequestFormPage />} />
          </Route>

          <Route element={<ProtectedRoute roles={['MANAGER', 'FINANCE']} />}>
            <Route path="/approvals" element={<PendingApprovalsPage />} />
          </Route>

          <Route element={<ProtectedRoute roles={['ADMIN']} />}>
            <Route path="/admin/dashboard" element={<Placeholder title="Summary Dashboard" />} />
            <Route path="/admin/requests" element={<RequestListPage title="All Requests" showOwner />} />
            <Route path="/admin/workflows" element={<Placeholder title="Workflow Definitions" />} />
          </Route>

          <Route path="*" element={<HomeRedirect />} />
        </Route>
      </Route>
    </Routes>
  )
}