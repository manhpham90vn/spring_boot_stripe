import { useCallback, useEffect, useRef, useState } from 'react'
import { waitingroom } from '../api/endpoints'
import { ApiError } from '../api/client'

// Van chặn spike: khách phải qua CAPTCHA → xếp hàng → tới lượt (nhận PASS) trước khi mua.
// Khi được thả, gọi onAdmitted(accessToken) để mở khoá luồng đặt vé.
type Stage = 'captcha' | 'queue' | 'soldout'

export default function WaitingRoomGate({
  eventId,
  onAdmitted,
}: {
  eventId: string
  onAdmitted: (accessToken: string) => void
}) {
  const [stage, setStage] = useState<Stage>('captcha')
  const [captcha, setCaptcha] = useState<{ captchaId: string; imageUrl: string } | null>(null)
  const [answer, setAnswer] = useState('')
  const [token, setToken] = useState<string | null>(null)
  const [position, setPosition] = useState<number | null>(null)
  const [eta, setEta] = useState<number | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Nạp CAPTCHA mới (và thu hồi objectURL cũ để khỏi rò bộ nhớ).
  const loadCaptcha = useCallback(() => {
    setError(null)
    setAnswer('')
    waitingroom
      .captcha()
      .then((c) => {
        setCaptcha((prev) => {
          if (prev) URL.revokeObjectURL(prev.imageUrl)
          return c
        })
      })
      .catch((e: ApiError) => setError(e.message))
  }, [])

  useEffect(() => {
    loadCaptcha()
  }, [loadCaptcha])

  async function joinQueue() {
    if (!captcha || !answer.trim()) return
    setBusy(true)
    setError(null)
    try {
      const res = await waitingroom.enqueue(eventId, {
        captchaId: captcha.captchaId,
        captchaAnswer: answer.trim(),
      })
      setToken(res.token)
      setPosition(res.position)
      setEta(res.etaSeconds)
      setStage('queue')
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Không vào được hàng đợi.')
      loadCaptcha() // CAPTCHA dùng một lần → cấp ảnh mới để thử lại
    } finally {
      setBusy(false)
    }
  }

  // Poll vị trí mỗi 2s khi đang xếp hàng; tới lượt → trả PASS lên cha.
  const pollRef = useRef<number | null>(null)
  useEffect(() => {
    if (stage !== 'queue' || !token) return
    let cancelled = false

    async function tick() {
      try {
        const s = await waitingroom.status(eventId, token!)
        if (cancelled) return
        if (s.admitted && s.accessToken) {
          onAdmitted(s.accessToken)
          return
        }
        if (s.soldOut) {
          setStage('soldout')
          return
        }
        setPosition(s.position)
      } catch {
        /* lỗi tạm thời — nhịp sau thử lại */
      }
    }

    tick()
    pollRef.current = window.setInterval(tick, 2000)
    return () => {
      cancelled = true
      if (pollRef.current) window.clearInterval(pollRef.current)
    }
  }, [stage, token, eventId, onAdmitted])

  if (stage === 'soldout') {
    return (
      <div className="alert">
        😔 Rất tiếc, sự kiện đã hết vé trong lúc bạn xếp hàng.
      </div>
    )
  }

  if (stage === 'queue') {
    return (
      <div className="panel" style={{ textAlign: 'center' }}>
        <div className="spinner" />
        <h3 style={{ marginBottom: 4 }}>Bạn đang trong hàng chờ</h3>
        <p style={{ fontSize: 28, fontWeight: 700, margin: '8px 0', color: 'var(--brand)' }}>
          #{position ?? '…'}
        </p>
        <p className="muted">
          {eta != null && eta > 0
            ? `Ước tính còn khoảng ${formatEta(eta)}.`
            : 'Sắp tới lượt bạn…'}
        </p>
        <p className="muted" style={{ fontSize: 13 }}>
          Vui lòng giữ trang mở — bạn sẽ được vào mua tự động khi tới lượt.
        </p>
      </div>
    )
  }

  // stage === 'captcha'
  return (
    <div className="panel" style={{ maxWidth: 380 }}>
      <h3 style={{ marginTop: 0 }}>Xác minh để vào hàng chờ</h3>
      <p className="muted">Nhập dãy số trong ảnh để chứng minh bạn không phải bot.</p>
      {captcha ? (
        <div className="row" style={{ gap: 8, alignItems: 'center' }}>
          <img
            src={captcha.imageUrl}
            alt="CAPTCHA"
            style={{ borderRadius: 8, height: 60, background: '#fff' }}
          />
          <button type="button" className="btn" title="Đổi ảnh khác" onClick={loadCaptcha}>
            ↻
          </button>
        </div>
      ) : (
        <div className="spinner">Đang tải mã…</div>
      )}
      <div className="field" style={{ marginTop: 12 }}>
        <input
          inputMode="numeric"
          placeholder="Nhập dãy số"
          value={answer}
          onChange={(e) => setAnswer(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && joinQueue()}
        />
      </div>
      {error && <div className="alert">{error}</div>}
      <button
        className="btn btn--primary"
        style={{ width: '100%' }}
        disabled={busy || !answer.trim() || !captcha}
        onClick={joinQueue}
      >
        {busy ? 'Đang vào hàng…' : 'Vào hàng chờ'}
      </button>
    </div>
  )
}

function formatEta(seconds: number): string {
  if (seconds < 60) return `${seconds} giây`
  const m = Math.ceil(seconds / 60)
  return `${m} phút`
}
