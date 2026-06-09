package dev.flexmodel.functions.dto;

import lombok.Data;

/**
 * @author cjbi
 */
@Data
public class FunctionPageRequest {

    private String name;
    private String status;
    private int page = 1;
    private int size = 20;
}
