import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { tickets as ticketsApi } from '../api/endpoints'
import { ApiError, getToken } from '../api/client'
import { formatDateTime } from '../lib/format'
import type { TicketResponse } from '../api/types'

export default function MyTicketsPage() {
  const [tickets, setTickets] = useState<TicketResponse[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    ticketsApi
      .mine()
      .then(setTickets)
      .catch((e: ApiError) => setError(e.message))
  }, [])

  return (
    <>
      <h1 className="title">Vé của tôi</h1>
      <p className="subtitle">Đưa mã QR này tại cổng để soát vé.</p>

      {error && <div className="alert">{error}</div>}
      {!tickets && !error && <div className="spinner">Đang tải…</div>}
      {tickets && tickets.length === 0 && (
        <p className="muted">
          Bạn chưa có vé nào. <Link to="/" style={{ color: 'var(--brand)' }}>Xem sự kiện</Link>.
        </p>
      )}

      <div className="grid">
        {tickets?.map((t) => (
          <TicketCard key={t.id} ticket={t} />
        ))}
      </div>
    </>
  )
}

function TicketCard({ ticket }: { ticket: TicketResponse }) {
  const [qrSrc, setQrSrc] = useState<string | null>(null)

  // QR endpoint cần Bearer token → tải qua fetch rồi tạo object URL cho <img>.
  useEffect(() => {
    let revoked = false
    let objectUrl: string | null = null
    fetch(ticketsApi.qrUrl(ticket.id), {
      headers: { Authorization: `Bearer ${getToken()}` },
    })
      .then((r) => (r.ok ? r.blob() : Promise.reject()))
      .then((blob) => {
        if (revoked) return
        objectUrl = URL.createObjectURL(blob)
        setQrSrc(objectUrl)
      })
      .catch(() => {})
    return () => {
      revoked = true
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [ticket.id])

  return (
    <div className="card">
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <h3 className="card__title">Vé #{ticket.id.slice(0, 8)}</h3>
        <span className={`badge badge--${ticket.status}`}>{ticket.status}</span>
      </div>
      <p className="card__meta">Phát hành: {formatDateTime(ticket.issuedAt)}</p>
      <div
        style={{
          background: '#fff',
          borderRadius: 10,
          padding: 12,
          marginTop: 12,
          display: 'flex',
          justifyContent: 'center',
        }}
      >
        {qrSrc ? (
          <img src={qrSrc} alt="Mã QR vé" width={180} height={180} />
        ) : (
          <span className="muted" style={{ color: '#555', height: 180, lineHeight: '180px' }}>
            Đang tạo QR…
          </span>
        )}
      </div>
    </div>
  )
}
