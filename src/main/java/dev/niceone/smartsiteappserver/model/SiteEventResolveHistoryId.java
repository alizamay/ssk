package dev.niceone.smartsiteappserver.model;

import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class SiteEventResolveHistoryId implements Serializable {
    private Long id;
    private LocalDateTime createDate;
}