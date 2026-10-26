package dev.niceone.smartsiteappserver.payload;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Setter
@Getter
public class CabinetStatusResponses {
    private List<CabinetStatusResponse> cabinets;
}
