package dev.flexmodel.functions;

import dev.flexmodel.common.BusinessException;

/**
 * @author cjbi
 */
public class FunctionException extends BusinessException {

    public FunctionException(String message) {
        super(message);
    }

    public FunctionException(String message, Throwable cause) {
        super(message, cause);
    }
}
