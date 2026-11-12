import { useCallback, useEffect, useState } from 'react'
import { api } from '../api/client.js'

export function useFetch(path) {
  const [state, setState] = useState({ data: null, loading: true, error: '' })
  const [tick, setTick] = useState(0)

  useEffect(() => {
    let cancelled = false
    setState((s) => ({ ...s, loading: true, error: '' }))
    api(path)
      .then((data) => !cancelled && setState({ data, loading: false, error: '' }))
      .catch((err) => !cancelled && setState({ data: null, loading: false, error: err.message }))
    return () => {
      cancelled = true
    }
  }, [path, tick])

  const reload = useCallback(() => setTick((t) => t + 1), [])
  return { ...state, reload }
}