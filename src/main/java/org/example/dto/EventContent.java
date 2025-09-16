package org.example.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.*;

import java.time.LocalDateTime;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class EventContent {
    @JsonProperty
    private String type;
    @JsonProperty
    private Long lineId;
    @JsonProperty
    private Long nextLineId;
    @JsonProperty
    private Long splitIndex;
    @JsonProperty
    private String content;
    @JsonProperty
    private int position;
    @JsonProperty
    private String clientId;
    @JsonProperty
    @JsonSerialize(using = LocalDateTimeSerializer.class) // 직렬화 시 필요
    @JsonDeserialize(using = LocalDateTimeDeserializer.class) // 역직렬화 시 필요
    @JsonFormat(pattern = "yyyy-MM-dd kk:mm:ss")
    private LocalDateTime timestamp;

    public void setClientId(String clientId){
        this.clientId = clientId;
    }

    public void setTimestamp(LocalDateTime timestamp){
        this.timestamp = timestamp;
    }
}
