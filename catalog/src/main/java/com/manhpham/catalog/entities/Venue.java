package com.manhpham.catalog.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "venues")
@Getter
public class Venue {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 500)
    private String address;

    @Column(length = 120)
    private String city;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Venue() {
        // for JPA
    }

    public static Venue create(String name, String address, String city) {
        Venue v = new Venue();
        v.name = name;
        v.address = address;
        v.city = city;
        return v;
    }

    /** Cập nhật thông tin địa điểm (admin). */
    public void update(String name, String address, String city) {
        this.name = name;
        this.address = address;
        this.city = city;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
