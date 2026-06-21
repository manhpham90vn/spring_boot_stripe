package com.manhpham.inventory.controller;

import com.manhpham.inventory.dto.AvailabilityResponse;
import com.manhpham.inventory.dto.HoldRequest;
import com.manhpham.inventory.dto.HoldResponse;
import com.manhpham.inventory.dto.SeedStockRequest;
import com.manhpham.inventory.services.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * API NỘI BỘ của Inventory (quy ước {@code /internal/**}, xem API-CONVENTIONS.md): CHỈ
 * service↔service gọi (Order điều phối saga), KHÔNG expose ra biên. Rào thật ở tầng mạng
 * (gateway không route + NetworkPolicy ở prod). Vì thế không cần JWT ở đây.
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalInventoryController {

    private final InventoryService inventory;

    /** Seed/đặt lại tồn cho một loại vé (admin/khởi tạo dữ liệu test). */
    @PutMapping("/stock/{ticketTypeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void seed(@PathVariable UUID ticketTypeId, @Valid @RequestBody SeedStockRequest request) {
        if (request.seated()) {
            inventory.seedSeats(ticketTypeId, request.eventId(), request.seatIds());
        } else {
            if (request.totalQty() == null) {
                throw new IllegalArgumentException("GA seed cần totalQty");
            }
            inventory.seed(ticketTypeId, request.eventId(), request.totalQty());
        }
    }

    /** Số vé còn lại. */
    @GetMapping("/stock/{ticketTypeId}")
    public AvailabilityResponse availability(@PathVariable UUID ticketTypeId) {
        return new AvailabilityResponse(ticketTypeId, inventory.available(ticketTypeId));
    }

    /** HOLD — giữ chỗ (saga bước 2). */
    @PostMapping("/holds")
    @ResponseStatus(HttpStatus.CREATED)
    public HoldResponse hold(@Valid @RequestBody HoldRequest request) {
        return inventory.hold(request);
    }

    /** COMMIT — chốt SOLD sau khi thu tiền (saga bước 4). */
    @PostMapping("/holds/{holdId}/commit")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void commit(@PathVariable UUID holdId) {
        inventory.commit(holdId);
    }

    /** RELEASE — nhả chỗ (bù trừ khi thu tiền hỏng / huỷ). */
    @DeleteMapping("/holds/{holdId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@PathVariable UUID holdId) {
        inventory.release(holdId);
    }
}
