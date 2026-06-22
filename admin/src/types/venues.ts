export interface VenueResponse {
  id: string
  name: string
  address: string | null
  city: string | null
}

export interface VenuePayload {
  name: string
  address: string
  city: string
}
