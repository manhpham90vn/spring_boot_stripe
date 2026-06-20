import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { catalog, orders } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { formatDateTime, formatMoney } from '../lib/format'
import type {
  EventDetailResponse,
  OrderResponse,
  TicketTypeResponse,
} from '../api/types'
import { Link } from 'react-router-dom'

export default function EventDetailPage() {
  const { id } = useParams<{ id: string }>()
  const { user } = useAuth()
  const [event, setEvent] = useState<EventDetailResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  // Trạng thái đặt vé
  const [selected, setSelected] = useState<TicketTypeResponse | null>(null)
  const [quantity, setQuantity] = useState(1)
  const [placing, setPlacing] = useState(false)
  const [result, setResult] = useState<OrderResponse | null>(null)
  const [orderError, setOrderError] = useState<string | null>(null)

  useEffect(() => {
    if (!id) return
    catalog
      .eventDetail(id)
      .then(setEvent)
      .catch((e: ApiError) => setError(e.message))
  }, [id])

  async function placeOrder() {
    if (!event || !selected) return
    setPlacing(true)
    setOrderError(null)
    setResult(null)
    try {
      const order = await orders.place({
        eventId: event.id,
        ticketTypeId: selected.id,
        quantity,
      })
      setResult(order)
    } catch (e) {
      setOrderError(e instanceof ApiError ? e.message : 'Đặt vé thất bại.')
    } finally {
      setPlacing(false)
    }
  }

  if (error) return <div className="alert">{error}</div>
  if (!event) return <div className="spinner">Đang tải…</div>

  return (
    <>
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <h1 className="title">{event.title}</h1>
        <span className={`badge badge--${event.status}`}>{event.status}</span>
      </div>
      <p className="subtitle">
        🗓 {formatDateTime(event.startsAt)}
        {event.venue && ` · 📍 ${event.venue.name}${event.venue.city ? ', ' + event.venue.city : ''}`}
      </p>

      {event.description && <p style={{ maxWidth: 680 }}>{event.description}</p>}

      <h2 style={{ marginTop: 32 }}>Hạng vé</h2>
      {event.ticketTypes.length === 0 && <p className="muted">Chưa mở bán hạng vé nào.</p>}

      <div className="grid">
        {event.ticketTypes.map((t) => (
          <div
            key={t.id}
            className="card"
            style={{
              borderColor: selected?.id === t.id ? 'var(--brand)' : undefined,
            }}
          >
            <h3 className="card__title">{t.name}</h3>
            {t.description && <p className="card__meta">{t.description}</p>}
            <p style={{ fontSize: 20, fontWeight: 700, margin: '10px 0' }}>
              {formatMoney(t.priceMinor, t.currency)}
            </p>
            <p className="card__meta">Tối đa {t.maxPerOrder} vé / đơn</p>
            <button
              className="btn btn--primary"
              style={{ marginTop: 8, width: '100%' }}
              onClick={() => {
                setSelected(t)
                setQuantity(1)
                setResult(null)
                setOrderError(null)
              }}
            >
              Chọn
            </button>
          </div>
        ))}
      </div>

      {selected && (
        <div className="panel" style={{ marginTop: 28, maxWidth: 480 }}>
          <h2 style={{ marginTop: 0 }}>Đặt mua: {selected.name}</h2>

          {!user ? (
            <p>
              Bạn cần <Link to="/login" style={{ color: 'var(--brand)' }}>đăng nhập</Link> để
              đặt vé.
            </p>
          ) : result ? (
            <OrderResultView order={result} ticketName={selected.name} />
          ) : (
            <>
              <div className="field">
                <label htmlFor="qty">Số lượng</label>
                <select
                  id="qty"
                  value={quantity}
                  onChange={(e) => setQuantity(Number(e.target.value))}
                >
                  {Array.from({ length: selected.maxPerOrder }, (_, i) => i + 1).map((n) => (
                    <option key={n} value={n}>
                      {n}
                    </option>
                  ))}
                </select>
              </div>
              <p className="row" style={{ justifyContent: 'space-between' }}>
                <span className="muted">Tạm tính</span>
                <strong>{formatMoney(selected.priceMinor * quantity, selected.currency)}</strong>
              </p>
              {orderError && <div className="alert">{orderError}</div>}
              <button
                className="btn btn--primary"
                style={{ width: '100%' }}
                disabled={placing}
                onClick={placeOrder}
              >
                {placing ? 'Đang xử lý thanh toán…' : 'Thanh toán'}
              </button>
            </>
          )}
        </div>
      )}
    </>
  )
}

function OrderResultView({ order, ticketName }: { order: OrderResponse; ticketName: string }) {
  if (order.status === 'PAID') {
    return (
      <>
        <div className="alert alert--ok">
          🎉 Thanh toán thành công {order.quantity} × {ticketName}!
        </div>
        <p className="muted">Mã đơn: {order.id}</p>
        <Link to="/tickets" className="btn btn--primary" style={{ width: '100%' }}>
          Xem vé của tôi
        </Link>
      </>
    )
  }

  const reason =
    order.status === 'REJECTED'
      ? 'Rất tiếc, hạng vé đã hết chỗ.'
      : order.failureReason ?? 'Thanh toán không thành công, bạn chưa bị trừ tiền.'

  return (
    <>
      <div className="alert">
        <span className={`badge badge--${order.status}`}>{order.status}</span> {reason}
      </div>
      <p className="muted">Mã đơn: {order.id}</p>
    </>
  )
}
