import { Link } from 'react-router-dom'
import { useCatalogStore } from '../stores/catalog'
import { useAsync } from '../hooks/useAsync'
import { formatDateTime } from '../lib/format'

export default function EventsPage() {
  const events = useCatalogStore((s) => s.events)
  const fetchEvents = useCatalogStore((s) => s.fetchEvents)
  const { loading, error } = useAsync(() => fetchEvents(), [fetchEvents], 'Không tải được sự kiện.')

  return (
    <>
      <h1 className="title">Sự kiện đang mở bán</h1>
      <p className="subtitle">Chọn một sự kiện để xem hạng vé và đặt mua.</p>

      {error && <div className="alert">{error}</div>}
      {loading && events.length === 0 && !error && (
        <div className="spinner">Đang tải sự kiện…</div>
      )}
      {!loading && events.length === 0 && !error && <p className="muted">Chưa có sự kiện nào.</p>}

      <div className="grid">
        {events.map((e) => (
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
