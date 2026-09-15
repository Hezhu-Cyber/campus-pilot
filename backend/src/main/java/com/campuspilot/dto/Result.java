package com.campuspilot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 所有 HTTP 接口共用的统一响应结构。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result {

    /** 请求是否成功。 */
    private Boolean success;

    /** 失败时的可读错误信息。 */
    private String errorMsg;

    /** 成功时的响应数据。 */
    private Object data;

    /** 分页查询总记录数。 */
    private Long total;

    /** 构造成功响应。 */
    public static Result ok(){
        return new Result(true, null, null, null);
    }

    /** 构造成功响应。 */
    public static Result ok(Object data){
        return new Result(true, null, data, null);
    }

    /** 构造成功响应。 */
    public static Result ok(List<?> data, Long total){
        return new Result(true, null, data, total);
    }

    /** 构造包含错误信息的失败响应。 */
    public static Result fail(String errorMsg){
        return new Result(false, errorMsg, null, null);
    }
}
