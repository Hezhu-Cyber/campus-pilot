package com.campuspilot.config;

import com.campuspilot.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 将权限、参数和运行时异常转换为统一 API 响应。 */
@Slf4j
@RestControllerAdvice
public class WebExceptionAdvice {

    /** 将权限异常转换为失败响应。 */
    @ResponseStatus(HttpStatus.FORBIDDEN)//403表示没有操作权限
    @ExceptionHandler(SecurityException.class)
    public Result handleSecurityException(SecurityException e) {
        return Result.fail(e.getMessage());
    }

    /** 将参数校验异常转换为失败响应。 */
    @ResponseStatus(HttpStatus.BAD_REQUEST)//业务参数不合法
    @ExceptionHandler(IllegalArgumentException.class)
    public Result handleIllegalArgumentException(IllegalArgumentException e) {
        return Result.fail(e.getMessage());
    }

    /** 记录未预期异常并返回不泄露内部细节的错误。 */
    @ExceptionHandler(RuntimeException.class)//返回500
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result handleRuntimeException(RuntimeException e) {
        log.error(e.toString(), e);
        return Result.fail("服务器异常");
    }
}
