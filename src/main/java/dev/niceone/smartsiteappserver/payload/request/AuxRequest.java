package dev.niceone.smartsiteappserver.payload.request;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class AuxRequest {
    private Long id;
    private int aux_no;
    private Boolean is_input;
    private String aux_name;
    private Boolean is_on;
    private Boolean is_active;
}
