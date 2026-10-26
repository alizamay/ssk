package dev.niceone.smartsiteappserver.payload.response;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class AuxResponse {
    private Long id;
    private int aux_no;
    private Boolean is_input;
    private String aux_name;
    private Boolean is_on;
    private Boolean is_active;
}
