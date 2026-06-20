import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { catalog } from '../api/endpoints'
import { ApiError } from '../api/client'
import { formatDateTime } from '../lib/format'
import type { EventSummaryResponse } from '../api/types'

export default function EventsPage() {
  const [events, setEvents] = useState<EventSummaryResponse[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    catalog
      .listEvents()
      .then(setEvents)
      .catch((e: ApiError) => setError(e.message))
  }, [])

  return (
    <>
      <h1 className="title">Sự kiện đang mở bán</h1>
      <p className="subtitle">Chọn một sự kiện để xem hạng vé và đặt mua.</p>

      {error && <div className="alert">{error}</div>}
      {!events && !error && <div className="spinner">Đang tải sự kiện…</div>}
      {events && events.length === 0 && <p className="muted">Chưa có sự kiện nào.</p>}

      <div className="grid">
        {events?.map((e) => (
          <Link key={e.id} to={`/events/${e.id}`} className="card">
            <div className="row" style={{ justifyContent: 'space-between' }}>
              <h3 className="card__title">{e.title}</h3>
              <span className={`badge badge--${e.status}`}>{e.status}</span>
            </div>
            <p className="card__meta">🗓 {formatDateTime(e.startsAt)}</p>
            {e.venue && (
              <p className="card__meta">
                📍 {e.venue.name}
                {e.venue.city ? `, ${e.venue.city}` : ''}
              </p>
            )}
          </Link>
        ))}
      </div>
    </>
  )
}
