import { useEffect, useState, type FormEvent } from 'react'
import { useParams, Link } from 'react-router-dom'
import { loadStripe } from '@stripe/stripe-js'
import type { Stripe } from '@stripe/stripe-js'
import { Elements, PaymentElement, useElements, useStripe } from '@stripe/react-stripe-js'
import { catalog, orders } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { formatDateTime, formatMoney } from '../lib/format'
import type { EventDetailResponse, OrderResponse, TicketTypeResponse } from '../api/types'

// Khởi tạo Stripe MỘT LẦN (ngoài component) từ khoá publishable. Thiếu khoá → null (UI báo lỗi cấu hình).
const PUBLISHABLE_KEY = import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY
const stripePromise: Promise<Stripe | null> | null = PUBLISHABLE_KEY ? loadStripe(PUBLISHABLE_KEY) : null

type Phase = 'form' | 'pay' | 'result'

export default function EventDetailPage() {
  const { id } = useParams<{ id: string }>()
  const { user } = useAuth()
  const [event, setEvent] = useState<EventDetailResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  // Trạng thái đặt vé
  const [selected, setSelected] = useState<TicketTypeResponse | null>(null)
  const [quantity, setQuantity] = useState(1)
  const [placing, setPlacing] = useState(false)
  const [order, setOrder] = useState<OrderResponse | null>(null)
  const [phase, setPhase] = useState<Phase>('form')
  const [orderError, setOrderError] = useState<string | null>(null)

  useEffect(() => {
    if (!id) return
    catalog
      .eventDetail(id)
      .then(setEvent)
      .catch((e: ApiError) => setError(e.message))
  }, [id])

  function resetOrder() {
    setOrder(null)
    setPhase('form')
    setOrderError(null)
  }

  async function placeOrder() {
    if (!event || !selected) return
    setPlacing(true)
    setOrderError(null)
    try {
      const o = await orders.place({ eventId: event.id, ticketTypeId: selected.id, quantity })
      setOrder(o)
      // Đã tạo PaymentIntent → sang bước nhập thẻ; ngược lại (REJECTED / lỗi tạo) → kết quả luôn.
      setPhase(o.status === 'AWAITING_PAYMENT' && o.clientSecret ? 'pay' : 'result')
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
                resetOrder()
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
          ) : phase === 'pay' && order ? (
            <PaymentPanel
              order={order}
              onResolved={(final) => {
                setOrder(final)
                setPhase('result')
              }}
            />
          ) : phase === 'result' && order ? (
            <OrderResultView order={order} ticketName={selected.name} />
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
                {placing ? 'Đang tạo đơn…' : 'Tiếp tục thanh toán'}
              </button>
            </>
          )}
        </div>
      )}
    </>
  )
}

/** Poll trạng thái đơn tới khi về trạng thái cuối (webhook chốt). Tối đa ~30s. */
async function pollOrderUntilFinal(orderId: string): Promise<OrderResponse> {
  for (let i = 0; i < 20; i++) {
    const o = await orders.get(orderId)
    if (o.status === 'PAID' || o.status === 'PAYMENT_FAILED' || o.status === 'REJECTED') return o
    await new Promise((r) => setTimeout(r, 1500))
  }
  return orders.get(orderId) // hết hạn poll: trả trạng thái mới nhất (UI hiện "đang xử lý")
}

function PaymentPanel({
  order,
  onResolved,
}: {
  order: OrderResponse
  onResolved: (final: OrderResponse) => void
}) {
  if (!stripePromise || !order.clientSecret) {
    return (
      <div className="alert">
        Thiếu cấu hình Stripe (đặt <code>VITE_STRIPE_PUBLISHABLE_KEY</code> trong <code>.env</code>).
      </div>
    )
  }
  return (
    <Elements
      stripe={stripePromise}
      options={{ clientSecret: order.clientSecret, appearance: { theme: 'night' } }}
    >
      <CheckoutForm order={order} onResolved={onResolved} />
    </Elements>
  )
}

function CheckoutForm({
  order,
  onResolved,
}: {
  order: OrderResponse
  onResolved: (final: OrderResponse) => void
}) {
  const stripe = useStripe()
  const elements = useElements()
  const [submitting, setSubmitting] = useState(false)
  const [waiting, setWaiting] = useState(false)
  const [err, setErr] = useState<string | null>(null)

  async function pay(e: FormEvent) {
    e.preventDefault()
    if (!stripe || !elements) return
    setSubmitting(true)
    setErr(null)

    // Xác nhận thẻ. redirect:'if_required' → ở lại trang với thẻ không cần redirect (kể cả 3DS modal).
    const { error } = await stripe.confirmPayment({
      elements,
      confirmParams: { return_url: window.location.href },
      redirect: 'if_required',
    })

    if (error) {
      // Lỗi ngay phía client (thẻ bị từ chối, thiếu thông tin) — đơn vẫn AWAITING_PAYMENT.
      setErr(error.message ?? 'Thanh toán không thành công.')
      setSubmitting(false)
      return
    }

    // Đã gửi xác nhận → backend chốt qua webhook. Poll tới trạng thái cuối rồi báo cha.
    setWaiting(true)
    const final = await pollOrderUntilFinal(order.id)
    onResolved(final)
  }

  return (
    <form onSubmit={pay}>
      <PaymentElement />
      {err && <div className="alert" style={{ marginTop: 12 }}>{err}</div>}
      <button
        className="btn btn--primary"
        style={{ width: '100%', marginTop: 16 }}
        disabled={!stripe || submitting}
      >
        {waiting
          ? '⏳ Đang xác nhận thanh toán…'
          : submitting
            ? 'Đang xử lý…'
            : `Trả ${formatMoney(order.amountMinor, order.currency)}`}
      </button>
    </form>
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

  // Đang chờ webhook chốt — KHÔNG phải thất bại.
  if (order.status === 'AWAITING_PAYMENT' || order.status === 'PENDING') {
    return (
      <>
        <div className="alert">
          ⏳ Đang xử lý thanh toán… Vé sẽ xuất hiện ở <Link to="/tickets">Vé của tôi</Link> khi hoàn tất.
        </div>
        <p className="muted">Mã đơn: {order.id}</p>
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
