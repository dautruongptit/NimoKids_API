package com.nimokids.entity;

import com.nimokids.entity.enums.AssetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Metadata of a reusable image/audio file. Binary content lives in object storage, not in PostgreSQL. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "media_assets")
public class MediaAsset extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 30)
    private AssetType assetType;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "storage_url", nullable = false, length = 1000)
    private String storageUrl;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
