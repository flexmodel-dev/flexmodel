package dev.flexmodel.functions.dto;

import lombok.Data;

/**
 * Simplified invoke request — the body IS the function input.
 *
 * @author cjbi
 */
@Data
public class FunctionInvokeRequest {

    private Object input;

    /** 仅服务端设置，客户端传入的值会被覆盖 */
    private String authToken;

    /** 仅服务端设置，本次调用的唯一ID，用于关联日志 */
    private String invokeId;
}
