package info.neu.infoapp.model;

import lombok.Data;

@Data
public class ResponseObject {
    String message;
    int statusCode;
    Object data;

    public ResponseObject(String message,  int statusCode, Object data) {
        this.message = message;
        this.statusCode = statusCode;
        this.data = data;
    }

    @Override
    public String toString() {
        return "ResponseObject{" +
                " message :'" + message + '\'' +
                ", status_code :" + statusCode +
                ", Data :" + data +
                '}';
    }
}
