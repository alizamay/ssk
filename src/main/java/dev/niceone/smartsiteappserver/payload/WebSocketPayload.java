package dev.niceone.smartsiteappserver.payload;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.niceone.smartsiteappserver.payload.request.StatusReportRequest;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WebSocketPayload {
    private String title;
    private String content;

    public WebSocketPayload() {
    }

    public WebSocketPayload(String title,StatusReportRequest content) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString( content );
        this.title = title;
        this.content = json;
    }
}
